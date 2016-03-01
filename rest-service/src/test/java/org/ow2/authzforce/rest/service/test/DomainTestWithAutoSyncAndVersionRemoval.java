/**
 * Copyright (C) 2015-2015 Thales Services SAS. All rights reserved. No warranty, explicit or implicit, provided.
 */
package org.ow2.authzforce.rest.service.test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.ws.rs.NotFoundException;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.transform.stream.StreamSource;

import oasis.names.tc.xacml._3_0.core.schema.wd_17.IdReferenceType;
import oasis.names.tc.xacml._3_0.core.schema.wd_17.PolicySet;
import oasis.names.tc.xacml._3_0.core.schema.wd_17.Request;
import oasis.names.tc.xacml._3_0.core.schema.wd_17.Response;

import org.ow2.authzforce.core.xmlns.pdp.Pdp;
import org.ow2.authzforce.core.xmlns.pdp.StaticRefBasedRootPolicyProvider;
import org.ow2.authzforce.pap.dao.flatfile.FlatFileDAOUtils;
import org.ow2.authzforce.rest.api.jaxrs.DomainResource;
import org.ow2.authzforce.rest.api.xmlns.DomainProperties;
import org.ow2.authzforce.rest.api.xmlns.PdpPropertiesUpdate;
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

/**
 * Tests specific to a domain with auto-sync and automatic removal of versions if too many
 */
public class DomainTestWithAutoSyncAndVersionRemoval extends RestServiceTest
{

	private static final IllegalArgumentException MAX_VERSION_COUNT_PER_POLICY_ILLEGAL_ARG_EXCEPTION = new IllegalArgumentException(
			"Invalid value of parameter 'org.ow2.authzforce.domain.policy.maxVersionCount' for this test. Expected value: >= 3");

	private static final Logger LOGGER = LoggerFactory.getLogger(DomainTestWithAutoSyncAndVersionRemoval.class);

	private DomainResource testDomain = null;
	private String testDomainId = null;
	private File testDomainDir;
	private File testDomainPropertiesFile;
	private File testDomainPoliciesDirName;
	private File testDomainPDPConfFile;

	private String testDomainExternalId = "test" + PRNG.nextInt(100);

	@Parameters({ "app.base.url", "start.server", "org.ow2.authzforce.domain.maxPolicyCount",
			"org.ow2.authzforce.domain.policy.maxVersionCount",
			"org.ow2.authzforce.domain.policy.removeOldVersionsIfTooMany", "org.ow2.authzforce.domains.sync.interval" })
	@BeforeTest
	public void beforeTest(@Optional(DEFAULT_APP_BASE_URL) String appBaseUrl, @Optional("true") boolean startServer,
			int maxPolicyCountPerDomain, int maxVersionCountPerPolicy, boolean removeOldVersionsTooMany,
			int domainSyncIntervalSec, ITestContext testCtx) throws Exception
	{
		super.startServerAndInitCLient(appBaseUrl, startServer, maxPolicyCountPerDomain, maxVersionCountPerPolicy,
				removeOldVersionsTooMany, domainSyncIntervalSec, testCtx);
	}

	@Parameters("start.server")
	@BeforeClass
	public void addDomain(@Optional("true") boolean startServer, ITestContext testCtx) throws JAXBException,
			IOException
	{
		final Link domainLink = client.addDomain(new DomainProperties("Some description", testDomainExternalId));
		assertNotNull(domainLink, "Domain creation failure");

		// The link href gives the new domain ID
		testDomainId = domainLink.getHref();
		LOGGER.debug("Added domain ID={}", testDomainId);
		testDomain = client.getDomainResource(testDomainId);
		assertNotNull(testDomain, String.format("Error retrieving domain ID=%s", testDomainId));
		testDomainDir = new File(DOMAINS_DIR, testDomainId);
		testDomainPropertiesFile = new File(testDomainDir, DOMAIN_PROPERTIES_FILENAME);
		testDomainPoliciesDirName = new File(testDomainDir, DOMAIN_POLICIES_DIRNAME);
		testDomainPDPConfFile = new File(testDomainDir, DOMAIN_PDP_CONF_FILENAME);
	}

	@AfterClass
	/**
	 * deleteDomain() already tested in {@link DomainSetTest#deleteDomains()}, so this is just for cleaning after testing
	 */
	public void deleteDomain()
	{
		assertNotNull(testDomain.deleteDomain(), String.format("Error deleting domain ID=%s", testDomainId));
	}

