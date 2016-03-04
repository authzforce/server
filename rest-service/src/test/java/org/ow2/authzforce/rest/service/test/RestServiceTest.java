/**
 * Copyright (C) 2015-2015 Thales Services SAS. All rights reserved. No warranty, explicit or implicit, provided.
 */
package org.ow2.authzforce.rest.service.test;

/**
 *
 *
 */
import java.io.File;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.TimeZone;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import javax.xml.validation.Schema;

import oasis.names.tc.xacml._3_0.core.schema.wd_17.PolicySet;
import oasis.names.tc.xacml._3_0.core.schema.wd_17.Request;
import oasis.names.tc.xacml._3_0.core.schema.wd_17.Target;

import org.apache.cxf.endpoint.Server;
import org.apache.cxf.interceptor.Interceptor;
import org.apache.cxf.interceptor.LoggingInInterceptor;
import org.apache.cxf.interceptor.LoggingOutInterceptor;
import org.apache.cxf.jaxrs.JAXRSServerFactoryBean;
import org.apache.cxf.jaxrs.client.ClientConfiguration;
import org.apache.cxf.jaxrs.client.JAXRSClientFactory;
import org.apache.cxf.jaxrs.client.WebClient;
import org.apache.cxf.jaxrs.provider.JAXBElementProvider;
import org.apache.cxf.jaxrs.utils.schemas.SchemaHandler;
import org.apache.cxf.message.Message;
import org.ow2.authzforce.core.pdp.impl.PdpModelHandler;
import org.ow2.authzforce.core.xmlns.test.TestAttributeProvider;
import org.ow2.authzforce.pap.dao.flatfile.FlatFileDAOUtils;
import org.ow2.authzforce.pap.dao.flatfile.xmlns.DomainProperties;
import org.ow2.authzforce.rest.api.jaxrs.DomainsResource;
import org.ow2.authzforce.rest.api.xmlns.Resources;
import org.ow2.authzforce.rest.service.jaxrs.BadRequestExceptionMapper;
import org.ow2.authzforce.rest.service.jaxrs.ClientErrorExceptionMapper;
import org.ow2.authzforce.rest.service.jaxrs.DomainsResourceImpl;
import org.ow2.authzforce.rest.service.jaxrs.ErrorHandlerInterceptor;
import org.ow2.authzforce.rest.service.jaxrs.ServerErrorExceptionMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import org.testng.ITestContext;

@ContextConfiguration(locations = { "classpath:META-INF/spring/applicationContext.xml" })
abstract class RestServiceTest extends AbstractTestNGSpringContextTests
{
	/**
	 * Test context attribute set by the beforeSuite() to the initialized value of class member 'client'. To be reused
	 * in other test classes of the same test suite. Attribute value type: {@link DomainsResource}
	 */
	public static final String REST_CLIENT_TEST_CONTEXT_ATTRIBUTE_ID = "com.thalesgroup.authzforce.test.rest.client";

	/**
	 * Test context attribute set by the beforeSuite() to the initialized value of class member 'pdpModelHandler'. To be
	 * reused in other test classes of the same test suite. Attribute value type: {@link PdpModelHandler}
	 */
	public static final String PDP_MODEL_HANDLER_TEST_CONTEXT_ATTRIBUTE_ID = "com.thalesgroup.authzforce.test.pdp.model.handler";

	/**
	 * Test context attribute set by the beforeSuite() to the XML schema of all XML data sent/received by the API
	 * client. To be reused in other test classes of the same test suite. Attribute value type: {@link Schema}
	 */
	public static final String REST_CLIENT_API_SCHEMA_TEST_CONTEXT_ATTRIBUTE_ID = "com.thalesgroup.authzforce.test.rest.client.api.schema";

	protected static final int MAX_XML_TEXT_LENGTH = 1000;

	private static final String WADL_LOCATION = "classpath:/authz-api.wadl";

	protected static final File DOMAINS_DIR = new File("target/server.data/domains");

	protected static final String DEFAULT_APP_BASE_URL = "http://localhost:9080/";

