/**
 * Copyright (C) 2015-2015 Thales Services SAS. All rights reserved. No warranty, explicit or implicit, provided.
 */
package org.ow2.authzforce.rest.service.test;

/**
 *
 *
 */
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.ws.rs.BadRequestException;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;

import org.apache.cxf.endpoint.Server;
import org.apache.cxf.interceptor.Interceptor;
import org.apache.cxf.interceptor.LoggingInInterceptor;
import org.apache.cxf.interceptor.LoggingOutInterceptor;
import org.apache.cxf.jaxrs.JAXRSServerFactoryBean;
import org.apache.cxf.jaxrs.client.ClientConfiguration;
import org.apache.cxf.jaxrs.client.JAXRSClientFactory;
import org.apache.cxf.jaxrs.client.WebClient;
import org.apache.cxf.jaxrs.provider.JAXBElementProvider;
import org.apache.cxf.message.Message;
import org.ow2.authzforce.rest.api.jaxrs.DomainResource;
import org.ow2.authzforce.rest.api.jaxrs.DomainsResource;
import org.ow2.authzforce.rest.api.xmlns.Domain;
import org.ow2.authzforce.rest.api.xmlns.DomainProperties;
import org.ow2.authzforce.rest.api.xmlns.Resources;
import org.ow2.authzforce.rest.service.jaxrs.BadRequestExceptionMapper;
import org.ow2.authzforce.rest.service.jaxrs.ClientErrorExceptionMapper;
import org.ow2.authzforce.rest.service.jaxrs.DomainsResourceImpl;
import org.ow2.authzforce.rest.service.jaxrs.ErrorHandlerInterceptor;
import org.ow2.authzforce.rest.service.jaxrs.NotFoundExceptionMapper;
import org.ow2.authzforce.rest.service.jaxrs.ServerErrorExceptionMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import org.testng.ITestContext;
import org.testng.annotations.AfterSuite;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.Optional;
import org.testng.annotations.Parameters;
import org.testng.annotations.Test;
import org.w3._2005.atom.Link;

@ContextConfiguration(locations = { "classpath:META-INF/spring/applicationContext.xml" })
public class DomainSetTest extends AbstractTestNGSpringContextTests
{
	/**
	 * Test context attribute set by the beforeSuite() to the initialized value of class member 'client'
	 */
	public static final String REST_CLIENT_TEST_CONTEXT_ATTRIBUTE_ID = "com.thalesgroup.authzforce.test.rest.client";

	private static final String WADL_LOCATION = "classpath:/authz-api.wadl";

	private static final Logger LOGGER = LoggerFactory.getLogger(DomainSetTest.class);

	private static final String DEFAULT_APP_BASE_URL = "http://localhost:9080/";

	@Autowired
	@Qualifier("jaxbProvider")
	private JAXBElementProvider<?> serverJaxbProvider;

	@Autowired
	private DomainsResourceImpl domainsResourceBean;

	private Server server;

	@Autowired
	private JAXBElementProvider<?> clientJaxbProvider;

	private DomainsResource client = null;

	private int domainExternalId = 0;
	private Set<String> createdDomainIds = new HashSet<>();

