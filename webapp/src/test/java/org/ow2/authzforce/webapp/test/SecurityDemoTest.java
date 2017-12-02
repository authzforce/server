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

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.ws.rs.BadRequestException;
import javax.ws.rs.ClientErrorException;
import javax.ws.rs.ForbiddenException;
import javax.ws.rs.NotFoundException;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;

import oasis.names.tc.xacml._3_0.core.schema.wd_17.PolicySet;

import org.ow2.authzforce.rest.api.jaxrs.DomainResource;
import org.ow2.authzforce.rest.api.xmlns.DomainProperties;
import org.ow2.authzforce.rest.api.xmlns.PrpProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Optional;
import org.testng.annotations.Parameters;
import org.testng.annotations.Test;
import org.w3._2005.atom.Link;

/**
 * Demo test
 */
public class SecurityDemoTest extends RestServiceTest
{

	private static final Logger LOGGER = LoggerFactory.getLogger(SecurityDemoTest.class);

	private static final String TEST_POLICY_ID = "policyTooManyV";

	private static final String TEST_CONSOLE_SEPARATOR = "*******************************************************************************";

	private DomainAPIHelper testDomainHelper = null;
	private DomainResource testDomain = null;
	private String testDomainId = null;

	private final String testDomainExternalId = "test" + PRNG.nextInt(100);

