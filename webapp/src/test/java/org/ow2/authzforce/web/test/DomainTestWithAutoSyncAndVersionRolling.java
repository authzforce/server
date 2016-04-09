/**
 * Copyright (C) 2015-2015 Thales Services SAS. All rights reserved. No warranty, explicit or implicit, provided.
 */
package org.ow2.authzforce.web.test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.ws.rs.NotFoundException;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;

import org.ow2.authzforce.pap.dao.flatfile.FlatFileBasedDomainsDAO;
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

import oasis.names.tc.xacml._3_0.core.schema.wd_17.IdReferenceType;
import oasis.names.tc.xacml._3_0.core.schema.wd_17.PolicySet;
import oasis.names.tc.xacml._3_0.core.schema.wd_17.Request;
import oasis.names.tc.xacml._3_0.core.schema.wd_17.Response;

/**
 * Tests specific to a domain with auto-sync and automatic removal of versions if too many
 */
public class DomainTestWithAutoSyncAndVersionRolling extends RestServiceTest
{

	private static final Logger LOGGER = LoggerFactory.getLogger(DomainTestWithAutoSyncAndVersionRolling.class);

	// TEST TIMEOUT MUST BE > 1 sec
	private static final int TEST_TIMEOUT_MS = FlatFileBasedDomainsDAO.SYNC_SERVICE_SHUTDOWN_TIMEOUT_SEC * 2000;

	private static final String TEST_POLICY_ID = "policyTooManyV";

	private DomainAPIHelper testDomainHelper = null;
	private DomainResource testDomain = null;
	private String testDomainId = null;
	private File testDomainDir;
	private int syncIntervalMs;

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
		testDomainDir = new File(DOMAINS_DIR, testDomainId);

		testDomainHelper = new DomainAPIHelper(testDomainId, testDomain, unmarshaller, pdpModelHandler);