	@AfterTest
	public void afterTest() throws Exception
	{
		super.destroyServer();
	}

	private static final int TEST_TIMEOUT_MS = 1000;

	private static final String TEST_POLICY_ID = "policyTooManyV";

	@Parameters({ "org.ow2.authzforce.domain.policy.maxVersionCount" })
	@Test
	public void addTooManyPolicyVersions(int maxVersionCountPerPolicy)
	{
		if (maxVersionCountPerPolicy < 3)
		{
			throw MAX_VERSION_COUNT_PER_POLICY_ILLEGAL_ARG_EXCEPTION;
		}

		for (int i = 0; i < maxVersionCountPerPolicy; i++)
		{
			PolicySet policySet = createDumbPolicySet(TEST_POLICY_ID, "1." + i);
			testDomain.getPapResource().getPoliciesResource().addPolicy(policySet);
		}

		// verify that all versions are there
		List<Link> links = testDomain.getPapResource().getPoliciesResource().getPolicyResource(TEST_POLICY_ID)
				.getPolicyVersions().getLinks();
		Set<Link> versionSet = new HashSet<>(links);
		assertEquals(links.size(), versionSet.size(),
				"Duplicate versions returned in links from getPolicyResource(policyId): " + links);

		assertEquals(
				versionSet.size(),
				maxVersionCountPerPolicy,
				"versions removed before reaching value of property 'org.ow2.authzforce.domain.policy.maxVersionCount'. Actual versions: "
						+ links);

		// set root policy to v1.1
		IdReferenceType newRootPolicyRef = new IdReferenceType(TEST_POLICY_ID, "1.1", null, null);
		testDomain.getPapResource().getPdpPropertiesResource()
				.updateOtherPdpProperties(new PdpPropertiesUpdate(newRootPolicyRef));

		// add new version -> too many versions -> we expect that v1.0 is removed automatically
		String lastV = "1." + maxVersionCountPerPolicy;
		PolicySet policySet = createDumbPolicySet(TEST_POLICY_ID, lastV);
		testDomain.getPapResource().getPoliciesResource().addPolicy(policySet);
		List<Link> linksAfterMaxReached = testDomain.getPapResource().getPoliciesResource()
				.getPolicyResource(TEST_POLICY_ID).getPolicyVersions().getLinks();
		assertEquals(
				linksAfterMaxReached.size(),
				maxVersionCountPerPolicy,
				"number of versions below or above value of property 'org.ow2.authzforce.domain.policy.maxVersionCount' (should be equal). Actual versions: "
						+ linksAfterMaxReached);

		// first link expected to be the last in, since links are returned from last version to oldest
		assertEquals(linksAfterMaxReached.get(0).getHref(), lastV,
				"First version returned by getPolicyResource(policyId) is not the last version. Actual versions: "
						+ linksAfterMaxReached);

		// last link expected to be v1.1 instead of v1.0 (supposed to be removed)
		assertEquals(linksAfterMaxReached.get(linksAfterMaxReached.size() - 1).getHref(), "1.1",
				"First version returned by getPolicyResource(policyId) is not the last version. Actual versions: "
						+ linksAfterMaxReached);

		/*
		 * Let's do it again, but this time since the oldest (1.1) is still used as root policy, it cannot be removed.
		 * So 1.1 remains the oldes, but the next one - 1.2 is removed
		 */
		String newLastV = "1." + (maxVersionCountPerPolicy + 1);
		PolicySet newPolicySet = createDumbPolicySet(TEST_POLICY_ID, newLastV);
		testDomain.getPapResource().getPoliciesResource().addPolicy(newPolicySet);
		List<Link> linksAfterMaxReachedAgain = testDomain.getPapResource().getPoliciesResource()
				.getPolicyResource(TEST_POLICY_ID).getPolicyVersions().getLinks();
		assertEquals(
				linksAfterMaxReachedAgain.size(),
				maxVersionCountPerPolicy,
				"number of versions below or above value of property 'org.ow2.authzforce.domain.policy.maxVersionCount' (should be equal). Actual versions: "
						+ linksAfterMaxReachedAgain);

		// first link expected to be the last in, since links are returned from last version to oldest
		assertEquals(linksAfterMaxReachedAgain.get(0).getHref(), newLastV,
				"First version returned by getPolicyResource(policyId) is not the last version. Actual versions: "
						+ linksAfterMaxReachedAgain);

		// last link expected to be v1.1 instead of v1.0 (supposed to be removed)
		assertEquals(linksAfterMaxReachedAgain.get(linksAfterMaxReachedAgain.size() - 1).getHref(), "1.1",
				"First version returned by getPolicyResource(policyId) is not the last version. Actual versions: "
						+ linksAfterMaxReachedAgain);

		// v1.2 should have been removed, therefore the second-to-last must be 1.3
		assertEquals(linksAfterMaxReachedAgain.get(linksAfterMaxReachedAgain.size() - 2).getHref(), "1.3",
				"First version returned by getPolicyResource(policyId) is not the last version. Actual versions: "
						+ linksAfterMaxReachedAgain);

	}

