/**
 * Copyright (C) 2012-2017 Thales Services SAS.
 *
 * This file is part of AuthzForce CE.
 *
 * AuthzForce CE is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * AuthzForce CE is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with AuthzForce CE.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.ow2.authzforce.webapp.test;

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
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.ws.rs.BadRequestException;
import javax.ws.rs.ClientErrorException;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.ServerErrorException;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response.Status;
import javax.xml.bind.JAXBException;

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
import org.ow2.authzforce.rest.api.xmlns.ProductMetadata;
import org.ow2.authzforce.rest.api.xmlns.Resources;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.SkipException;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Optional;
import org.testng.annotations.Parameters;
import org.testng.annotations.Test;
import org.w3._2005.atom.Link;
import org.w3._2005.atom.Relation;

/**
 * Tests on REST API's root resources: /?_wadl, /version, /domains
 *
 */
public class RootResourcesTest extends RestServiceTest
{

	private static final Logger LOGGER = LoggerFactory.getLogger(RootResourcesTest.class);

	private int nextCreatedDomainIndex = 0;
	private final Set<String> createdDomainIds = new HashSet<>();

	/**
	 * Test parameters from testng.xml are ignored when executing with maven surefire plugin, so we use default values for all.
	 * 
	 * WARNING: the BeforeTest-annotated method must be in the test class, not in a super class although the same method logic is used in other test class
	 * 
	 * @param remoteAppBaseUrl
	 * @param enableFastInfoset
	 * @param domainSyncIntervalSec
	 * @throws Exception
	 * 
	 *             NB: use Boolean class instead of boolean primitive type for Testng parameter, else the default value in @Optional annotation is not handled properly.
	 */
	@Parameters({ "remote.base.url", "enableFastInfoset", "useJSON", "enableDoSMitigation", "org.ow2.authzforce.domains.sync.interval", "enablePdpOnly" })
	@BeforeTest()
	public void beforeTest(@Optional final String remoteAppBaseUrl, @Optional("false") final Boolean enableFastInfoset, @Optional("false") final Boolean useJSON,
			@Optional("true") final Boolean enableDoSMitigation, @Optional("-1") final int domainSyncIntervalSec, @Optional("false") final Boolean enablePdpOnly) throws Exception
	{
		startServerAndInitCLient(remoteAppBaseUrl, useJSON ? ClientType.JSON : (enableFastInfoset ? ClientType.FAST_INFOSET : ClientType.XML), enableDoSMitigation, domainSyncIntervalSec,
				enablePdpOnly);
	}

	/**
	 * 
	 * WARNING: the AfterTest-annotated method must be in the test class, not in a super class although the same method logic is used in other test class
	 *
	 * @throws Exception
	 */
	@AfterTest
	public void afterTest() throws Exception
	{
		// shhutdown server
		shutdownServer();

		// clean up domains directory
		FlatFileDAOUtils.deleteDirectory(DOMAINS_DIR.toPath(), 4);
		DOMAINS_DIR.mkdir();
	}

	@Parameters({ "remote.base.url" })
	@Test
	public void getWADL(@Optional final String remoteAppBaseUrlParam)
	{
		final String remoteAppBaseUrl = remoteAppBaseUrlParam == null || remoteAppBaseUrlParam.isEmpty() ? WebClient.getConfig(domainsAPIProxyClient).getEndpoint().getEndpointInfo().getAddress()
				: remoteAppBaseUrlParam;
		final WebTarget target = ClientBuilder.newClient().target(remoteAppBaseUrl).queryParam("_wadl", "");
		final Invocation.Builder builder = target.request();
		// if (LOGGER.isDebugEnabled())
		// {
		final ClientConfiguration builderConf = WebClient.getConfig(builder);
		builderConf.getInInterceptors().add(new LoggingInInterceptor());
		builderConf.getOutInterceptors().add(new LoggingOutInterceptor());
		// }

		final javax.ws.rs.core.Response response = builder.get();
		assertEquals(response.getStatus(), javax.ws.rs.core.Response.Status.OK.getStatusCode());
	}

