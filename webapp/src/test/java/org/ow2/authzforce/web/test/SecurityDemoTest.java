/**
 * Copyright (C) 2015-2015 Thales Services SAS. All rights reserved. No warranty, explicit or implicit, provided.
 */
package org.ow2.authzforce.web.test;

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

import org.ow2.authzforce.rest.api.jaxrs.DomainResource;
import org.ow2.authzforce.rest.api.xmlns.DomainProperties;
import org.ow2.authzforce.rest.api.xmlns.PrpProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.ITestContext;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Optional;
import org.testng.annotations.Parameters;
import org.testng.annotations.Test;
import org.w3._2005.atom.Link;

import oasis.names.tc.xacml._3_0.core.schema.wd_17.PolicySet;

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

	private String testDomainExternalId = "test" + PRNG.nextInt(100);

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
	@Parameters({ "remote.base.url", "enableFastInfoset", "org.ow2.authzforce.domains.sync.interval" })
	@BeforeTest()
	public void beforeTest(@Optional String remoteAppBaseUrl, @Optional("false") boolean enableFastInfoset, @Optional("-1") int domainSyncIntervalSec, ITestContext testCtx) throws Exception
	{
		startServerAndInitCLient(remoteAppBaseUrl, enableFastInfoset, domainSyncIntervalSec, testCtx);
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
		} catch (NotFoundException e)
		{
			// already removed
		}
	}

	@Test(expectedExceptions = BadRequestException.class)
	public void addPolicyWithTooBigId()
	{
		char[] chars = new char[MAX_XML_ATTRIBUTE_SIZE + 1];
		Arrays.fill(chars, 'a');
		String policyId = new String(chars);
		PolicySet policySet = RestServiceTest.createDumbPolicySet(policyId, "1.0");
		testDomainHelper.testAddAndGetPolicy(policySet);
	}

	@Test(expectedExceptions = BadRequestException.class)
	public void addPolicyWithTooBigDescription()
	{
		char[] chars = new char[MAX_XML_TEXT_LENGTH + 1];
		Arrays.fill(chars, 'a');
		String description = new String(chars);
		PolicySet policySet = RestServiceTest.createDumbPolicySet("policyWithBigDescription", "1.0", description);
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
		final JAXBElement<PolicySet> badPolicySetJaxbObj = testDomainHelper.unmarshal(new File(RestServiceTest.XACML_SAMPLES_DIR, "policyWithTooManyChildElements.xml"), PolicySet.class);
		final PolicySet badPolicySet = badPolicySetJaxbObj.getValue();
		testDomainHelper.testAddAndGetPolicy(badPolicySet);
	}

	@Test(expectedExceptions = BadRequestException.class)
	public void addPolicyTooDeep() throws JAXBException
	{
		final JAXBElement<PolicySet> jaxbObj = testDomainHelper.unmarshal(new File(RestServiceTest.XACML_SAMPLES_DIR, "policyTooDeep.xml"), PolicySet.class);
		final PolicySet badPolicySet = jaxbObj.getValue();
		testDomainHelper.testAddAndGetPolicy(badPolicySet);
	}

	@Test(expectedExceptions = BadRequestException.class)
	public void setRootPolicyWithCircularPolicyRef() throws JAXBException
	{
		/*
		 * Before putting the empty refPolicySets (to make a policySets with references wrong), first put good root policySet without policy references (in case
		 * the current root PolicySet has references, uploading the empty refPolicySets before will be rejected because it makes the policySet with references
		 * invalid)
		 */
		testDomainHelper.resetPdpAndPrp();

		// add refPolicies
		final JAXBElement<PolicySet> refPolicySet = testDomainHelper.unmarshal(new File(RestServiceTest.XACML_SAMPLES_DIR, "PolicyReference.Circular/refPolicies/invalid-pps-employee.xml"), PolicySet.class);
		testDomainHelper.testAddAndGetPolicy(refPolicySet.getValue());
		final JAXBElement<PolicySet> refPolicySet2 = testDomainHelper.unmarshal(new File(RestServiceTest.XACML_SAMPLES_DIR, "PolicyReference.Circular/refPolicies/pps-manager.xml"), PolicySet.class);
		testDomainHelper.testAddAndGetPolicy(refPolicySet2.getValue());

		// Then attempt to put bad root policy set (referenced policysets in
		// Policy(Set)IdReferences do
		// not exist since refPolicySets empty)
		final JAXBElement<PolicySet> policySetWithRefs = testDomainHelper.unmarshal(new File(RestServiceTest.XACML_SAMPLES_DIR, "PolicyReference.Circular/policy.xml"), PolicySet.class);
		testDomainHelper.setRootPolicy(policySetWithRefs.getValue(), true);
	}

	@Test(expectedExceptions = BadRequestException.class)
	public void setRootPolicyWithTooDeepPolicyRef() throws JAXBException
	{
		/*
		 * Before putting the empty refPolicySets (to make a policySets with references wrong), first put good root policySet without policy references (in case
		 * the current root PolicySet has references, uploading the empty refPolicySets before will be rejected because it makes the policySet with references
		 * invalid)
		 */
		testDomainHelper.resetPdpAndPrp();

		// add refPolicies
		final JAXBElement<PolicySet> refPolicySet = testDomainHelper.unmarshal(new File(RestServiceTest.XACML_SAMPLES_DIR, "PolicyReference.TooDeep/refPolicies/invalid-pps-employee.xml"), PolicySet.class);
		testDomainHelper.testAddAndGetPolicy(refPolicySet.getValue());
		final JAXBElement<PolicySet> refPolicySet2 = testDomainHelper.unmarshal(new File(RestServiceTest.XACML_SAMPLES_DIR, "PolicyReference.TooDeep/refPolicies/pps-manager.xml"), PolicySet.class);
		testDomainHelper.testAddAndGetPolicy(refPolicySet2.getValue());

		// Then attempt to put bad root policy set (referenced policysets in
		// Policy(Set)IdReferences do
		// not exist since refPolicySets empty)
		final JAXBElement<PolicySet> policySetWithRefs = testDomainHelper.unmarshal(new File(RestServiceTest.XACML_SAMPLES_DIR, "PolicyReference.TooDeep/policy.xml"), PolicySet.class);
		testDomainHelper.setRootPolicy(policySetWithRefs.getValue(), true);
		System.out.println(TEST_CONSOLE_SEPARATOR);
	}

	@Parameters({ "org.ow2.authzforce.domain.maxPolicyCount" })
	@Test(expectedExceptions = ForbiddenException.class)
	public void addTooManyPolicies(int maxPolicyCountPerDomain) throws JAXBException
	{
		// replace all policies with one root policy and this is the only one
		// policy
		testDomainHelper.resetPdpAndPrp();

		testDomain.getPapResource().getPrpPropertiesResource().updateOtherPrpProperties(new PrpProperties(BigInteger.valueOf(maxPolicyCountPerDomain), BigInteger.valueOf(10), false));

		// So we can only add maxPolicyCountPerDomain-1 more policies before
		// reaching the max
		for (int i = 0; i < maxPolicyCountPerDomain - 1; i++)
		{
			PolicySet policySet = RestServiceTest.createDumbPolicySet("policyTooMany" + i, "1.0");
			testDomainHelper.testAddAndGetPolicy(policySet);
		}

		// verify that all policies are there
		List<Link> links = testDomain.getPapResource().getPoliciesResource().getPolicies().getLinks();
		Set<Link> policyLinkSet = new HashSet<>(links);
		assertEquals(links.size(), policyLinkSet.size(), "Duplicate policies returned in links from getPolicies: " + links);

		assertEquals(policyLinkSet.size(), maxPolicyCountPerDomain, "policies removed before reaching value of property 'org.ow2.authzforce.domain.maxPolicyCount'. Actual versions: " + links);

		// We should have reached the max, so adding one more should be rejected
		// by the server
		PolicySet policySet = RestServiceTest.createDumbPolicySet("policyTooMany" + maxPolicyCountPerDomain, "1.0");
		testDomainHelper.testAddAndGetPolicy(policySet);
		System.out.println(TEST_CONSOLE_SEPARATOR);
	}

	@Parameters({ "org.ow2.authzforce.domain.policy.maxVersionCount" })
	@Test(expectedExceptions = ForbiddenException.class)
	public void addTooManyPolicyVersions(int maxVersionCountPerPolicy) throws JAXBException
	{
		testDomainHelper.resetPdpAndPrp();

		testDomain.getPapResource().getPrpPropertiesResource().updateOtherPrpProperties(new PrpProperties(BigInteger.valueOf(10), BigInteger.valueOf(maxVersionCountPerPolicy), false));

		for (int i = 0; i < maxVersionCountPerPolicy; i++)
		{
			PolicySet policySet = RestServiceTest.createDumbPolicySet(TEST_POLICY_ID, "1." + i);
			testDomainHelper.testAddAndGetPolicy(policySet);
		}

		// verify that all versions are there
		List<Link> links = testDomain.getPapResource().getPoliciesResource().getPolicyResource(TEST_POLICY_ID).getPolicyVersions().getLinks();
		Set<Link> versionSet = new HashSet<>(links);
		assertEquals(links.size(), versionSet.size(), "Duplicate versions returned in links from getPolicyResource(policyId): " + links);

		assertEquals(versionSet.size(), maxVersionCountPerPolicy, "versions removed before reaching value of property 'org.ow2.authzforce.domain.policy.maxVersionCount'. Actual versions: " + links);

		PolicySet policySet = RestServiceTest.createDumbPolicySet(TEST_POLICY_ID, "1." + maxVersionCountPerPolicy);
		testDomainHelper.testAddAndGetPolicy(policySet);
		System.out.println(TEST_CONSOLE_SEPARATOR);
	}

}