	@Parameters({ "app.base.url", "start.server" })
	@BeforeSuite
	public void beforeSuite(@Optional(DEFAULT_APP_BASE_URL) String appBaseUrl, @Optional("true") boolean startServer, ITestContext testCtx) throws Exception
	{

		if (startServer)
		{
			// Create the directory target/domains if does not exist (see src/test/resources/META-INF/spring/server.xml for actual domains directory path)
			final File domainsDir = new File("target/server.data/domains");
			if (!domainsDir.exists())
			{
				domainsDir.mkdirs();
			}

			/*
			 * Workaround for: http://stackoverflow.com/questions/10184602/accessing-spring-context-in-testngs -beforetest
			 * https://jira.spring.io/browse/SPR-4072 https://jira.spring.io/browse/SPR-5404 (duplicate of previous issue)
			 * springTestContextPrepareTestInstance() happens in
			 * 
			 * @BeforeClass before no access to Autowired beans by default in @BeforeTest
			 */
			super.springTestContextPrepareTestInstance();
			// For SSL debugging
			// System.setProperty("javax.net.debug", "all");

			/**
			 * Create the REST (JAX-RS) server
			 */
			JAXRSServerFactoryBean sf = new JAXRSServerFactoryBean();
			sf.setAddress(appBaseUrl);
			sf.setDocLocation(WADL_LOCATION);
			sf.setStaticSubresourceResolution(true);
			sf.setProviders(Arrays.asList(serverJaxbProvider, new BadRequestExceptionMapper(), new ClientErrorExceptionMapper(), new NotFoundExceptionMapper(),
					new ServerErrorExceptionMapper()));
			sf.setServiceBean(domainsResourceBean);
			final Map<String, Object> jaxRsServerProperties = new HashMap<>();
			jaxRsServerProperties.put("org.apache.cxf.propagate.exception", "false");
			// XML security properties
			jaxRsServerProperties.put("org.apache.cxf.stax.maxChildElements", "10");
			jaxRsServerProperties.put("org.apache.cxf.stax.maxElementDepth", "10");

			// Maximum size of a single attribute
			jaxRsServerProperties.put("org.apache.cxf.stax.maxAttributeSize", "500");

			// Maximum size of an elements text value
			jaxRsServerProperties.put("org.apache.cxf.stax.maxTextLength", "1000");

			sf.setProperties(jaxRsServerProperties);
			sf.setOutFaultInterceptors(Collections.<Interceptor<? extends Message>> singletonList(new ErrorHandlerInterceptor()));
			server = sf.create();
		}

		/**
		 * Create the REST (JAX-RS) client
		 */
		client = JAXRSClientFactory.create(appBaseUrl, DomainsResource.class, Collections.singletonList(clientJaxbProvider));

		/**
		 * Request/response logging (for debugging).
		 */
		final ClientConfiguration clientConf = WebClient.getConfig(client);
		clientConf.getInInterceptors().add(new LoggingInInterceptor());
		clientConf.getOutInterceptors().add(new LoggingOutInterceptor());

		testCtx.setAttribute(REST_CLIENT_TEST_CONTEXT_ATTRIBUTE_ID, client);

		// Retrieve WADL
		// FIXME: find a way to test WADL location. Code below does not work as of writing (response HTTP 404 Not Found for /?_wadl, same with /services)
		// WebTarget target = ClientBuilder.newClient().target(serverURL).queryParam("_wadl", "");
		// Invocation.Builder builder = target.request();
		// final ClientConfiguration builderConf = WebClient.getConfig(builder);
		// builderConf.getInInterceptors().add(new LoggingInInterceptor());
		// builderConf.getOutInterceptors().add(new LoggingOutInterceptor());
		// Response response = builder.get();
		// LOGGER.error("WADL request response: {}", response);
	}

	@AfterSuite
	public void destroy() throws Exception
	{
		// server != null only if test suite property start.server = true
		if (server != null)
		{
			server.stop();
			server.destroy();
		}
	}

	@Parameters({ "app.base.url" })
	@Test
	public void getWADL(@Optional(DEFAULT_APP_BASE_URL) String appBaseUrl)
	{
		WebTarget target = ClientBuilder.newClient().target(appBaseUrl).queryParam("_wadl", "");
		Invocation.Builder builder = target.request();
		final ClientConfiguration builderConf = WebClient.getConfig(builder);
		builderConf.getInInterceptors().add(new LoggingInInterceptor());
		builderConf.getOutInterceptors().add(new LoggingOutInterceptor());
		Response response = builder.get();

		assertEquals(response.getStatus(), Response.Status.OK.getStatusCode());
	}