	@Parameters({ "start.server", "org.ow2.authzforce.domains.sync.interval" })
	@Test(description = "Check whether externalId-to-domain mapping updated automatically after any modification to domain's properties file", timeOut = TEST_TIMEOUT_MS)
	public void syncExternalIdAfterDomainPropertiesFileChanged(@Optional("true") boolean startServer,
			int syncIntervalSec) throws JAXBException, InterruptedException
	{
		// skip test i f server not started locally
		if (!startServer)
		{
			return;
		}

		// sync interval (seconds) must be lesser than 10 s as the timeout for the test is 10 s
		if (syncIntervalSec >= TEST_TIMEOUT_MS)
		{
			throw new IllegalArgumentException(
					"Invalid value of property 'org.ow2.authzforce.domains.sync.interval' (sec): " + syncIntervalSec
							+ ". Expected: < " + TEST_TIMEOUT_MS);
		}

		// test sync with properties file
		final DomainProperties props = testDomain.getDomainPropertiesResource().getDomainProperties();
		final String newExternalId = testDomainExternalId + "bis";
		final DomainProperties newProps = new DomainProperties(props.getDescription(), newExternalId);
		JAXB_CTX.createMarshaller().marshal(newProps, testDomainPropertiesFile);

		// wait for sync
		Thread.sleep(syncIntervalSec * 1000);

		// test the new externalId
		List<Link> domainLinks = null;
		boolean syncDone = false;
		while (!syncDone)
		{
			domainLinks = client.getDomains(newExternalId).getLinks();
			syncDone = domainLinks != null && !domainLinks.isEmpty();
		}

		if (domainLinks == null)
		{
			throw new RuntimeException("Invalid response from getDomains()");
		}

		String matchedDomainId = domainLinks.get(0).getHref();
		assertEquals(matchedDomainId, testDomainId,
				"Auto sync of externalId with domain properties file failed: getDomains(externalId = " + newExternalId
						+ ") returned wrong domainId: " + matchedDomainId + " instead of " + testDomainId);

		// test the old externalId
		final List<Link> domainLinks2 = client.getDomains(testDomainExternalId).getLinks();
		assertTrue(domainLinks2.isEmpty(),
				"Auto sync of externalId with domain properties file failed: old externaldId still mapped to the domain");

		testDomainExternalId = newExternalId;
	}