	protected static final File XACML_SAMPLES_DIR = new File("src/test/resources/xacml.samples");
	static
	{
		if (!XACML_SAMPLES_DIR.exists())
		{
			throw new RuntimeException("XACML SAMPLES DIRECTORY NOT FOUND: " + XACML_SAMPLES_DIR);
		}
	}

	protected static final Path SAMPLE_DOMAIN_DIR = Paths
			.get("src/test/resources/domain.samples/A0bdIbmGEeWhFwcKrC9gSQ");
	static
	{
		if (!Files.exists(SAMPLE_DOMAIN_DIR))
		{
			throw new RuntimeException("SAMPLE DOMAIN DIRECTORY NOT FOUND: " + SAMPLE_DOMAIN_DIR);
		}
	}

	/*
	 * JAXB context for (un)marshalling XACML
	 */
	protected static final JAXBContext JAXB_CTX;

	static
	{
		try
		{
			JAXB_CTX = JAXBContext.newInstance(PolicySet.class, Request.class, Resources.class, DomainProperties.class,
					TestAttributeProvider.class);
		} catch (JAXBException e)
		{
			throw new RuntimeException("Error instantiating JAXB context for XML to Java binding", e);
		}
	}

	/**
	 * XACML request filename
	 */
	protected final static String REQUEST_FILENAME = "request.xml";

	/**
	 * XACML policy filename used by default when no PDP configuration file found, i.e. no file named "pdp.xml" exists
	 * in the test directory
	 */
	protected final static String TEST_POLICY_FILENAME = "policy.xml";

	/**
	 * Expected XACML response filename
	 */
	protected final static String EXPECTED_RESPONSE_FILENAME = "response.xml";

	protected static final Random PRNG = new Random();

	protected static final DateFormat UTC_DATE_WITH_MILLIS_FORMATTER = new SimpleDateFormat(
			"yyyy-MM-dd HH:mm:ss.SSS ('UTC')");
	static
	{
		UTC_DATE_WITH_MILLIS_FORMATTER.setTimeZone(TimeZone.getTimeZone("UTC"));
	}

	protected static PolicySet createDumbPolicySet(String policyId, String version)
	{
		return new PolicySet(null, null, null, new Target(null), null, null, null, policyId, version,
				"urn:oasis:names:tc:xacml:3.0:policy-combining-algorithm:deny-unless-permit", BigInteger.ZERO);
	}

	@Autowired
	@Qualifier("jaxbProvider")
	private JAXBElementProvider<?> serverJaxbProvider;

	@Autowired
	private DomainsResourceImpl domainsResourceBean;

	@Autowired
	@Qualifier("pdpModelHandler")
	protected PdpModelHandler pdpModelHandler;

	@Autowired
	private SchemaHandler clientApiSchemaHandler;

	private Server server;

	@Autowired
	private JAXBElementProvider<?> clientJaxbProvider;

	protected Unmarshaller unmarshaller = null;

	protected DomainsResource client = null;

	public final static String DOMAIN_PROPERTIES_FILENAME = "properties.xml";

	public final static String DOMAIN_POLICIES_DIRNAME = "policies";
	public final static String DOMAIN_PDP_CONF_FILENAME = "pdp.xml";

