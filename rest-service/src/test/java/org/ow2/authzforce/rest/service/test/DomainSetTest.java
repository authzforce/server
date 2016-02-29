/**
 * Copyright (C) 2015-2015 Thales Services SAS. All rights reserved. No warranty, explicit or implicit, provided.
 */
package org.ow2.authzforce.rest.service.test;

/**
 *
 *
 */
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.ws.rs.BadRequestException;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;

import oasis.names.tc.xacml._3_0.core.schema.wd_17.Request;
import oasis.names.tc.xacml._3_0.core.schema.wd_17.Response;

import org.apache.cxf.interceptor.LoggingInInterceptor;
import org.apache.cxf.interceptor.LoggingOutInterceptor;
import org.apache.cxf.jaxrs.client.ClientConfiguration;
import org.apache.cxf.jaxrs.client.WebClient;
import org.ow2.authzforce.pap.dao.flatfile.FlatFileDAOUtils;
import org.ow2.authzforce.rest.api.jaxrs.DomainResource;
import org.ow2.authzforce.rest.api.xmlns.Domain;
import org.ow2.authzforce.rest.api.xmlns.DomainProperties;
import org.ow2.authzforce.rest.api.xmlns.Resources;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.test.context.ContextConfiguration;
import org.testng.annotations.Optional;
import org.testng.annotations.Parameters;
import org.testng.annotations.Test;
import org.w3._2005.atom.Link;

@ContextConfiguration(locations = { "classpath:META-INF/spring/applicationContext.xml" })
public class DomainSetTest extends RestServiceTest
{
	private static final Logger LOGGER = LoggerFactory.getLogger(DomainSetTest.class);

	private int domainExternalId = 0;
	private Set<String> createdDomainIds = new HashSet<>();

	@Parameters({ "app.base.url" })
	@Test
	public void getWADL(@Optional(DEFAULT_APP_BASE_URL) String appBaseUrl)
	{
		WebTarget target = ClientBuilder.newClient().target(appBaseUrl).queryParam("_wadl", "");
		Invocation.Builder builder = target.request();
		final ClientConfiguration builderConf = WebClient.getConfig(builder);
		builderConf.getInInterceptors().add(new LoggingInInterceptor());
		builderConf.getOutInterceptors().add(new LoggingOutInterceptor());
		javax.ws.rs.core.Response response = builder.get();

		assertEquals(response.getStatus(), javax.ws.rs.core.Response.Status.OK.getStatusCode());
	}

	@Test(invocationCount = 3)
	public void addAndGetDomain()
	{
		// externalID is x:NCName therefore cannot start with a number
		final DomainProperties domainProperties = new DomainProperties("Test domain", "external"
				+ Integer.toString(domainExternalId));
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
			final String href = link.getHref();
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
		char[] chars = new char[MAX_XML_TEXT_LENGTH + 1];
		Arrays.fill(chars, 'a');
		String description = new String(chars);
		// externalID is x:NCName therefore cannot start with a number
		final DomainProperties domainProperties = new DomainProperties(description, "external"
				+ Integer.toString(domainExternalId));
		domainExternalId += 1;
		client.addDomain(domainProperties);
	}

	@Test(expectedExceptions = BadRequestException.class)
	public void addDomainWithTooBigExternalId()
	{
		// for maxAttributeSize = 500, exception raised only when chars.length >
		// 910! WHY? Possible issue with woodstox library.
		// FIXME: report this issue to CXF/Woodstox
		char[] chars = new char[911];
		Arrays.fill(chars, 'a');
		String externalId = new String(chars);
		// externalID is x:NCName therefore cannot start with a number
		final DomainProperties domainProperties = new DomainProperties("test", externalId);
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

		assertEquals(matchedDomainCount, createdDomainIds.size(),
				"Test domains added by 'addDomain' not all found by getDomains");
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
		// verify that there is only one domain resource link and it is the one
		// we are looking for
		assertEquals(domainLinks.size(), 1);

		String matchedDomainId = domainLinks.get(0).getHref();
		assertEquals(matchedDomainId, createdDomainId, "getDomains(externalId) returned wrong domainId: "
				+ matchedDomainId + " instead of " + createdDomainId);
	}

	private static final String SAMPLE_DOMAIN_ID = SAMPLE_DOMAIN_DIR.getFileName().toString();
	private static final Path SAMPLE_DOMAIN_COPY_DIR = new File(DOMAINS_DIR, SAMPLE_DOMAIN_ID).toPath();