	/**
	 * Test parameters from testng.xml are ignored when executing with maven surefire plugin, so we use default values for all.
	 * 
	 * WARNING: the BeforeTest-annotated method must be in the test class, not in a super class although the same method logic is used in other test class
	 * 
	 * @param remoteAppBaseUrl
	 * @param enableFastInfoset
	 * @param domainSyncIntervalSec
	 * @param testCtx
	 * @throws Exception
	 */
	@Parameters({ "remote.base.url", "enableFastInfoset", "enableDoSMitigation", "org.ow2.authzforce.domains.sync.interval", "enablePdpOnly" })
	@BeforeTest()
	public void beforeTest(@Optional final String remoteAppBaseUrl, @Optional("false") final boolean enableFastInfoset, @Optional("true") final boolean enableDoSMitigation,
			@Optional("-1") final int domainSyncIntervalSec, @Optional("false") final boolean enablePdpOnly) throws Exception
	{
		startServerAndInitCLient(remoteAppBaseUrl, enableFastInfoset ? ClientType.FAST_INFOSET : ClientType.JSON, enableDoSMitigation, domainSyncIntervalSec, enablePdpOnly);
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

	@BeforeClass
	public void addDomain() throws JAXBException, IOException
	{
		final Link domainLink = domainsAPIProxyClient.addDomain(new DomainProperties("Some description", testDomainExternalId));
		assertNotNull(domainLink, "Domain creation failure");

		// The link href gives the new domain ID
		testDomainId = domainLink.getHref();
		LOGGER.debug("Added domain ID={}", testDomainId);
		testDomain = domainsAPIProxyClient.getDomainResource(testDomainId);
		assertNotNull(testDomain, String.format("Error retrieving domain ID=%s", testDomainId));

		testDomainHelper = new DomainAPIHelper(testDomainId, testDomain, unmarshaller, pdpModelHandler);
	}

	@AfterClass
	/**
	 * deleteDomain() already tested in {@link DomainSetTest#deleteDomains()}, so this is just for cleaning after testing
	 */
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

	@Test(expectedExceptions = BadRequestException.class)
	public void addPolicyWithTooBigId()
	{
		final char[] chars = new char[XML_MAX_ATTRIBUTE_SIZE_EFFECTIVE + 1];
		Arrays.fill(chars, 'a');
		final String policyId = new String(chars);
		final PolicySet policySet = RestServiceTest.createDumbXacmlPolicySet(policyId, "1.0");
		testDomainHelper.testAddAndGetPolicy(policySet);
	}

	@Test(expectedExceptions = BadRequestException.class)
	public void addPolicyWithTooBigDescription()
	{
		final char[] chars = new char[XML_MAX_TEXT_LENGTH + 1];
		Arrays.fill(chars, 'a');
		final String description = new String(chars);
		final PolicySet policySet = RestServiceTest.createDumbXacmlPolicySet("policyWithBigDescription", "1.0", description);
		testDomainHelper.testAddAndGetPolicy(policySet);
	}

	/**
	 * Server expected to reply with HTTP 413 Request Entity Too Large
	 * 
	 * @throws JAXBException
	 */
	@Test(expectedExceptions = ClientErrorException.class)
	public void addPolicyWithTooManyChildElements() throws JAXBException
	{
		final JAXBElement<PolicySet> badPolicySetJaxbObj = testDomainHelper.unmarshalXacml(new File(RestServiceTest.XACML_SAMPLES_DIR, "policyWithTooManyChildElements.xml"), PolicySet.class);
		final PolicySet badPolicySet = badPolicySetJaxbObj.getValue();
		testDomainHelper.testAddAndGetPolicy(badPolicySet);
	}

	@Test(expectedExceptions = BadRequestException.class)
	public void addPolicyTooDeep() throws JAXBException
	{
		final JAXBElement<PolicySet> jaxbObj = testDomainHelper.unmarshalXacml(new File(RestServiceTest.XACML_SAMPLES_DIR, "policyTooDeep.xml"), PolicySet.class);
		final PolicySet badPolicySet = jaxbObj.getValue();
		testDomainHelper.testAddAndGetPolicy(badPolicySet);
	}

	@Test(expectedExceptions = BadRequestException.class)
	public void setRootPolicyWithCircularPolicyRef() throws JAXBException
	{
		/*
		 * Before putting the empty refPolicySets (to make a policySets with references wrong), first put good root policySet without policy references (in case the current root PolicySet has
		 * references, uploading the empty refPolicySets before will be rejected because it makes the policySet with references invalid)
		 */
		testDomainHelper.resetPdpAndPrp();

		// add refPolicies
		final JAXBElement<PolicySet> refPolicySet = testDomainHelper.unmarshalXacml(new File(RestServiceTest.XACML_SAMPLES_DIR, "PolicyReference.Circular/refPolicies/invalid-pps-employee.xml"),
				PolicySet.class);
		testDomainHelper.testAddAndGetPolicy(refPolicySet.getValue());
		final JAXBElement<PolicySet> refPolicySet2 = testDomainHelper.unmarshalXacml(new File(RestServiceTest.XACML_SAMPLES_DIR, "PolicyReference.Circular/refPolicies/pps-manager.xml"),
				PolicySet.class);
		testDomainHelper.testAddAndGetPolicy(refPolicySet2.getValue());

		// Then attempt to put bad root policy set (referenced policysets in
		// Policy(Set)IdReferences do
		// not exist since refPolicySets empty)
		final JAXBElement<PolicySet> policySetWithRefs = testDomainHelper.unmarshalXacml(new File(RestServiceTest.XACML_SAMPLES_DIR, "PolicyReference.Circular/policy.xml"), PolicySet.class);
		testDomainHelper.setRootPolicy(policySetWithRefs.getValue(), true);
	}

	@Test(expectedExceptions = BadRequestException.class)
	public void setRootPolicyWithTooDeepPolicyRef() throws JAXBException
	{
		/*
		 * Before putting the empty refPolicySets (to make a policySets with references wrong), first put good root policySet without policy references (in case the current root PolicySet has
		 * references, uploading the empty refPolicySets before will be rejected because it makes the policySet with references invalid)
		 */
		testDomainHelper.resetPdpAndPrp();

		// add refPolicies
		final JAXBElement<PolicySet> refPolicySet = testDomainHelper.unmarshalXacml(new File(RestServiceTest.XACML_SAMPLES_DIR, "PolicyReference.TooDeep/refPolicies/invalid-pps-employee.xml"),
				PolicySet.class);
		testDomainHelper.testAddAndGetPolicy(refPolicySet.getValue());
		final JAXBElement<PolicySet> refPolicySet2 = testDomainHelper.unmarshalXacml(new File(RestServiceTest.XACML_SAMPLES_DIR, "PolicyReference.TooDeep/refPolicies/pps-manager.xml"),
				PolicySet.class);
		testDomainHelper.testAddAndGetPolicy(refPolicySet2.getValue());

		// Then attempt to put bad root policy set (referenced policysets in
		// Policy(Set)IdReferences do
		// not exist since refPolicySets empty)
		final JAXBElement<PolicySet> policySetWithRefs = testDomainHelper.unmarshalXacml(new File(RestServiceTest.XACML_SAMPLES_DIR, "PolicyReference.TooDeep/policy.xml"), PolicySet.class);
		testDomainHelper.setRootPolicy(policySetWithRefs.getValue(), true);
		System.out.println(TEST_CONSOLE_SEPARATOR);
	}

	@Parameters({ "org.ow2.authzforce.domain.maxPolicyCount" })
	@Test(expectedExceptions = ForbiddenException.class)
	public void addTooManyPolicies(final int maxPolicyCountPerDomain) throws JAXBException
	{
		// replace all policies with one root policy and this is the only one
		// policy
		testDomainHelper.resetPdpAndPrp();

		testDomain.getPapResource().getPrpPropertiesResource().updateOtherPrpProperties(new PrpProperties(BigInteger.valueOf(maxPolicyCountPerDomain), BigInteger.valueOf(10), false));

		// So we can only add maxPolicyCountPerDomain-1 more policies before
		// reaching the max
		for (int i = 0; i < maxPolicyCountPerDomain - 1; i++)
		{
			final PolicySet policySet = RestServiceTest.createDumbXacmlPolicySet("policyTooMany" + i, "1.0");
			testDomainHelper.testAddAndGetPolicy(policySet);
		}

		// verify that all policies are there
		final List<Link> links = testDomain.getPapResource().getPoliciesResource().getPolicies().getLinks();
		final Set<Link> policyLinkSet = new HashSet<>(links);
		assertEquals(links.size(), policyLinkSet.size(), "Duplicate policies returned in links from getPolicies: " + links);

		assertEquals(policyLinkSet.size(), maxPolicyCountPerDomain, "policies removed before reaching value of property 'org.ow2.authzforce.domain.maxPolicyCount'. Actual versions: " + links);

		// We should have reached the max, so adding one more should be rejected
		// by the server
		final PolicySet policySet = RestServiceTest.createDumbXacmlPolicySet("policyTooMany" + maxPolicyCountPerDomain, "1.0");
		testDomainHelper.testAddAndGetPolicy(policySet);
		System.out.println(TEST_CONSOLE_SEPARATOR);
	}

	@Parameters({ "org.ow2.authzforce.domain.policy.maxVersionCount" })
	@Test(expectedExceptions = ForbiddenException.class)
	public void addTooManyPolicyVersions(final int maxVersionCountPerPolicy) throws JAXBException
	{
		testDomainHelper.resetPdpAndPrp();

		testDomain.getPapResource().getPrpPropertiesResource().updateOtherPrpProperties(new PrpProperties(BigInteger.valueOf(10), BigInteger.valueOf(maxVersionCountPerPolicy), false));

		for (int i = 0; i < maxVersionCountPerPolicy; i++)
		{
			final PolicySet policySet = RestServiceTest.createDumbXacmlPolicySet(TEST_POLICY_ID, "1." + i);
			testDomainHelper.testAddAndGetPolicy(policySet);
		}

		// verify that all versions are there
		final List<Link> links = testDomain.getPapResource().getPoliciesResource().getPolicyResource(TEST_POLICY_ID).getPolicyVersions().getLinks();
		final Set<Link> versionSet = new HashSet<>(links);
		assertEquals(links.size(), versionSet.size(), "Duplicate versions returned in links from getPolicyResource(policyId): " + links);

		assertEquals(versionSet.size(), maxVersionCountPerPolicy, "versions removed before reaching value of property 'org.ow2.authzforce.domain.policy.maxVersionCount'. Actual versions: " + links);

		final PolicySet policySet = RestServiceTest.createDumbXacmlPolicySet(TEST_POLICY_ID, "1." + maxVersionCountPerPolicy);
		testDomainHelper.testAddAndGetPolicy(policySet);
		System.out.println(TEST_CONSOLE_SEPARATOR);
	}

}
