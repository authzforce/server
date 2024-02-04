/*
 * Copyright (C) 2012-2024 THALES.
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

import org.apache.cxf.jaxrs.client.ClientConfiguration;
import org.apache.cxf.jaxrs.client.WebClient;
import org.ow2.authzforce.pap.dao.flatfile.FlatFileBasedDomainsDao;
import org.ow2.authzforce.rest.api.jaxrs.DomainResource;
import org.ow2.authzforce.rest.api.xmlns.DomainProperties;
import org.ow2.authzforce.rest.api.xmlns.Feature;
import org.ow2.authzforce.webapp.JsonRiCxfJaxrsProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.ITestContext;
import org.testng.annotations.*;
import org.w3._2005.atom.Link;

import jakarta.ws.rs.NotFoundException;
import jakarta.xml.bind.JAXBException;
import java.io.File;
import java.util.Collections;
import java.util.List;

import static org.testng.Assert.assertNotNull;

/**
 * Demo test
 */
public class DomainPdpResourceWithCustomXacmlJsonSchemaTest extends RestServiceTest
{

	private static final Logger LOGGER = LoggerFactory.getLogger(DomainPdpResourceWithCustomXacmlJsonSchemaTest.class);

	private DomainAPIHelper testDomainHelper = null;
	private DomainResource testDomain = null;
	private String testDomainId = null;
	private WebClient httpClient;

	private final String testDomainExternalId = "test" + PRNG.nextInt(100);

	/**
	 * Test parameters from testng.xml are ignored when executing with maven surefire plugin, so we use default values for all.
	 * <p>
	 * WARNING: the BeforeTest-annotated method must be in the test class, not in a super class although the same method logic is used in other test class
	 * 
	 * @param remoteAppBaseUrl base URL of remote app
	 * @throws Exception error
	 */
	@Parameters({ "remote.base.url", "xacmlJsonSchemaRelativePath" })
	@BeforeTest()
	public void beforeTest(@Optional("") final String remoteAppBaseUrl, @Optional("") final String xacmlJsonSchemaRelativePath) throws Exception
	{
		startServerAndInitCLient(remoteAppBaseUrl, ClientType.JSON, xacmlJsonSchemaRelativePath,true, -1, false);
	}

	/**
	 * 
	 * WARNING: the AfterTest-annotated method must be in the test class, not in a super class although the same method logic is used in other test class
	 *
	 * @throws Exception error
	 */
	@AfterTest
	public void afterTest() throws Exception
	{
		shutdownServer();
	}

	@BeforeClass
	public void addDomain() throws JAXBException
	{
		final Link domainLink = domainsAPIProxyClient.addDomain(new DomainProperties("Some description", testDomainExternalId));
		assertNotNull(domainLink, "Domain creation failure");

		// The link href gives the new domain ID
		testDomainId = domainLink.getHref();
		LOGGER.debug("Added domain ID={}", testDomainId);
		testDomain = domainsAPIProxyClient.getDomainResource(testDomainId);
		assertNotNull(testDomain, String.format("Error retrieving domain ID=%s", testDomainId));

		testDomainHelper = new DomainAPIHelper(testDomainId, testDomain, unmarshaller, pdpModelHandler);

		final ClientConfiguration apiProxyClientConf = WebClient.getConfig(domainsAPIProxyClient);
		final String appBaseUrl = apiProxyClientConf.getEndpoint().getEndpointInfo().getAddress();
		httpClient = WebClient.create(appBaseUrl, Collections.singletonList(new JsonRiCxfJaxrsProvider<>()), true);

		assertNotNull(testDomain, String.format("Error retrieving domain ID=%s", testDomainId));
	}

	@DataProvider
	public Object[][] getData(ITestContext context) {
		String xacmlTestDirsParameter = context.getCurrentXmlTest().getLocalParameters().get("xacmlTestDirs");
		String[] xacmlTestDirRelativePaths = xacmlTestDirsParameter.split(",");
		String customDatatypeParameter = context.getCurrentXmlTest().getLocalParameters().get("customDatatype");
		Object[][] returnValues = new Object[xacmlTestDirRelativePaths.length][2];
		int index = 0;
		for (Object[] each : returnValues) {
			each[0] = customDatatypeParameter;
			each[1] = xacmlTestDirRelativePaths[index++].trim();
		}
		return returnValues;
	}

	@Test(dataProvider = "getData")
	public void requestPdpWithValidXacmlJsonWithCustomJsonObjectDatatype(final String customDatatype, final String xacmlTestDirRelativePath) throws Exception
	{
		final File testDir = new File(RestServiceTest.XACML_SAMPLES_DIR, xacmlTestDirRelativePath);
		final Feature geometryDatatypeFeature = new Feature(customDatatype, FlatFileBasedDomainsDao.PdpFeatureType.DATATYPE.toString(), true);
		final List<Feature> pdpFeatures = Collections.singletonList(geometryDatatypeFeature);
		testDomainHelper.requestXacmlJsonPDP(testDir, pdpFeatures, !IS_EMBEDDED_SERVER_STARTED.get(), httpClient);
	}

	@Test(dataProvider = "getData")
	public void requestPdpWithCustomMediaType(final String customDatatype, final String xacmlTestDirRelativePath) throws Exception
	{
		final File testDir = new File(RestServiceTest.XACML_SAMPLES_DIR, xacmlTestDirRelativePath);
		final Feature geometryDatatypeFeature = new Feature(customDatatype, FlatFileBasedDomainsDao.PdpFeatureType.DATATYPE.toString(), true);
		final List<Feature> pdpFeatures = Collections.singletonList(geometryDatatypeFeature);
		testDomainHelper.requestXacmlJsonPDP(testDir, pdpFeatures, !IS_EMBEDDED_SERVER_STARTED.get(), httpClient, java.util.Optional.of("application/geoxacml+json"));
	}


	/*
	 * deleteDomain() already tested in {@link DomainSetTest#deleteDomains()}, so this is just for cleaning after testing
	 */
	@AfterClass
	public void deleteDomain()
	{
		// this will throw NotFoundException if
		// syncToRemoveDomainFromAPIAfterDirectorydeleted() succeeded
		try
		{
			assertNotNull(testDomain.deleteDomain(), String.format("Error deleting domain ID=%s", testDomainId));
		}
		catch (final NotFoundException e)
		{
			// already removed
		}
	}



}