	@Parameters({ "start.server" })
	@Test(dependsOnMethods = { "getDomains", "getDomainByExternalId" })
	public void getDomainsAfterFileModifications(@Optional("true") boolean startServer)
			throws IllegalArgumentException, IOException
	{
		// skip test if server not started locally
		if (!startServer)
		{
			return;
		}

		// we add one domain directory and delete another existing one on disk

		FlatFileDAOUtils.copyDirectory(SAMPLE_DOMAIN_DIR, SAMPLE_DOMAIN_COPY_DIR, 2);

		// delete existing domain on disk, but first get its externalId for later testing
		String deletedDomainId = createdDomainIds.iterator().next();
		String deletedDomainExternalId = client.getDomainResource(deletedDomainId).getDomain().getProperties()
				.getExternalId();
		File deleteSrcDir = new File(DOMAINS_DIR, deletedDomainId);
		FlatFileDAOUtils.deleteDirectory(deleteSrcDir.toPath(), 2);

		final Resources domainResources = client.getDomains(null);
		// match retrieved domains against created ones in addDomain test
		Set<String> newDomainIds = new HashSet<>();
		for (final Link domainLink : domainResources.getLinks())
		{
			newDomainIds.add(domainLink.getHref());
		}

		// Tests for deleted domain
		assertFalse(newDomainIds.contains(deletedDomainId), "Sync from disk getDomains() failed: domain ID "
				+ deletedDomainId + " still returned by REST API although domain directory deleted on disk");
		try
		{
			client.getDomainResource(deletedDomainId).getDomain();
			fail("Sync from disk with getDomains() failed: getDomain(" + deletedDomainId
					+ ") on REST API succeeds although domain directory deleted on disk");
		} catch (NotFoundException e)
		{
			// OK
		}

		// test externalId on deleted domain
		try
		{
			client.getDomains(deletedDomainExternalId).getLinks();
			fail("Sync from disk with getDomains() failed: getDomains(externalId = " + deletedDomainExternalId
					+ ") on REST API succeeds although domain directory deleted on disk");
		} catch (NotFoundException e)
		{
			// OK
		}

		createdDomainIds.remove(deletedDomainId);

		// Test for domain created on disk
		assertTrue(newDomainIds.contains(SAMPLE_DOMAIN_ID), "Manual sync with getDomains() failed: domain ID "
				+ SAMPLE_DOMAIN_ID + " not returned by REST API although domain directory created on disk");
		Domain testDomainResource = client.getDomainResource(SAMPLE_DOMAIN_ID).getDomain();
		assertNotNull(testDomainResource, "Manual sync with getDomains() failed: domain ID " + SAMPLE_DOMAIN_ID
				+ " failed although domain directory created on disk");
		createdDomainIds.add(SAMPLE_DOMAIN_ID);

		String externalId = testDomainResource.getProperties().getExternalId();
		// test externalId
		final List<Link> domainLinks = client.getDomains(externalId).getLinks();
		// verify that there is only one domain resource link and it is the one
		// we are looking for
		assertEquals(domainLinks.size(), 1);
		String matchedDomainId = domainLinks.get(0).getHref();
		assertEquals(matchedDomainId, SAMPLE_DOMAIN_ID,
				"Manual sync with getDomains() failed: getDomains(externalId = " + externalId
						+ ") returned wrong domainId: " + matchedDomainId + " instead of " + SAMPLE_DOMAIN_ID);

	}

	@Parameters({ "start.server" })
	@Test(dependsOnMethods = { "getDomainsAfterFileModifications" })
	public void deleteDomainAfterDirectoryDeleted(@Optional("true") boolean startServer)
			throws IllegalArgumentException, IOException
	{
		// skip test if server not started locally
		if (!startServer)
		{
			return;
		}

		// delete on disk
		FlatFileDAOUtils.deleteDirectory(SAMPLE_DOMAIN_DIR, 2);

		// sync API cache
		DomainResource domainRes = client.getDomainResource(SAMPLE_DOMAIN_ID);
		DomainProperties deletedDomainProps = domainRes.deleteDomain();
		// make sure it's done
		try
		{
			// try to do something on the domain expected to be deleted ->
			// MUST fail
			domainRes.getDomain();
			fail("Error deleting domain with API deleteDomain() after deleting directory on disk: getDomain() still returns 200");
		} catch (NotFoundException nfe)
		{
			// OK
		}

		// try with externalId
		List<Link> links = client.getDomains(deletedDomainProps.getExternalId()).getLinks();
		assertTrue(
				links.isEmpty(),
				"Error deleting domain with API deleteDomain() after deleting directory on disk: getDomains(externalId) still returns link to domain");

	}

	@Parameters({ "start.server" })
	@Test(dependsOnMethods = { "deleteDomainAfterDirectoryDeleted" })
	public void getPdpAfterDomainDirCreated(@Optional("true") boolean startServer) throws IllegalArgumentException,
			IOException, JAXBException
	{
		// skip test if server not started locally
		if (!startServer)
		{
			return;
		}

		// verify domain does not exist by trying the PDP
		DomainResource testDomainRes = client.getDomainResource(SAMPLE_DOMAIN_ID);
		File testDir = new File(XACML_SAMPLES_DIR, "IIIG301");
		Unmarshaller unmarshaller = JAXB_CTX.createUnmarshaller();
		final Request request = (Request) unmarshaller.unmarshal(new File(testDir, REQUEST_FILENAME));
		try
		{
			testDomainRes.getPdpResource().requestPolicyDecision(request);
			fail("Test precondition failed: domain " + SAMPLE_DOMAIN_ID + " already exists");
		} catch (NotFoundException e)
		{
			// OK
		}

		// create domain directory on disk
		FlatFileDAOUtils.copyDirectory(SAMPLE_DOMAIN_DIR, SAMPLE_DOMAIN_COPY_DIR, 2);
		// check PDP returned policy identifier
		final Response actualResponse = testDomainRes.getPdpResource().requestPolicyDecision(request);
		createdDomainIds.add(SAMPLE_DOMAIN_ID);
		assertTrue(
				actualResponse != null,
				"Manual sync with PDP API method requestPolicyDecision() failed: could not get PDP response after creating domain directory on disk");
	}

	@Test(dependsOnMethods = { "getDomainsAfterFileModifications" })
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
				// try to do something on the domain expected to be deleted ->
				// MUST fail
				domainResource.getDomain();
			} catch (NotFoundException nfe)
			{
				isDeleted = true;
			}

			assertTrue(isDeleted, String.format("Error deleting domain ID=%s", domainId));
		}
	}

}