	@Test
	public void getProductMetadata()
	{
		final ProductMetadata prodMeta;
		try
		{
			prodMeta = prodMetadataResClient.getProductMetadata();
			assertEquals(prodMeta.getName(), "authzforce-ce-server", "Wrong product name returned by GET /version");
			assertTrue(prodMeta.getVersion().matches("^\\d+\\.\\d+\\.\\d+$"), "Wrong product version returned by GET /version");
			assertTrue(prodMeta.getReleaseDate().isValid(), "Wrong product release date returned by GET /version");
			assertEquals(prodMeta.getDoc(), "https://authzforce.github.io/fiware/authorization-pdp-api-spec/5.2/");
		}
		catch (final ServerErrorException e)
		{
			fail("GET /version failed", e);
		}
	}

	@Parameters({ "remote.base.url", "enableFastInfoset" })
	@Test()
	public void getDomainsWithoutAcceptHeader(@Optional final String remoteAppBaseUrlParam, @Optional("false") final Boolean enableFastInfoset)
	{
		// try to use application/fastinfoset
		final String remoteAppBaseUrl = remoteAppBaseUrlParam == null || remoteAppBaseUrlParam.isEmpty() ? WebClient.getConfig(domainsAPIProxyClient).getEndpoint().getEndpointInfo().getAddress()
				: remoteAppBaseUrlParam;
		final WebTarget target = ClientBuilder.newClient().target(remoteAppBaseUrl).path("domains");
		final Invocation.Builder builder = target.request();
		// if (LOGGER.isDebugEnabled())
		// {
		final ClientConfiguration builderConf = WebClient.getConfig(builder);
		builderConf.getInInterceptors().add(new LoggingInInterceptor());
		builderConf.getOutInterceptors().add(new LoggingOutInterceptor());
		// }

		final javax.ws.rs.core.Response response = builder.get();
		assertEquals(response.getStatus(), javax.ws.rs.core.Response.Status.OK.getStatusCode());
		assertEquals(response.getMediaType(), MediaType.APPLICATION_XML_TYPE);
	}

	/**
	 * Get /domains with Accept=application/fastinfoset although fastinfoset disabled. Should result in bad request
	 * 
	 * @param remoteAppBaseUrlParam
	 * @param enableFastInfoset
	 */
	@Parameters({ "remote.base.url", "enableFastInfoset" })
	@Test()
	public void getDomainsWithBadAcceptHeader(@Optional final String remoteAppBaseUrlParam, @Optional("false") final Boolean enableFastInfoset)
	{
		if (!enableFastInfoset)
		{
			// try to use application/fastinfoset
			final String remoteAppBaseUrl = remoteAppBaseUrlParam == null || remoteAppBaseUrlParam.isEmpty() ? WebClient.getConfig(domainsAPIProxyClient).getEndpoint().getEndpointInfo().getAddress()
					: remoteAppBaseUrlParam;
			final WebTarget target = ClientBuilder.newClient().target(remoteAppBaseUrl).path("domains");
			final Invocation.Builder builder = target.request().accept("application/fastinfoset");
			// if (LOGGER.isDebugEnabled())
			// {
			final ClientConfiguration builderConf = WebClient.getConfig(builder);
			builderConf.getInInterceptors().add(new LoggingInInterceptor());
			builderConf.getOutInterceptors().add(new LoggingOutInterceptor());
			// }

			final javax.ws.rs.core.Response response = builder.get();
			/**
			 * CXF should return code 500 with Payload: "No message body writer has been found for class org.ow2.authzforce.rest.api.xmlns.Resources, ContentType: application/fastinfoset"
			 */
			assertEquals(response.getStatus(), javax.ws.rs.core.Response.Status.NOT_ACCEPTABLE.getStatusCode());
		}
	}