	protected void startServerAndInitCLient(String appBaseUrl, boolean startServer, int maxPolicyCountPerDomain,
			int maxVersionCountPerPolicy, boolean removeOldVersionsTooMany, int domainSyncIntervalSec,
			ITestContext testCtx) throws Exception
	{

		if (startServer)
		{
			/*
			 * Make sure the directory target/domains exists and is empty (see
			 * src/test/resources/META-INF/spring/server.xml for actual domains directory path)
			 */
			if (DOMAINS_DIR.exists())
			{
				// delete to start clean
				FlatFileDAOUtils.deleteDirectory(DOMAINS_DIR.toPath(), 4);
			}

			DOMAINS_DIR.mkdirs();

			// Override some server properties via JNDI
			try
			{
				// Create initial context
				System.setProperty(Context.INITIAL_CONTEXT_FACTORY, "org.eclipse.jetty.jndi.InitialContextFactory");
				System.setProperty(Context.URL_PKG_PREFIXES, "org.eclipse.jetty.jndi");

				InitialContext ic = new InitialContext();
				ic.createSubcontext("java:comp/env");
				ic.bind("java:comp/env/org.ow2.authzforce.domain.maxPolicyCount", maxPolicyCountPerDomain);
				ic.bind("java:comp/env/org.ow2.authzforce.domain.policy.maxVersionCount", maxVersionCountPerPolicy);
				ic.bind("java:comp/env/org.ow2.authzforce.domain.policy.removeOldVersionsIfTooMany",
						removeOldVersionsTooMany);
				ic.bind("java:comp/env/org.ow2.authzforce.domains.sync.interval", domainSyncIntervalSec);
			} catch (NamingException ex)
			{
				throw new RuntimeException("Error setting property via JNDI", ex);
			}

			/*
			 * Workaround for: http://stackoverflow.com/questions/10184602/accessing -spring-context-in-testngs
			 * -beforetest https://jira.spring.io/browse/SPR-4072 https://jira.spring.io/browse/SPR-5404 (duplicate of
			 * previous issue) springTestContextPrepareTestInstance() happens in
			 * 
			 * @BeforeClass before no access to Autowired beans by default in
			 * 
			 * @BeforeTest
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
			sf.setProviders(Arrays.asList(serverJaxbProvider, new BadRequestExceptionMapper(),
					new ClientErrorExceptionMapper(), new ServerErrorExceptionMapper()));
			sf.setServiceBean(domainsResourceBean);
			final Map<String, Object> jaxRsServerProperties = new HashMap<>();
			jaxRsServerProperties.put("org.apache.cxf.propagate.exception", "false");
			// XML security properties
			jaxRsServerProperties.put("org.apache.cxf.stax.maxChildElements", "10");
			jaxRsServerProperties.put("org.apache.cxf.stax.maxElementDepth", "10");
			// Maximum number of attributes per element
			jaxRsServerProperties.put("org.apache.cxf.stax.maxAttributeCount", "100");
			// Maximum size of a single attribute
			jaxRsServerProperties.put("org.apache.cxf.stax.maxAttributeSize", "500");
			// Maximum size of an element's text value
			jaxRsServerProperties.put("org.apache.cxf.stax.maxTextLength", Integer.toString(MAX_XML_TEXT_LENGTH, 10));

			sf.setProperties(jaxRsServerProperties);
			sf.setOutFaultInterceptors(Collections
					.<Interceptor<? extends Message>> singletonList(new ErrorHandlerInterceptor()));
			server = sf.create();

			testCtx.setAttribute(PDP_MODEL_HANDLER_TEST_CONTEXT_ATTRIBUTE_ID, pdpModelHandler);
		}

		/**
		 * Create the REST (JAX-RS) client
		 */
		/*
		 * WARNING: if tests are to be multi-threaded, modify according to Thread-safety section of CXF JAX-RS client
		 * API documentation http://cxf .apache.org/docs/jax-rs-client-api.html#JAX-RSClientAPI-ThreadSafety
		 */
		client = JAXRSClientFactory.create(appBaseUrl, DomainsResource.class,
				Collections.singletonList(clientJaxbProvider));

		final Schema apiSchema = this.clientApiSchemaHandler.getSchema();
		testCtx.setAttribute(REST_CLIENT_API_SCHEMA_TEST_CONTEXT_ATTRIBUTE_ID, apiSchema);

		unmarshaller = DomainSetTest.JAXB_CTX.createUnmarshaller();
		unmarshaller.setSchema(apiSchema);

		/**
		 * Request/response logging (for debugging).
		 */
		final ClientConfiguration clientConf = WebClient.getConfig(client);
		clientConf.getInInterceptors().add(new LoggingInInterceptor());
		clientConf.getOutInterceptors().add(new LoggingOutInterceptor());

		testCtx.setAttribute(REST_CLIENT_TEST_CONTEXT_ATTRIBUTE_ID, client);
	}

	protected void destroyServer() throws Exception
	{
		// server != null only if test suite property start.server = true
		if (server != null)
		{
			server.stop();
			server.destroy();
		}
	}

}