	@Test(invocationCount = 3)
	public void addAndGetDomain()
	{
		// externalID is x:NCName therefore cannot start with a number
		final DomainProperties domainProperties = new DomainProperties("Test domain", "external" + Integer.toString(domainExternalId), null);
		domainExternalId += 1;
		final Link domainLink = client.addDomain(domainProperties);
		assertNotNull(domainLink, "Domain creation failure");

		// The link href gives the new domain ID
		final String domainId = domainLink.getHref();
		LOGGER.debug("Added domain ID={}", domainId);
		assertTrue(createdDomainIds.add(domainId), "Domain ID uniqueness violation: Conflict on domain ID=" + domainId);

		// verify link appears on GET /domains
		final Resources domainResources = client.getDomains(null);
		assertNotNull(domainResources, "No domain found");
		for (final Link link : domainResources.getLinks())
		{
			final String href = domainLink.getHref();
			if (domainId.equals(href))
			{
				return;
			}
		}

		fail("Test domain added by 'addAndGetDomain' not found in links from getDomains");
	}

	@Test(expectedExceptions = BadRequestException.class)
	public void addDomainWithTooBigDescription()
	{
		char[] chars = new char[1001];
		Arrays.fill(chars, 'a');
		String description = new String(chars);
		// externalID is x:NCName therefore cannot start with a number
		final DomainProperties domainProperties = new DomainProperties(description, "external" + Integer.toString(domainExternalId), null);
		domainExternalId += 1;
		client.addDomain(domainProperties);
	}

	@Test(expectedExceptions = BadRequestException.class)
	public void addDomainWithTooBigExternalId()
	{
		// for maxAttributeSize = 500, exception raised only when chars.length > 910! WHY? Possible issue with woodstox library.
		// FIXME: report this issue to CXF/Woodstox
		char[] chars = new char[911];
		Arrays.fill(chars, 'a');
		String externalId = new String(chars);
		// externalID is x:NCName therefore cannot start with a number
		final DomainProperties domainProperties = new DomainProperties("test", externalId, null);
		domainExternalId += 1;
		client.addDomain(domainProperties);
	}

	@Test(dependsOnMethods = { "addAndGetDomain" })
	public void getDomains()
	{
		final Resources domainResources = client.getDomains(null);
		assertNotNull(domainResources, "No domain found");
		// match retrieved domains against created ones in addDomain test
		int matchedDomainCount = 0;
		for (final Link domainLink : domainResources.getLinks())
		{
			final String domainId = domainLink.getHref();
			if (createdDomainIds.contains(domainId))
			{
				matchedDomainCount += 1;
			}
		}

		assertEquals(matchedDomainCount, createdDomainIds.size(), "Test domains added by 'addDomain' not all found by getDomains");
	}

	@Test(dependsOnMethods = { "addAndGetDomain" })
	public void getDomain()
	{
		final String testDomainId = createdDomainIds.iterator().next();
		Domain testDomainResource = client.getDomainResource(testDomainId).getDomain();
		assertNotNull(testDomainResource, String.format("Error retrieving domain ID=%s", testDomainId));
	}

	@Test(dependsOnMethods = { "getDomain" })
	public void getDomainByExternalId()
	{
		String createdDomainId = createdDomainIds.iterator().next();
		String externalId = client.getDomainResource(createdDomainId).getDomain().getProperties().getExternalId();

		final List<Link> domainLinks = client.getDomains(externalId).getLinks();
		// verify that there is only one domain resource link and it is the one we are looking for
		assertEquals(domainLinks.size(), 1);

		String matchedDomainId = domainLinks.get(0).getHref();
		assertEquals(matchedDomainId, createdDomainId, "getDomains(externalId) returned wrong result, i.e. linked domainId does not have the same externalId");
	}

	@Test(dependsOnMethods = { "getDomainByExternalId" })
	public void deleteDomains()
	{
		for (final String domainId : createdDomainIds)
		{
			LOGGER.debug("Deleting domain ID={}", domainId);
			final DomainResource domainResource = client.getDomainResource(domainId);
			final DomainProperties domainProperties = domainResource.deleteDomain();
			assertNotNull(domainProperties, String.format("Error deleting domain ID=%s", domainId));

			boolean isDeleted = false;
			try
			{
				// try to do something on the domain expected to be deleted -> MUST fail
				domainResource.getDomain();
			} catch (NotFoundException nfe)
			{
				isDeleted = true;
			}

			assertTrue(isDeleted, String.format("Error deleting domain ID=%s", domainId));
		}
	}

}