	@Parameters({ "start.server", "org.ow2.authzforce.domains.sync.interval" })
	@Test(timeOut = TEST_TIMEOUT_MS, dependsOnMethods = { "syncExternalIdAfterDomainPropertiesFileChanged" })
	public void syncPdpAfterConfFileChanged(@Optional("true") boolean startServer, int syncIntervalSec)
			throws JAXBException, InterruptedException
	{
		// skip this if server not started locally (files not local)
		if (!startServer)
		{
			return;
		}

		File testDir = new File(DomainSetTest.XACML_SAMPLES_DIR, "IIIG301");
		// we add some policy
		final PolicySet policy = (PolicySet) unmarshaller.unmarshal(new File(testDir,
				DomainSetTest.TEST_POLICY_FILENAME));
		IdReferenceType newRootPolicyRef = new IdReferenceType(policy.getPolicySetId(), policy.getVersion(), null, null);
		testDomain.getPapResource().getPoliciesResource().addPolicy(policy);

		// change root policyref in PDP conf file to reference the policy added previously
		Pdp pdpConf = pdpModelHandler.unmarshal(new StreamSource(testDomainPDPConfFile), Pdp.class);
		final StaticRefBasedRootPolicyProvider staticRefBasedRootPolicyProvider = (StaticRefBasedRootPolicyProvider) pdpConf
				.getRootPolicyProvider();
		staticRefBasedRootPolicyProvider.setPolicyRef(newRootPolicyRef);
		pdpModelHandler.marshal(pdpConf, testDomainPDPConfFile);

		// wait for sync
		Thread.sleep(syncIntervalSec * 1000);

		// check PDP returned policy identifier
		final Request xacmlReq = (Request) unmarshaller.unmarshal(new File(testDir, DomainSetTest.REQUEST_FILENAME));
		boolean syncDone = false;
		while (!syncDone)
		{
			final Response actualResponse = testDomain.getPdpResource().requestPolicyDecision(xacmlReq);
			for (JAXBElement<IdReferenceType> jaxbElt : actualResponse.getResults().get(0).getPolicyIdentifierList()
					.getPolicyIdReferencesAndPolicySetIdReferences())
			{
				String tagLocalName = jaxbElt.getName().getLocalPart();
				if (tagLocalName.equals("PolicySetIdReference"))
				{
					assertEquals(
							jaxbElt.getValue(),
							newRootPolicyRef,
							"Auto sync with PDP configuration file failed: PolicySetIdReference returned by PDP does not match the root policyRef in PDP configuration file");
					syncDone = true;
					return;
				}
			}
		}

		fail("Auto sync with APDP configuration file failed: PolicySetIdReference returned by PDP does not match the root policyRef in PDP configuration file");
	}

	@Parameters({ "start.server", "org.ow2.authzforce.domains.sync.interval" })
	@Test(timeOut = TEST_TIMEOUT_MS * 2, dependsOnMethods = { "syncPdpAfterConfFileChanged" })
	public void syncPdpAfterUsedPolicyDirectoryChanged(@Optional("true") boolean startServer, int syncIntervalSec)
			throws JAXBException, InterruptedException
	{
		// skip this if server not started locally (files not local)
		if (!startServer)
		{
			return;
		}

		final File testDir = new File(RestServiceTest.XACML_SAMPLES_DIR, "pdp/PolicyReference.Valid");

		final PolicySet refPolicySet = (PolicySet) unmarshaller.unmarshal(new File(testDir,
				"refPolicies/pps-employee.xml"));
		testDomain.getPapResource().getPoliciesResource().addPolicy(refPolicySet);

		// Set root policy referencing ref policy above
		final PolicySet policySetWithRef = (PolicySet) unmarshaller.unmarshal(new File(testDir, TEST_POLICY_FILENAME));
		// Add the policy and point the rootPolicyRef to new policy with refs to
		// instantiate it as root policy (validate, etc.)
		IdReferenceType rootPolicyRef = new IdReferenceType(policySetWithRef.getPolicySetId(),
				policySetWithRef.getVersion(), null, null);
		testDomain.getPapResource().getPdpPropertiesResource()
				.updateOtherPdpProperties(new PdpPropertiesUpdate(rootPolicyRef));

		// update referenced policy version on disk (we add ".1" to old version to have later version)
		PolicySet newRefPolicySet = new PolicySet(refPolicySet.getDescription(), refPolicySet.getPolicyIssuer(),
				refPolicySet.getPolicySetDefaults(), refPolicySet.getTarget(),
				refPolicySet.getPolicySetsAndPoliciesAndPolicySetIdReferences(),
				refPolicySet.getObligationExpressions(), refPolicySet.getAdviceExpressions(),
				refPolicySet.getPolicySetId(), refPolicySet.getVersion() + ".1",
				refPolicySet.getPolicyCombiningAlgId(), refPolicySet.getMaxDelegationDepth());
		Marshaller marshaller = JAXB_CTX.createMarshaller();
		File refPolicyDir = new File(testDomainPoliciesDirName, FlatFileDAOUtils.base64UrlEncode(newRefPolicySet
				.getPolicySetId()));
		File refPolicyFile = new File(refPolicyDir, newRefPolicySet.getVersion() + ".xml");
		marshaller.marshal(newRefPolicySet, refPolicyFile);

		// wait for sync
		Thread.sleep(syncIntervalSec * 1000);

		// check PDP returned policy identifier for a ref to the new version of the refPolicySet
		IdReferenceType newRefPolicySetRef = new IdReferenceType(newRefPolicySet.getPolicySetId(),
				newRefPolicySet.getVersion(), null, null);
		final Request xacmlReq = (Request) unmarshaller.unmarshal(new File(testDir, REQUEST_FILENAME));
		boolean syncDone = false;
		while (!syncDone)
		{
			final Response actualResponse = testDomain.getPdpResource().requestPolicyDecision(xacmlReq);
			for (JAXBElement<IdReferenceType> jaxbElt : actualResponse.getResults().get(0).getPolicyIdentifierList()
					.getPolicyIdReferencesAndPolicySetIdReferences())
			{
				String tagLocalName = jaxbElt.getName().getLocalPart();
				if (tagLocalName.equals("PolicySetIdReference"))
				{
					syncDone = jaxbElt.getValue().equals(newRefPolicySetRef);
					break;
				}
			}

			// this test succeeds if syncDone, i.e. the while loop ends before the test timeout (see testng parameter)
		}

		// Redo the same but updating the root policy version on disk this time
		PolicySet newRootPolicySet = new PolicySet(policySetWithRef.getDescription(),
				policySetWithRef.getPolicyIssuer(), policySetWithRef.getPolicySetDefaults(),
				policySetWithRef.getTarget(), policySetWithRef.getPolicySetsAndPoliciesAndPolicySetIdReferences(),
				policySetWithRef.getObligationExpressions(), policySetWithRef.getAdviceExpressions(),
				policySetWithRef.getPolicySetId(), policySetWithRef.getVersion() + ".1",
				policySetWithRef.getPolicyCombiningAlgId(), policySetWithRef.getMaxDelegationDepth());
		File rootPolicyDir = new File(testDomainPoliciesDirName, FlatFileDAOUtils.base64UrlEncode(newRootPolicySet
				.getPolicySetId()));
		File rootPolicyFile = new File(rootPolicyDir, newRootPolicySet.getVersion() + ".xml");
		marshaller.marshal(newRootPolicySet, rootPolicyFile);

		// wait for sync
		Thread.sleep(syncIntervalSec * 1000);

		// check PDP returned policy identifier for a ref to the new version of the root policySet
		IdReferenceType newRootPolicySetRef = new IdReferenceType(newRootPolicySet.getPolicySetId(),
				newRootPolicySet.getVersion(), null, null);
		syncDone = false;
		while (!syncDone)
		{
			final Response actualResponse = testDomain.getPdpResource().requestPolicyDecision(xacmlReq);
			for (JAXBElement<IdReferenceType> jaxbElt : actualResponse.getResults().get(0).getPolicyIdentifierList()
					.getPolicyIdReferencesAndPolicySetIdReferences())
			{
				String tagLocalName = jaxbElt.getName().getLocalPart();
				if (tagLocalName.equals("PolicySetIdReference"))
				{
					syncDone = jaxbElt.getValue().equals(newRootPolicySetRef);
					break;
				}
			}

			// this test succeeds if syncDone, i.e. the while loop ends before the test timeout (see testng parameter)
		}
	}