		// first test enables version rolling
	}

	@AfterClass
	/**
	 * deleteDomain() already tested in {@link DomainSetTest#deleteDomains()}, so this is just for cleaning after testing
	 */
	public void deleteDomain()
	{
		// this will throw NotFoundException if syncToRemoveDomainFromAPIAfterDirectorydeleted() succeeded
		try
		{
			assertNotNull(testDomain.deleteDomain(), String.format("Error deleting domain ID=%s", testDomainId));
		} catch (NotFoundException e)
		{
			// already removed
		}
	}

	@Test
	public void enablePolicyVersionRolling()
	{
		final boolean isEnabled = testDomainHelper.setPolicyVersionRollingAndGetStatus(true);
		assertTrue(isEnabled, "Failed to enable policy version rolling");
	}

	@Test(dependsOnMethods = { "enablePolicyVersionRolling" })
	public void addTooManyPolicyVersions()
	{
		int maxVersionCountPerPolicy = 3;
		testDomainHelper.updateMaxPolicyVersionCount(maxVersionCountPerPolicy);

		for (int i = 0; i < maxVersionCountPerPolicy; i++)
		{
			PolicySet policySet = createDumbPolicySet(TEST_POLICY_ID, "1." + i);
			testDomain.getPapResource().getPoliciesResource().addPolicy(policySet);
		}

		// verify that all versions are there
		List<Link> links = testDomain.getPapResource().getPoliciesResource().getPolicyResource(TEST_POLICY_ID).getPolicyVersions().getLinks();
		Set<Link> versionSet = new HashSet<>(links);
		assertEquals(links.size(), versionSet.size(), "Duplicate versions returned in links from getPolicyResource(policyId): " + links);

		assertEquals(versionSet.size(), maxVersionCountPerPolicy, "versions removed before reaching value of property 'org.ow2.authzforce.domain.policy.maxVersionCount'. Actual versions: " + links);

		// set root policy to v1.1
		IdReferenceType newRootPolicyRef = new IdReferenceType(TEST_POLICY_ID, "1.1", null, null);
		testDomain.getPapResource().getPdpPropertiesResource().updateOtherPdpProperties(new PdpPropertiesUpdate(null, newRootPolicyRef));

		// add new version -> too many versions -> we expect that v1.0 is removed automatically
		String lastV = "1." + maxVersionCountPerPolicy;
		PolicySet policySet = createDumbPolicySet(TEST_POLICY_ID, lastV);
		testDomain.getPapResource().getPoliciesResource().addPolicy(policySet);
		List<Link> linksAfterMaxReached = testDomain.getPapResource().getPoliciesResource().getPolicyResource(TEST_POLICY_ID).getPolicyVersions().getLinks();
		final List<String> linkedVersions = new ArrayList<>();
		for (final Link link : linksAfterMaxReached)
		{
			linkedVersions.add(link.getHref());
		}

		assertEquals(linksAfterMaxReached.size(), maxVersionCountPerPolicy, "number of versions below or above value of property 'org.ow2.authzforce.domain.policy.maxVersionCount' (should be equal). Actual versions: " + linkedVersions);

		// first link expected to be the last in, since links are returned from last version to oldest
		assertEquals(linksAfterMaxReached.get(0).getHref(), lastV, "First link returned by getPolicyResource(policyId) does not correspond to the latest version. Actual versions: " + linkedVersions);

		// last link expected to be v1.1 instead of v1.0 (supposed to be removed)
		assertEquals(linksAfterMaxReached.get(linksAfterMaxReached.size() - 1).getHref(), "1.1", "Last link returned by getPolicyResource(policyId) does not correspond to the oldest version. Actual versions: " + linkedVersions);

		/*
		 * Let's do it again, but this time since the oldest (1.1) is still used as root policy, it cannot be removed. So 1.1 remains the oldes, but the next
		 * one - 1.2 is removed
		 */
		String newLastV = "1." + (maxVersionCountPerPolicy + 1);
		PolicySet newPolicySet = createDumbPolicySet(TEST_POLICY_ID, newLastV);
		testDomain.getPapResource().getPoliciesResource().addPolicy(newPolicySet);
		List<Link> linksAfterMaxReachedAgain = testDomain.getPapResource().getPoliciesResource().getPolicyResource(TEST_POLICY_ID).getPolicyVersions().getLinks();
		final List<String> linkedVersions2 = new ArrayList<>();
		for (final Link link : linksAfterMaxReachedAgain)
		{
			linkedVersions2.add(link.getHref());
		}

		assertEquals(linksAfterMaxReachedAgain.size(), maxVersionCountPerPolicy, "number of versions below or above value of property 'org.ow2.authzforce.domain.policy.maxVersionCount' (should be equal). Actual versions: " + linkedVersions2);

		// first link expected to be the last in, since links are returned from last version to oldest
		assertEquals(linksAfterMaxReachedAgain.get(0).getHref(), newLastV, "First link returned by getPolicyResource(policyId) does not correspond to the latest version. Actual versions: " + linkedVersions2);

		// last link expected to be v1.1 instead of v1.0 (supposed to be removed)
		assertEquals(linksAfterMaxReachedAgain.get(linksAfterMaxReachedAgain.size() - 1).getHref(), "1.1", "Last link returned by getPolicyResource(policyId) does not correspond to the oldest version. Actual versions: " + linkedVersions2);

		// v1.2 should have been removed, therefore the second-to-last must be 1.3
		assertEquals(linksAfterMaxReachedAgain.get(linksAfterMaxReachedAgain.size() - 2).getHref(), "1.3", "Second-to-last link returned by getPolicyResource(policyId) does not correspond not the second-to-oldest version. Actual versions: " + linkedVersions2);

	}

	@Parameters({ "remote.base.url", "legacy.fs" })
	@Test(timeOut = TEST_TIMEOUT_MS, description = "Check whether externalId-to-domain mapping updated automatically after any modification to domain's properties file")
	public void syncExternalIdAfterDomainPropertiesFileChanged(String remoteBaseUrl, @Optional("false") boolean isFilesystemLegacy) throws JAXBException, InterruptedException
	{
		// skip test i f server not started locally
		if (remoteBaseUrl != null)
		{
			return;
		}

		final String newExternalId = testDomainExternalId + "bis";
		testDomainHelper.modifyDomainPropertiesFile(newExternalId, isFilesystemLegacy);

		// wait for sync
		Thread.sleep(syncIntervalMs);

		// test the old externalId
		List<Link> domainLinks = null;
		boolean syncDone = false;
		while (!syncDone)
		{
			domainLinks = domainsAPIProxyClient.getDomains(testDomainExternalId).getLinks();
			syncDone = domainLinks != null && domainLinks.isEmpty();
			// if sync failed, the loop goes on until the timeout specified in testng.xml expires
		}

		testDomainExternalId = newExternalId;

		// test the new externalId
		// test externalId
		final List<Link> domainLinks2 = domainsAPIProxyClient.getDomains(newExternalId).getLinks();
		String matchedDomainId = domainLinks2.get(0).getHref();
		assertEquals(matchedDomainId, testDomainId, "Auto sync of externalId with domain properties file failed: getDomains(externalId = " + newExternalId + ") returned wrong domainId: " + matchedDomainId + " instead of " + testDomainId);
	}

	@Parameters({ "remote.base.url", "legacy.fs" })
	@Test(timeOut = TEST_TIMEOUT_MS, dependsOnMethods = { "syncExternalIdAfterDomainPropertiesFileChanged" })
	public void syncPdpAfterConfFileChanged(String remoteBaseUrl, @Optional("false") boolean isFilesystemLegacy) throws JAXBException, InterruptedException
	{
		// skip this if server not started locally (files not local)
		if (remoteBaseUrl != null)
		{
			return;
		}

		final IdReferenceType newRootPolicyRef = testDomainHelper.modifyRootPolicyRefInPdpConfFile(isFilesystemLegacy);

		// wait for sync
		Thread.sleep(syncIntervalMs);

		// check PDP returned policy identifier
		final Request xacmlReq = (Request) unmarshaller.unmarshal(new File(RestServiceTest.XACML_IIIG301_PDP_TEST_DIR, DomainSetTest.REQUEST_FILENAME));
		boolean isNewRootPolicyRefMatched = false;
		while (!isNewRootPolicyRefMatched)
		{
			final Response actualResponse = testDomain.getPdpResource().requestPolicyDecision(xacmlReq);
			for (JAXBElement<IdReferenceType> jaxbElt : actualResponse.getResults().get(0).getPolicyIdentifierList().getPolicyIdReferencesAndPolicySetIdReferences())
			{
				String tagLocalName = jaxbElt.getName().getLocalPart();
				if (tagLocalName.equals("PolicySetIdReference"))
				{
					assertEquals(jaxbElt.getValue(), newRootPolicyRef, "Auto sync with PDP configuration file failed: PolicySetIdReference returned by PDP does not match the root policyRef in PDP configuration file");
					isNewRootPolicyRefMatched = true;
					return;
				}
			}
		}
	}

	@Parameters({ "remote.base.url", "legacy.fs" })
	@Test(timeOut = TEST_TIMEOUT_MS * 2, dependsOnMethods = { "syncPdpAfterConfFileChanged" })
	public void syncPdpAfterUsedPolicyDirectoryChanged(String remoteBaseUrl, @Optional("false") boolean isFilesystemLegacy) throws JAXBException, InterruptedException
	{
		// skip this if server not started locally (files not local)
		if (remoteBaseUrl != null)
		{
			return;
		}

		final File inputRefPolicyFile = new File(RestServiceTest.XACML_POLICYREFS_PDP_TEST_DIR, RestServiceTest.TEST_REF_POLICIES_DIRECTORY_NAME + "/pps-employee.xml");
		final File inputRootPolicyFile = new File(RestServiceTest.XACML_POLICYREFS_PDP_TEST_DIR, RestServiceTest.TEST_DEFAULT_POLICYSET_FILENAME);
		final IdReferenceType newRefPolicySetRef = testDomainHelper.addRootPolicyWithRefAndUpdate(inputRootPolicyFile, inputRefPolicyFile, false, isFilesystemLegacy);

		// wait for sync
		Thread.sleep(syncIntervalMs);

		/*
		 * We cannot check again with /domain/{domainId}/pap/properties because it will sync again and this is not what we intend to test here.
		 */
		// Check PDP returned policy identifiers
		final JAXBElement<Request> jaxbXacmlReq = testDomainHelper.unmarshal(new File(RestServiceTest.XACML_POLICYREFS_PDP_TEST_DIR, "requestPolicyIdentifiers.xml"), Request.class);
		final Request xacmlReq = jaxbXacmlReq.getValue();

		boolean isNewRefPolicyRefMatched = false;
		while (!isNewRefPolicyRefMatched)
		{
			final Response actualResponse = testDomain.getPdpResource().requestPolicyDecision(xacmlReq);
			final List<JAXBElement<IdReferenceType>> jaxbPolicyRefs = actualResponse.getResults().get(0).getPolicyIdentifierList().getPolicyIdReferencesAndPolicySetIdReferences();
			final List<IdReferenceType> returnedPolicyIdentifiers = new ArrayList<>();
			for (JAXBElement<IdReferenceType> jaxbPolicyRef : jaxbPolicyRefs)
			{
				String tagLocalName = jaxbPolicyRef.getName().getLocalPart();
				if (tagLocalName.equals("PolicySetIdReference"))
				{
					final IdReferenceType idRef = jaxbPolicyRef.getValue();
					returnedPolicyIdentifiers.add(idRef);
					if (idRef.equals(newRefPolicySetRef))
					{
						isNewRefPolicyRefMatched = true;
					}
				}
			}
		}

		// Redo the same but adding new root policy version on disk this time
		final IdReferenceType newRootPolicySetRef = testDomainHelper.addRootPolicyWithRefAndUpdate(inputRootPolicyFile, inputRefPolicyFile, true, isFilesystemLegacy);

		// wait for sync
		Thread.sleep(syncIntervalMs);

		/*
		 * We cannot check again with /domain/{domainId}/pap/properties because it will sync again and this is not what we intend to test here.
		 */
		// Check PDP returned policy identifiers
		boolean isNewRootPolicyRefMatched = false;
		while (!isNewRootPolicyRefMatched)
		{
			final Response actualResponse2 = testDomain.getPdpResource().requestPolicyDecision(xacmlReq);
			final List<JAXBElement<IdReferenceType>> jaxbPolicyRefs2 = actualResponse2.getResults().get(0).getPolicyIdentifierList().getPolicyIdReferencesAndPolicySetIdReferences();
			final List<IdReferenceType> returnedPolicyIdentifiers = new ArrayList<>();
			for (JAXBElement<IdReferenceType> jaxbPolicyRef : jaxbPolicyRefs2)
			{
				String tagLocalName = jaxbPolicyRef.getName().getLocalPart();
				if (tagLocalName.equals("PolicySetIdReference"))
				{
					final IdReferenceType idRef = jaxbPolicyRef.getValue();
					returnedPolicyIdentifiers.add(idRef);
					if (idRef.equals(newRootPolicySetRef))
					{
						isNewRootPolicyRefMatched = true;
					}
				}
			}
		}
	}

	/**
	 * To be executed last since the domain is removed as a result if successful
	 * 
	 * @throws InterruptedException
	 * @throws IOException
	 * @throws IllegalArgumentException
	 * @throws JAXBException
	 */
	@Parameters({ "remote.base.url" })
	@Test(timeOut = TEST_TIMEOUT_MS, dependsOnMethods = { "addTooManyPolicyVersions", "syncPdpAfterUsedPolicyDirectoryChanged" })
	public void syncToRemoveDomainFromAPIAfterDirectorydeleted(String remoteBaseUrl) throws InterruptedException, IllegalArgumentException, IOException, JAXBException
	{
		// skip test if server not started locally
		if (remoteBaseUrl != null)
		{
			return;
		}

		// delete on disk
		FlatFileDAOUtils.deleteDirectory(testDomainDir.toPath(), 3);

		// wait for sync
		Thread.sleep(syncIntervalMs);

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