	@Test(invocationCount = 3)
	@Parameters({ "enablePdpOnly" })
	public void addAndGetDomain(@Optional("false") final Boolean enablePdpOnly) throws IllegalArgumentException, IOException, JAXBException
	{
		// externalID is xs:NCName therefore cannot start with a number
		final String nextCreatedDomainIndexStr = Integer.toString(nextCreatedDomainIndex);
		final String externalId = "external" + nextCreatedDomainIndexStr;
		nextCreatedDomainIndex += 1;

		final DomainProperties domainProperties = new DomainProperties("Test domain", externalId);
		final Link domainLink;
		try
		{
			domainLink = domainsAPIProxyClient.addDomain(domainProperties);
		}
		catch (final ServerErrorException e)
		{
			assertTrue(enablePdpOnly, "addDomain method not allowed although enablePdpOnly=false");
			/*
			 * enablePdpOnly=true does not allow adding domain via REST API, so we add it on the filesystem directly for other tests to work
			 */
			FlatFileDAOUtils.copyDirectory(SAMPLE_DOMAIN_DIR, SAMPLE_DOMAIN_COPY_DIR, 3);
			// use the domainIndex as domainId
			final String domainId = nextCreatedDomainIndexStr;
			final File domainDir = new File(DOMAINS_DIR, domainId);
			Files.move(SAMPLE_DOMAIN_COPY_DIR, domainDir.toPath());
			final org.ow2.authzforce.pap.dao.flatfile.xmlns.DomainProperties newProps = new org.ow2.authzforce.pap.dao.flatfile.xmlns.DomainProperties(domainProperties.getDescription(), externalId,
					null, null, false);
			final File domainPropertiesFile = new File(domainDir, RestServiceTest.DOMAIN_PROPERTIES_FILENAME);
			RestServiceTest.JAXB_CTX.createMarshaller().marshal(newProps, domainPropertiesFile);
			LOGGER.debug("Added domain ID={} directlry on filesystem (enablePdpOnly=true)", domainId);
			createdDomainIds.add(domainId);
			return;
		}
		catch (final Exception e)
		{
			fail("Unexpected exception:", e);
			return;
		}

		assertFalse(enablePdpOnly, "addDomain method allowed although enablePdpOnly=true");

		assertNotNull(domainLink, "Domain creation failure");

		// The link href gives the new domain ID
		final String domainId = domainLink.getHref();
		LOGGER.debug("Added domain ID={}", domainId);
		assertTrue(createdDomainIds.add(domainId), "Domain ID uniqueness violation: Conflict on domain ID=" + domainId);

		// verify link appears on GET /domains
		final Resources domainResources = domainsAPIProxyClient.getDomains(null);
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

	@Parameters({ "enableFastInfoset" })
	@Test
	public void addDomainWithTooBigDescription(@Optional("false") final Boolean enableFastInfoset)
	{
		if (enableFastInfoset)
		{
			throw new SkipException("Not supported in FastInfoset mode");
		}

		/*
		 * FIXME: the CXF property 'org.apache.cxf.stax.maxTextLength' is not supported with Fastinfoset (you will get exception ... cannot be cast to XmlStreamReader2). See
		 * https://issues.apache.org/jira/browse/CXF-6848.
		 */
		final char[] chars = new char[XML_MAX_TEXT_LENGTH + 1];
		Arrays.fill(chars, 'a');
		final String description = new String(chars);
		// externalID is x:NCName therefore cannot start with a number
		final DomainProperties domainProperties = new DomainProperties(description, "external" + Integer.toString(nextCreatedDomainIndex));
		nextCreatedDomainIndex += 1;
		try
		{
			domainsAPIProxyClient.addDomain(domainProperties);
			fail("Bad request to add domain (too big description element) accepted");
		}
		catch (final BadRequestException e)
		{
			// Bad request as expected (e.g. for XML API)
		}
		catch (final ClientErrorException e)
		{
			// The error may be 413 Request Entity Too Large (e.g. for JSON API)
			assertEquals(e.getResponse().getStatus(), Status.REQUEST_ENTITY_TOO_LARGE.getStatusCode());
		}
	}

	@Parameters({ "enableFastInfoset" })
	@Test
	public void addDomainWithTooBigExternalId(@Optional("false") final Boolean enableFastInfoset)
	{
		/*
		 * FIXME: the CXF property 'org.apache.cxf.stax.maxAttributeSize' is not supported with Fastinfoset (you will get exception ... cannot be cast to XmlStreamReader2). See
		 * https://issues.apache.org/jira/browse/CXF-6848.
		 */
		if (enableFastInfoset)
		{
			throw new SkipException("Not supported in FastInfoset mode");
		}

		final char[] chars = new char[XML_MAX_ATTRIBUTE_SIZE_EFFECTIVE];
		Arrays.fill(chars, 'a');
		final String externalId = new String(chars);
		// externalID is x:NCName therefore cannot start with a number
		final DomainProperties domainProperties = new DomainProperties("test", externalId);
		nextCreatedDomainIndex += 1;
		try
		{
			domainsAPIProxyClient.addDomain(domainProperties);
			fail("Bad request to add domain (too big externalId attribute) accepted");
		}
		catch (final BadRequestException e)
		{
			// Bad request as expected (e.g. for XML API)
		}
		catch (final ClientErrorException e)
		{
			// The error may be 413 Request Entity Too Large (e.g. for JSON API)
			assertEquals(e.getResponse().getStatus(), Status.REQUEST_ENTITY_TOO_LARGE.getStatusCode());
		}
	}

	@Parameters({ "enablePdpOnly" })
	@Test(dependsOnMethods = { "addAndGetDomain" })
	public void getDomains(@Optional("false") final Boolean enablePdpOnly)
	{
		final Resources domainResources;
		try
		{
			domainResources = domainsAPIProxyClient.getDomains(null);
			assertFalse(enablePdpOnly, "getDomains method allowed although enablePdpOnly=true");
		}
		catch (final ServerErrorException e)
		{
			assertTrue(enablePdpOnly, "getDomains method not allowed although enablePdpOnly=false");
			return;
		}

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

	@Parameters({ "enablePdpOnly" })
	@Test(dependsOnMethods = { "getDomains" })
	public void getDomain(@Optional("false") final Boolean enablePdpOnly)
	{
		final String testDomainId = createdDomainIds.iterator().next();
		final Domain testDomainResource;
		try
		{
			testDomainResource = domainsAPIProxyClient.getDomainResource(testDomainId).getDomain();
			assertFalse(enablePdpOnly, "getDomain method allowed although enablePdpOnly=true");
		}
		catch (final ServerErrorException e)
		{
			assertTrue(enablePdpOnly, "getDomain method not allowed although enablePdpOnly=false");
			return;
		}

		assertNotNull(testDomainResource, String.format("Error retrieving domain ID=%s", testDomainId));
		final Link pdpLink = DomainAPIHelper.getMatchingLink("/pdp", testDomainResource.getChildResources().getLinks());
		assertNotNull(pdpLink, "Missing link to PDP in response to getDomain(" + testDomainId + ")");
		assertEquals(pdpLink.getRel(), Relation.HTTP_DOCS_OASIS_OPEN_ORG_NS_XACML_RELATION_PDP, "PDP link relation in response to getDomain(" + testDomainId
				+ ") does not comply with REST profile of XACML 3.0");
	}

	@Parameters({ "enablePdpOnly" })
	@Test(dependsOnMethods = { "getDomain" })
	public void getDomainByExternalId(@Optional("false") final Boolean enablePdpOnly)
	{
		final String createdDomainId = createdDomainIds.iterator().next();
		final String externalId;
		try
		{
			externalId = domainsAPIProxyClient.getDomainResource(createdDomainId).getDomain().getProperties().getExternalId();
			assertFalse(enablePdpOnly, "getDomain method allowed although enablePdpOnly=true");
		}
		catch (final ServerErrorException e)
		{
			assertTrue(enablePdpOnly, "getDomain method not allowed although enablePdpOnly=false");
			return;
		}

		final List<Link> domainLinks = domainsAPIProxyClient.getDomains(externalId).getLinks();
		/*
		 * verify that there is only one domain resource link and it is the one we are looking for
		 */
		assertEquals(domainLinks.size(), 1);

		final String matchedDomainId = domainLinks.get(0).getHref();
		assertEquals(matchedDomainId, createdDomainId, "getDomains(externalId) returned wrong domainId: " + matchedDomainId + " instead of " + createdDomainId);
	}

	@Parameters({ "enablePdpOnly" })
	@Test(dependsOnMethods = { "getDomainByExternalId" })
	public void deleteDomain(@Optional("false") final Boolean enablePdpOnly)
	{
		final String createdDomainId = createdDomainIds.iterator().next();
		final DomainResource domainRes = domainsAPIProxyClient.getDomainResource(createdDomainId);
		final DomainProperties deletedDomainProps;
		try
		{
			deletedDomainProps = domainRes.deleteDomain();
			assertFalse(enablePdpOnly, "deleteDomain method allowed but enablePdpOnly=true");
		}
		catch (final ServerErrorException e)
		{
			assertTrue(enablePdpOnly, "deleteDomain method not allowed but enablePdpOnly=false");
			return;
		}

		// make sure it's done
		try
		{
			// try to do something on the domain expected to be deleted ->
			// MUST fail
			domainRes.getDomain();
			fail("Error deleting domain " + createdDomainId + " with API deleteDomain(): getDomain() still returns 200");
		}
		catch (final NotFoundException nfe)
		{
			// OK
		}

		// try with externalId
		final String externalId = deletedDomainProps.getExternalId();
		final List<Link> links = domainsAPIProxyClient.getDomains(externalId).getLinks();
		assertTrue(links.isEmpty(), "Error deleting domain " + createdDomainId + " with API deleteDomain(): getDomains(externalId=" + externalId + ") still returns link to domain");

		createdDomainIds.remove(createdDomainId);
	}

	/**
	 * We don't want deleteDomain happening after this method and before next one (deleteDomainAfterDirectoryDeleted()), otherwise SAMPLE_DOMAIN_COPY_DIR directory might be removed before test
	 * deleteDomainAfterDirectoryDeleted() occurs, causing unexpected error
	 * 
	 * @param remoteAppBaseUrl
	 * @throws IllegalArgumentException
	 * @throws IOException
	 */
	@Parameters({ "remote.base.url" })
	@Test(dependsOnMethods = { "deleteDomain" })
	public void getDomainsAfterFileModifications(@Optional final String remoteAppBaseUrl) throws IllegalArgumentException, IOException
	{
		// skip test if server is remote (remoteAppBaseUrl != null)
		if (remoteAppBaseUrl != null && !remoteAppBaseUrl.isEmpty())
		{
			return;
		}

		// we add one domain directory and delete another existing one on disk
		FlatFileDAOUtils.copyDirectory(SAMPLE_DOMAIN_DIR, SAMPLE_DOMAIN_COPY_DIR, 3);

		// delete existing domain on disk, but first get its externalId for later testing
		final String deletedDomainId = createdDomainIds.iterator().next();
		final String deletedDomainExternalId = domainsAPIProxyClient.getDomainResource(deletedDomainId).getDomain().getProperties().getExternalId();
		final File deleteSrcDir = new File(DOMAINS_DIR, deletedDomainId);
		FlatFileDAOUtils.deleteDirectory(deleteSrcDir.toPath(), 3);

		// Manual sync of files-to-cache with getDomains()
		final List<Link> linksToDomains = domainsAPIProxyClient.getDomains(null).getLinks();
		// match retrieved domains against created ones in addDomain test
		final Set<String> newDomainIds = new HashSet<>();
		for (final Link domainLink : linksToDomains)
		{
			newDomainIds.add(domainLink.getHref());
		}

		// Tests for deleted domain
		assertFalse(newDomainIds.contains(deletedDomainId), "Sync from disk getDomains() failed: domain ID " + deletedDomainId
				+ " still returned by REST API although domain directory deleted on disk");
		try
		{
			domainsAPIProxyClient.getDomainResource(deletedDomainId).getDomain();
			fail("Sync from disk with getDomains() failed: getDomain(" + deletedDomainId + ") on REST API succeeds although domain directory deleted on disk");
		}
		catch (final NotFoundException e)
		{
			// OK
		}

		// test externalId on deleted domain

		final List<Link> links = domainsAPIProxyClient.getDomains(deletedDomainExternalId).getLinks();
		assertTrue(links.isEmpty(), "Sync from disk with getDomains() failed: getDomains(externalId = " + deletedDomainExternalId + ") on REST API succeeds although domain directory deleted on disk");

		createdDomainIds.remove(deletedDomainId);

		// Test for domain created on disk
		assertTrue(newDomainIds.contains(SAMPLE_DOMAIN_ID), "Manual sync with getDomains() failed: domain ID " + SAMPLE_DOMAIN_ID
				+ " not returned by REST API although domain directory created on disk");
		final Domain testDomainResource = domainsAPIProxyClient.getDomainResource(SAMPLE_DOMAIN_ID).getDomain();
		assertNotNull(testDomainResource, "Manual sync with getDomains() failed: domain ID " + SAMPLE_DOMAIN_ID + " failed although domain directory created on disk");
		createdDomainIds.add(SAMPLE_DOMAIN_ID);

		final String externalId = testDomainResource.getProperties().getExternalId();
		if (externalId == null)
		{
			fail("Bad test data: test domain in directory '" + SAMPLE_DOMAIN_DIR + "' must have an externalId (modify the properties.xml file to add an externalId before running this test)");
		}

		// test externalId
		final List<Link> domainLinks = domainsAPIProxyClient.getDomains(externalId).getLinks();
		// verify that there is only one domain resource link and it is the one
		// we are looking for
		assertEquals(domainLinks.size(), 1);
		final String matchedDomainId = domainLinks.get(0).getHref();
		assertEquals(matchedDomainId, SAMPLE_DOMAIN_ID, "Manual sync with getDomains() failed: getDomains(externalId = " + externalId + ") returned wrong domainId: " + matchedDomainId
				+ " instead of " + SAMPLE_DOMAIN_ID);

	}

	@Parameters({ "remote.base.url" })
	@Test(dependsOnMethods = { "getDomainsAfterFileModifications" })
	public void deleteDomainAfterDirectoryDeleted(@Optional final String remoteAppBaseUrl) throws IllegalArgumentException, IOException
	{
		// skip test if server is remote (remoteAppBaseUrl != null)
		if (remoteAppBaseUrl != null && !remoteAppBaseUrl.isEmpty())
		{
			return;
		}

		// delete on disk
		FlatFileDAOUtils.deleteDirectory(SAMPLE_DOMAIN_COPY_DIR, 3);

		// sync API cache
		final DomainResource domainRes = domainsAPIProxyClient.getDomainResource(SAMPLE_DOMAIN_ID);
		final DomainProperties deletedDomainProps = domainRes.deleteDomain();
		// make sure it's done
		try
		{
			// try to do something on the domain expected to be deleted ->
			// MUST fail
			domainRes.getDomain();
			fail("Error deleting domain with API deleteDomain() after deleting directory on disk: getDomain() still returns 200");
		}
		catch (final NotFoundException nfe)
		{
			// OK
		}

		// try with externalId
		final List<Link> links = domainsAPIProxyClient.getDomains(deletedDomainProps.getExternalId()).getLinks();
		assertTrue(links.isEmpty(), "Error deleting domain with API deleteDomain() after deleting directory on disk: getDomains(externalId) still returns link to domain");

		createdDomainIds.remove(SAMPLE_DOMAIN_ID);
	}

	@Parameters({ "remote.base.url" })
	@Test(dependsOnMethods = { "deleteDomainAfterDirectoryDeleted" })
	public void getPdpAfterDomainDirCreated(@Optional final String remoteAppBaseUrl) throws IllegalArgumentException, IOException, JAXBException
	{
		// skip test if server is remote (remoteAppBaseUrl != null)
		if (remoteAppBaseUrl != null && !remoteAppBaseUrl.isEmpty())
		{
			return;
		}

		// verify domain does not exist by trying the PDP
		final DomainResource testDomainRes = domainsAPIProxyClient.getDomainResource(SAMPLE_DOMAIN_ID);
		final File testDir = new File(XACML_SAMPLES_DIR, "IIIG301");
		final Request request = (Request) unmarshaller.unmarshal(new File(testDir, REQUEST_FILENAME));

		try
		{
			testDomainRes.getPdpResource().requestPolicyDecision(request);
			fail("Test precondition failed: domain " + SAMPLE_DOMAIN_ID + " already exists");
		}
		catch (final NotFoundException e)
		{ // OK
		}

		// create domain directory on disk
		FlatFileDAOUtils.copyDirectory(SAMPLE_DOMAIN_DIR, SAMPLE_DOMAIN_COPY_DIR, 3);

		// check PDP returned policy identifier
		final Response actualResponse = testDomainRes.getPdpResource().requestPolicyDecision(request);
		createdDomainIds.add(SAMPLE_DOMAIN_ID);

		assertTrue(actualResponse != null, "Manual sync with PDP API method requestPolicyDecision() failed: could not get PDP response after creating domain directory on disk");
	}

	// @Test(dependsOnMethods = { "getPdpAfterDomainDirCreated" })
	public void deleteDomains()
	{
		for (final String domainId : createdDomainIds)
		{
			LOGGER.debug("Deleting domain ID={}", domainId);
			final DomainResource domainResource = domainsAPIProxyClient.getDomainResource(domainId);
			final DomainProperties domainProperties = domainResource.deleteDomain();
			assertNotNull(domainProperties, String.format("Error deleting domain ID=%s", domainId));

			boolean isDeleted = false;
			try
			{
				// try to do something on the domain expected to be deleted ->
				// MUST fail
				domainResource.getDomain();
			}
			catch (final NotFoundException nfe)
			{
				isDeleted = true;
			}

			assertTrue(isDeleted, String.format("Error deleting domain ID=%s", domainId));
		}
	}

}