	/**
	 * To be executed last since the domain is removed as a result if successful
	 * 
	 * @param startServer
	 * 
	 * @param syncIntervalSec
	 * @throws InterruptedException
	 * @throws IOException
	 * @throws IllegalArgumentException
	 * @throws JAXBException
	 */
	@Parameters({ "start.server", "org.ow2.authzforce.domains.sync.interval" })
	@Test(timeOut = TEST_TIMEOUT_MS, dependsOnMethods = { "addTooManyPolicyVersions",
			"syncPdpAfterUsedPolicyDirectoryChanged" })
	public void syncToRemoveDomainFromAPIAfterDirectorydeleted(@Optional("true") boolean startServer,
			int syncIntervalSec) throws InterruptedException, IllegalArgumentException, IOException, JAXBException
	{
		// skip test if server not started locally
		if (!startServer)
		{
			return;
		}

		// delete on disk
		FlatFileDAOUtils.deleteDirectory(testDomainDir.toPath(), 3);

		// wait for sync
		Thread.sleep(syncIntervalSec * 1000);

		// check whether domain's PDP reachable
		File testDir = new File(DomainSetTest.XACML_SAMPLES_DIR, "IIIG301");
		final Request xacmlReq = (Request) unmarshaller.unmarshal(new File(testDir, REQUEST_FILENAME));
		boolean syncDone = false;
		while (!syncDone)
		{
			try
			{
				testDomain.getPdpResource().requestPolicyDecision(xacmlReq);
			} catch (NotFoundException nfe)
			{
				// OK
				syncDone = true;
				break;
			}
		}

	}

}
