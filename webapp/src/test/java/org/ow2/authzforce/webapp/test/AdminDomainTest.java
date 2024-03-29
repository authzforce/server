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

import jakarta.xml.bind.JAXBElement;
import jakarta.xml.bind.JAXBException;
import oasis.names.tc.xacml._3_0.core.schema.wd_17.PolicySet;
import org.ow2.authzforce.rest.api.jaxrs.DomainResource;
import org.ow2.authzforce.rest.api.xmlns.DomainProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.*;
import org.w3._2005.atom.Link;

import java.io.File;
import java.io.FileFilter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.Objects;

import static org.testng.Assert.assertNotNull;

/**
 * Tests specific to the special 'superadmin' domain for managing and enforcing the AuthzForce REST API access policy (controls access to all the REST API itself including all domains). For example,
 * the admin policy may have a rule/policy such that only the Domain_Admin of domain X (contextualized role) is allowed to do all actions on path '/domains/X' or '/domains/X/*' except DELETE
 * /domains/X. Another rule for the superadmin which may do all actions on any path including all domains.
 */
public class AdminDomainTest extends RestServiceTest
{
	private static final Logger LOGGER = LoggerFactory.getLogger(AdminDomainTest.class);

	private static final FileFilter DIRECTORY_FILTER = File::isDirectory;

	private DomainAPIHelper testDomainHelper = null;

	private boolean enableFastInfoset = false;

	/**
	 * @param remoteAppBaseUrl
	 * @param enableFastInfoset
	 * @throws Exception
	 * 
	 *             NB: use Boolean class instead of boolean primitive type for Testng parameter, else the default value in @Optional annotation is not handled properly.
	 */
	@Parameters({ "remote.base.url", "enableFastInfoset" })
	@BeforeClass
	public void addDomain(@Optional final String remoteAppBaseUrl, @Optional("false") final Boolean enableFastInfoset) throws Exception
	{
		this.enableFastInfoset = enableFastInfoset;
		String testDomainExternalId = "admin";
		final Link domainLink = domainsAPIProxyClient.addDomain(new DomainProperties("Superadmin domain", testDomainExternalId));
		assertNotNull(domainLink, "Domain creation failure");

		// The link href gives the new domain ID
		String testDomainId = domainLink.getHref();
		LOGGER.debug("Added domain ID={}", testDomainId);
		DomainResource testDomain = domainsAPIProxyClient.getDomainResource(testDomainId);
		assertNotNull(testDomain, String.format("Error retrieving (admin) domain ID=%s", testDomainId));
		this.testDomainHelper = new DomainAPIHelper(testDomainId, testDomain, unmarshaller, pdpModelHandler);

		assertNotNull(testDomain, String.format("Error retrieving domain ID=%s", testDomainId));
	}

	/**
	 * Test parameters from testng.xml are ignored when executing with maven surefire plugin, so we use default values for all.
	 *
	 * WARNING: the BeforeTest-annotated method must be in the test class, not in a super class although the same method logic is used in other test class
	 *
	 * @param remoteAppBaseUrl
	 * @param enableFastInfoset
	 * @param domainSyncIntervalSec
	 * @param enablePdpOnly
	 * @throws Exception
	 */
	@Parameters({ "remote.base.url", "enableFastInfoset", "enableDoSMitigation", "org.ow2.authzforce.domains.sync.interval", "enablePdpOnly" })
	@BeforeTest()
	public void beforeTest(@Optional final String remoteAppBaseUrl, @Optional("false") final boolean enableFastInfoset, @Optional("true") final boolean enableDoSMitigation,
						   @Optional("-1") final int domainSyncIntervalSec, @Optional("false") final boolean enablePdpOnly) throws Exception
	{
		startServerAndInitCLient(remoteAppBaseUrl, enableFastInfoset ? ClientType.FAST_INFOSET : ClientType.XML, "", enableDoSMitigation, domainSyncIntervalSec, enablePdpOnly);
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
		shutdownServer();
	}

	@Test
	public void setRootPolicy() throws JAXBException
	{
		final JAXBElement<PolicySet> jaxbElement = testDomainHelper.unmarshalXacml(new File(RestServiceTest.XACML_SAMPLES_DIR, "admin/policy.xml"), PolicySet.class);
		final PolicySet rootPolicySet = jaxbElement.getValue();
		testDomainHelper.setRootPolicy(rootPolicySet, true);
	}

	/**
	 * Create PDP evaluation test data. Various PolicySets/Requests/Responses from conformance tests.
	 * 
	 * 
	 * @return iterator over test data
	 */
	@DataProvider(name = "adminPdpTestFiles")
	public static Iterator<Object[]> createData()
	{
		final Collection<Object[]> testParams = new ArrayList<>();
		/*
		 * Each sub-directory of the root directory is data for a specific test. So we configure a test for each directory
		 */
		final File testRootDir = new File(RestServiceTest.XACML_SAMPLES_DIR, "admin");
		for (final File subDir : Objects.requireNonNull(testRootDir.listFiles(DIRECTORY_FILTER)))
		{
			// specific test's resources directory location, used as parameter
			// to PdpTest(String)
			testParams.add(new Object[] { subDir });
		}

		return testParams.iterator();
	}

	@Test(dependsOnMethods = { "setRootPolicy" }, dataProvider = "adminPdpTestFiles")
	public void requestPDP(final File testDirectory) throws Exception
	{
		// disable all features (incl. MDP) of PDP
		testDomainHelper.requestXacmlXmlPDP(testDirectory, null, !IS_EMBEDDED_SERVER_STARTED.get(), java.util.Optional.empty(), enableFastInfoset);
	}
}
