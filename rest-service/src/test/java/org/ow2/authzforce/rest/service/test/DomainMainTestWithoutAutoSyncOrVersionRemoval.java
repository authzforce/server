/**
 * Copyright (C) 2015-2015 Thales Services SAS. All rights reserved. No warranty, explicit or implicit, provided.
 */
package org.ow2.authzforce.rest.service.test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.Set;

import javax.ws.rs.BadRequestException;
import javax.ws.rs.ClientErrorException;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.core.Response.Status;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;

import oasis.names.tc.xacml._3_0.core.schema.wd_17.IdReferenceType;
import oasis.names.tc.xacml._3_0.core.schema.wd_17.PolicySet;
import oasis.names.tc.xacml._3_0.core.schema.wd_17.Request;
import oasis.names.tc.xacml._3_0.core.schema.wd_17.Response;

import org.ow2.authzforce.core.pdp.impl.PdpModelHandler;
import org.ow2.authzforce.core.test.utils.TestUtils;
import org.ow2.authzforce.core.xmlns.pdp.Pdp;
import org.ow2.authzforce.core.xmlns.pdp.StaticRefBasedRootPolicyProvider;
import org.ow2.authzforce.core.xmlns.test.TestAttributeProvider;
import org.ow2.authzforce.pap.dao.flatfile.FlatFileDAOUtils;
import org.ow2.authzforce.rest.api.jaxrs.AttributeProvidersResource;
import org.ow2.authzforce.rest.api.jaxrs.DomainPropertiesResource;
import org.ow2.authzforce.rest.api.jaxrs.DomainResource;
import org.ow2.authzforce.rest.api.jaxrs.DomainsResource;
import org.ow2.authzforce.rest.api.jaxrs.PdpPropertiesResource;
import org.ow2.authzforce.rest.api.jaxrs.PoliciesResource;
import org.ow2.authzforce.rest.api.jaxrs.PolicyResource;
import org.ow2.authzforce.rest.api.jaxrs.PolicyVersionResource;
import org.ow2.authzforce.rest.api.xmlns.AttributeProviders;
import org.ow2.authzforce.rest.api.xmlns.Domain;
import org.ow2.authzforce.rest.api.xmlns.DomainProperties;
import org.ow2.authzforce.rest.api.xmlns.PdpProperties;
import org.ow2.authzforce.rest.api.xmlns.PdpPropertiesUpdate;
import org.ow2.authzforce.rest.api.xmlns.ResourceContent;
import org.ow2.authzforce.rest.api.xmlns.Resources;
import org.ow2.authzforce.xmlns.pdp.ext.AbstractAttributeProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.ITestContext;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Optional;
import org.testng.annotations.Parameters;
import org.testng.annotations.Test;
import org.w3._2005.atom.Link;

/**
 * Main tests specific to a domain, requires {@link DomainSetTest} to be run first
 */
public class DomainMainTestWithoutAutoSyncOrVersionRemoval
{
	private static final String TEST_REF_POLICIES_DIRECTORY_NAME = "refPolicies";

	private static final String TEST_ATTRIBUTE_PROVIDER_FILENAME = "attributeProvider.xml";

	private static final Logger LOGGER = LoggerFactory.getLogger(DomainMainTestWithoutAutoSyncOrVersionRemoval.class);

	private static final Random PRNG = new Random();

	private static final String TEST_DEFAULT_POLICYSET_FILENAME = "policySet.xml";

	private static final FileFilter DIRECTORY_FILTER = new FileFilter()
	{

		@Override
		public boolean accept(File pathname)
		{
			return pathname.isDirectory();
		}

	};

	private Unmarshaller unmarshaller = null;

	private static final Path XACML_SAMPLES_DIR = Paths.get("src/test/resources/xacml.samples");
	static
	{
		if (!Files.exists(XACML_SAMPLES_DIR))
		{
			throw new RuntimeException("Test data directory '" + XACML_SAMPLES_DIR + "' does not exist");
		}
	}

	private PdpModelHandler pdpModelHandler = null;

	private DomainsResource client;

	private DomainResource testDomain = null;
	private String testDomainId = null;
	private File testDomainPropertiesFile;
	private File testDomainPoliciesDirName;
	private File testDomainPDPConfFile;

	private String testDomainExternalId = "test";

	@Parameters("start.server")
	@BeforeClass
	public void addDomain(@Optional("true") boolean startServer, ITestContext testCtx) throws JAXBException,
			IOException
	{
		if (startServer)
		{
			pdpModelHandler = (PdpModelHandler) testCtx
					.getAttribute(RestServiceTest.PDP_MODEL_HANDLER_TEST_CONTEXT_ATTRIBUTE_ID);
		}

		/*
		 * WARNING: if tests are to be multi-threaded, modify according to Thread-safety section of CXF JAX-RS client
		 * API documentation http://cxf .apache.org/docs/jax-rs-client-api.html#JAX-RSClientAPI-ThreadSafety
		 */
		client = (DomainsResource) testCtx.getAttribute(RestServiceTest.REST_CLIENT_TEST_CONTEXT_ATTRIBUTE_ID);
		final Link domainLink = client.addDomain(new DomainProperties("Some description", testDomainExternalId));
		assertNotNull(domainLink, "Domain creation failure");

		// The link href gives the new domain ID
		testDomainId = domainLink.getHref();
		LOGGER.debug("Added domain ID={}", testDomainId);
		testDomain = client.getDomainResource(testDomainId);
		assertNotNull(testDomain, String.format("Error retrieving domain ID=%s", testDomainId));
		File testDomainDir = new File(RestServiceTest.DOMAINS_DIR, testDomainId);
		testDomainPropertiesFile = new File(testDomainDir, RestServiceTest.DOMAIN_PROPERTIES_FILENAME);
		testDomainPoliciesDirName = new File(testDomainDir, RestServiceTest.DOMAIN_POLICIES_DIRNAME);
		testDomainPDPConfFile = new File(testDomainDir, RestServiceTest.DOMAIN_PDP_CONF_FILENAME);

		final Schema apiSchema = (Schema) testCtx
				.getAttribute(RestServiceTest.REST_CLIENT_API_SCHEMA_TEST_CONTEXT_ATTRIBUTE_ID);
		unmarshaller = RestServiceTest.JAXB_CTX.createUnmarshaller();
		unmarshaller.setSchema(apiSchema);
	}

	@AfterClass
	/**
	 * deleteDomain() already tested in {@link DomainSetTest#deleteDomains()}, so this is just for cleaning after testing
	 */
	public void deleteDomain()
	{
		assertNotNull(testDomain.deleteDomain(), String.format("Error deleting domain ID=%s", testDomainId));
	}

	@Test
	public void getDomain()
	{
		final Domain domainResourceInfo = testDomain.getDomain();
		assertNotNull(domainResourceInfo);

		final DomainProperties props = domainResourceInfo.getProperties();
		assertNotNull(props);
	}

	@Test
	public void getDomainProperties()
	{
		final DomainPropertiesResource propsResource = testDomain.getDomainPropertiesResource();
		assertNotNull(propsResource);

		final DomainProperties props = propsResource.getDomainProperties();
		assertNotNull(props);
	}

	@Test(dependsOnMethods = { "getDomainProperties" })
	public void updateDomainProperties()
	{
		final DomainPropertiesResource propsResource = testDomain.getDomainPropertiesResource();
		assertNotNull(propsResource);

		byte[] randomBytes = new byte[128];
		PRNG.nextBytes(randomBytes);
		String description = FlatFileDAOUtils.base64UrlEncode(randomBytes);
		byte[] randomBytes2 = new byte[16];
		PRNG.nextBytes(randomBytes2);
		// externalId must be a xs:NCName, therefore cannot start with a number
		String newExternalId = "external" + FlatFileDAOUtils.base64UrlEncode(randomBytes2);
		propsResource.updateDomainProperties(new DomainProperties(description, newExternalId));
		// verify result
		final DomainProperties newProps = testDomain.getDomainPropertiesResource().getDomainProperties();
		assertEquals(newProps.getDescription(), description);
		assertEquals(newProps.getExternalId(), newExternalId);

		// test old externalID -> should fail
		final List<Link> domainLinks = client.getDomains(testDomainExternalId).getLinks();
		assertTrue(domainLinks.isEmpty(), "Update of externalId on GET /domains/" + this.testDomainId
				+ "/properties failed: old externaldId still mapped to the domain");

		testDomainExternalId = newExternalId;

		// test the new externalId
		final List<Link> domainLinks2 = client.getDomains(newExternalId).getLinks();
		String matchedDomainId = domainLinks2.get(0).getHref();
		assertEquals(matchedDomainId, testDomainId, "Update of externalId on GET /domains/" + this.testDomainId
				+ "/properties failed: getDomains(externalId = " + newExternalId + ") returned wrong domainId: "
				+ matchedDomainId + " instead of " + testDomainId);
	}

	@Parameters({ "start.server" })
	@Test(dependsOnMethods = { "getDomainProperties" })
	public void getDomainPropertiesAfterFileModification(@Optional("true") boolean startServer) throws JAXBException
	{
		// skip test i f server not started locally
		if (!startServer)
		{
			return;
		}

		// test sync with properties file
		final DomainProperties props = testDomain.getDomainPropertiesResource().getDomainProperties();
		final String newExternalId = testDomainExternalId + "bis";
		final DomainProperties newProps = new DomainProperties(props.getDescription(), newExternalId);
		RestServiceTest.JAXB_CTX.createMarshaller().marshal(newProps, testDomainPropertiesFile);

		// manual sync with GET /domains/{id}/properties
		final DomainProperties newPropsFromAPI = testDomain.getDomainPropertiesResource().getDomainProperties();
		final String externalIdFromAPI = newPropsFromAPI.getExternalId();
		assertEquals(externalIdFromAPI, newExternalId, "Manual sync of externalId on GET /domains/" + this.testDomainId
				+ "/properties failed: externaldId returned (" + externalIdFromAPI
				+ ") does not match externalId in modified file: " + testDomainPropertiesFile);

		// test the old externalId
		final List<Link> domainLinks = client.getDomains(testDomainExternalId).getLinks();
		assertTrue(domainLinks.isEmpty(), "Manual sync of externalId on GET /domains/" + this.testDomainId
				+ "/properties failed: old externaldId still mapped to the domain");

		testDomainExternalId = newExternalId;

		// test the new externalId
		// test externalId
		final List<Link> domainLinks2 = client.getDomains(newExternalId).getLinks();
		String matchedDomainId = domainLinks2.get(0).getHref();
		assertEquals(matchedDomainId, testDomainId, "Manual sync of externalId on GET /domains/" + this.testDomainId
				+ "/properties failed: getDomains(externalId = " + newExternalId + ") returned wrong domainId: "
				+ matchedDomainId + " instead of " + testDomainId);
	}

	@Test(dependsOnMethods = { "getDomain" })
	public void getPap()
	{
		final ResourceContent papResource = testDomain.getPapResource().getPAP();
		assertNotNull(papResource);
	}

	@Test(dependsOnMethods = { "getPap" })
	public void getAttributeProviders()
	{
		final AttributeProviders attributeProviders = testDomain.getPapResource().getAttributeProvidersResource()
				.getAttributeProviderList();
		assertNotNull(attributeProviders);
	}

	@Test(dependsOnMethods = { "getAttributeProviders" })
	public void updateAttributeProviders() throws JAXBException
	{
		final AttributeProvidersResource attributeProvidersResource = testDomain.getPapResource()
				.getAttributeProvidersResource();
		JAXBElement<TestAttributeProvider> jaxbElt = (JAXBElement<TestAttributeProvider>) unmarshaller
				.unmarshal(new File(RestServiceTest.XACML_SAMPLES_DIR, "pdp/IIA002(PolicySet)/attributeProvider.xml"));
		TestAttributeProvider testAttrProvider = jaxbElt.getValue();
		AttributeProviders updateAttrProvidersResult = attributeProvidersResource
				.updateAttributeProviderList(new AttributeProviders(Collections
						.<AbstractAttributeProvider> singletonList(testAttrProvider)));
		assertNotNull(updateAttrProvidersResult);
		assertEquals(updateAttrProvidersResult.getAttributeProviders().size(), 1);
		AbstractAttributeProvider updateAttrProvidersItem = updateAttrProvidersResult.getAttributeProviders().get(0);
		if (updateAttrProvidersItem instanceof TestAttributeProvider)
		{
			assertEquals(((TestAttributeProvider) updateAttrProvidersItem).getAttributes(),
					testAttrProvider.getAttributes());
		} else
		{
			fail("AttributeProvider in result of updateAttributeProviderList(inputAttributeProviders) does not match the one in inputAttributeProviders: "
					+ updateAttrProvidersItem);
		}

		// check getAttributeProviders
		AttributeProviders getAttrProvidersResult = attributeProvidersResource.getAttributeProviderList();
		assertNotNull(getAttrProvidersResult);
		assertEquals(getAttrProvidersResult.getAttributeProviders().size(), 1);
		AbstractAttributeProvider getAttrProvidersItem = getAttrProvidersResult.getAttributeProviders().get(0);
		if (getAttrProvidersItem instanceof TestAttributeProvider)
		{
			assertEquals(((TestAttributeProvider) getAttrProvidersItem).getAttributes(),
					testAttrProvider.getAttributes());
		} else
		{
			fail("AttributeProvider in result of updateAttributeProviderList(inputAttributeProviders) does not match the one in inputAttributeProviders: "
					+ getAttrProvidersItem);
		}

	}

	@Test(dependsOnMethods = { "getPap" })
	public void getPolicies()
	{
		final Resources resources = testDomain.getPapResource().getPoliciesResource().getPolicies();
		assertNotNull(resources);
		assertTrue(resources.getLinks().size() > 0, "No resource for root policy found");
	}

	@Test(dependsOnMethods = { "getDomainProperties", "getPolicies" })
	public void getRootPolicy()
	{
		final IdReferenceType rootPolicyRef = testDomain.getPapResource().getPdpPropertiesResource()
				.getOtherPdpProperties().getRootPolicyRef();
		final List<Link> links = testDomain.getPapResource().getPoliciesResource()
				.getPolicyResource(rootPolicyRef.getValue()).getPolicyVersions().getLinks();
		assertTrue(links.size() > 0, "No root policy version found");
	}

	private static void matchPolicySets(PolicySet actual, PolicySet expected, String testedMethodId)
	{
		assertEquals(
				actual.getPolicySetId(),
				expected.getPolicySetId(),
				String.format("Actual PolicySetId (='%s') from %s() != expected PolicySetId (='%s')",
						actual.getPolicySetId(), testedMethodId, expected.getPolicySetId()));
		assertEquals(actual.getVersion(), expected.getVersion(), String.format(
				"Actual PolicySet Version (='%s') from %s() != expected PolicySet Version (='%s')",
				actual.getVersion(), testedMethodId, expected.getVersion()));
	}

	private static boolean isHrefMatched(String href, List<Link> links)
	{
		for (Link link : links)
		{
			if (link.getHref().equals(href))
			{
				return true;
			}
		}

		return false;
	}

	/**
	 * Adds a given PolicySet and returns allocated resource ID. It also checks 2 things: 1) The response's PolicySet
	 * matches the input PolicySet (simply checking PolicySet IDs and versions) 2) The reponse to a getPolicySet() also
	 * matches the input PolicySet, to make sure the new PolicySet was actually committed succesfully and no error
	 * occurred in the process. 3) Response to getPolicyResources() with same PolicySetId and Version matches.
	 * <p>
	 * Returns created policy resource path segment
	 */
	private String testAddAndGetPolicy(PolicySet policySet)
	{
		// put new policyset
		final Link link = testDomain.getPapResource().getPoliciesResource().addPolicy(policySet);
		final String policyHref = link.getHref();
		final String[] parts = policyHref.split("/");
		assertTrue(parts.length >= 2, "Link returned by addPolicy does not match pattern 'policyId/version'");
		final String policyResId = parts[0];
		final String versionResId = parts[1];

		// Check result was committed
		// check added policy link is in policies list
		PoliciesResource policiesRes = testDomain.getPapResource().getPoliciesResource();
		assertTrue(isHrefMatched(policyResId, policiesRes.getPolicies().getLinks()),
				"Added policy resource link not found in links returned by getPoliciesResource()");

		// check added policy version is in policy versions list
		PolicyResource policyRes = policiesRes.getPolicyResource(policyResId);
		final Resources policyVersionsResources = policyRes.getPolicyVersions();
		assertTrue(isHrefMatched(versionResId, policyVersionsResources.getLinks()),
				"Added policy version resource link not found in links returned by getPolicyVersions()");

		// check PolicySet of added policy id/version is actually the one we
		// added
		final PolicySet getRespPolicySet = policyRes.getPolicyVersionResource(versionResId).getPolicyVersion();
		matchPolicySets(getRespPolicySet, policySet, "getPolicy");

		return policyResId;
	}

	private static final String TEST_POLICY_ID1 = "testPolicyAdd";

	@Test(dependsOnMethods = { "getRootPolicy" })
	public void addAndGetPolicy()
	{
		PolicySet policySet = RestServiceTest.createDumbPolicySet(TEST_POLICY_ID1, "1.0");
		testAddAndGetPolicy(policySet);

		PolicySet policySet2 = RestServiceTest.createDumbPolicySet(TEST_POLICY_ID1, "1.1");
		testAddAndGetPolicy(policySet2);

		PolicySet policySet3 = RestServiceTest.createDumbPolicySet(TEST_POLICY_ID1, "1.2");
		testAddAndGetPolicy(policySet3);
		Resources policyVersionsResources = testDomain.getPapResource().getPoliciesResource()
				.getPolicyResource(TEST_POLICY_ID1).getPolicyVersions();
		assertEquals(policyVersionsResources.getLinks().size(), 3);

	}

	private static final String TEST_POLICY_ID2 = "policyToTestGetLatestV";

	@Test(dependsOnMethods = { "addAndGetPolicy" })
	public void getLatestPolicyVersion()
	{
		PolicySet policySet = RestServiceTest.createDumbPolicySet(TEST_POLICY_ID2, "1.0");
		testAddAndGetPolicy(policySet);

		PolicySet policySet2 = RestServiceTest.createDumbPolicySet(TEST_POLICY_ID2, "1.1");
		testAddAndGetPolicy(policySet2);

		PolicySet policySet3 = RestServiceTest.createDumbPolicySet(TEST_POLICY_ID2, "1.2");
		testAddAndGetPolicy(policySet3);

		PoliciesResource policiesRes = testDomain.getPapResource().getPoliciesResource();
		PolicyResource policyRes = policiesRes.getPolicyResource(TEST_POLICY_ID2);
		final PolicySet getRespPolicySet = policyRes.getPolicyVersionResource("latest").getPolicyVersion();
		matchPolicySets(getRespPolicySet, policySet3, "getLatestPolicyVersion");

	}

	@Parameters({ "org.ow2.authzforce.domain.maxPolicyCount" })
	@Test(dependsOnMethods = { "addAndGetPolicy" })
	public void addTooManyPolicies(int maxPolicyCountPerDomain)
	{
		for (int i = 0; i < maxPolicyCountPerDomain; i++)
		{
			PolicySet policySet = RestServiceTest.createDumbPolicySet("policyTooMany" + i, "1.0");
			testAddAndGetPolicy(policySet);
		}

		try
		{
			PolicySet policySet = RestServiceTest.createDumbPolicySet("policyTooMany", "1.0");
			testAddAndGetPolicy(policySet);
			fail("Failed to enforce maxPoliciesPerDomain property: " + maxPolicyCountPerDomain);
		} catch (BadRequestException e)
		{
			// OK
		}

	}

	private static final String TEST_POLICY_ID = "policyTooManyV";

	@Parameters({ "org.ow2.authzforce.domain.policy.maxVersionCount" })
	@Test(dependsOnMethods = { "addTooManyPolicies" })
	public void addTooManyPolicyVersions(int maxVersionCountPerPolicy)
	{
		for (int i = 0; i < maxVersionCountPerPolicy; i++)
		{
			PolicySet policySet = RestServiceTest.createDumbPolicySet(TEST_POLICY_ID, "1." + i);
			testAddAndGetPolicy(policySet);
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

		try
		{
			PolicySet policySet = RestServiceTest.createDumbPolicySet(TEST_POLICY_ID, "2.0");
			testAddAndGetPolicy(policySet);
			fail("Failed to enforce property 'org.ow2.authzforce.domain.policy.maxVersionCount': "
					+ maxVersionCountPerPolicy);
		} catch (BadRequestException e)
		{
			// OK
		}

	}

	@Test(dependsOnMethods = { "addAndGetPolicy" })
	public void addConflictingPolicyVersion()
	{
		PolicySet policySet = RestServiceTest.createDumbPolicySet("testAddConflictingPolicyVersion", "1.0");
		testAddAndGetPolicy(policySet);

		// must be rejected
		try
		{
			testAddAndGetPolicy(policySet);
			fail("Adding the same policy did not fail with HTTP 409 Conflict as expected");
		} catch (ClientErrorException e)
		{
			assertEquals(e.getResponse().getStatus(), Status.CONFLICT.getStatusCode());
		}
	}

	@Test(dependsOnMethods = { "addAndGetPolicy" })
	public void deleteAndTryGetUnusedPolicy()
	{
		PolicySet policySet1 = RestServiceTest.createDumbPolicySet("testPolicyDelete", "1.2.3");
		testAddAndGetPolicy(policySet1);

		PolicySet policySet2 = RestServiceTest.createDumbPolicySet("testPolicyDelete", "1.3.1");
		testAddAndGetPolicy(policySet2);

		// delete policy (all versions)
		PolicyResource policyResource = testDomain.getPapResource().getPoliciesResource()
				.getPolicyResource("testPolicyDelete");
		Resources versionsResources = policyResource.deletePolicy();
		assertNotNull(versionsResources);
		assertTrue(isHrefMatched("1.2.3", versionsResources.getLinks()));
		assertTrue(isHrefMatched("1.3.1", versionsResources.getLinks()));

		try
		{
			policyResource.getPolicyVersions();
			fail("Policy (all versions) removal failed (resource still there");
		} catch (NotFoundException e)
		{
			// OK
		}

		PoliciesResource policiesRes = testDomain.getPapResource().getPoliciesResource();
		assertFalse(isHrefMatched("testPolicyDelete", policiesRes.getPolicies().getLinks()),
				"Deleted policy resource (all versions) is still in links returned by getPoliciesResource()");

	}

	@Test(dependsOnMethods = { "deleteAndTryGetUnusedPolicy" })
	public void deleteAndTryGetPolicyVersion()
	{
		PolicySet policySet1 = RestServiceTest.createDumbPolicySet("testPolicyDelete", "1.2.3");
		testAddAndGetPolicy(policySet1);

		PolicySet policySet2 = RestServiceTest.createDumbPolicySet("testPolicyDelete", "1.3.1");
		testAddAndGetPolicy(policySet2);

		// delete one of the versions
		PolicyVersionResource policyVersionRes = testDomain.getPapResource().getPoliciesResource()
				.getPolicyResource("testPolicyDelete").getPolicyVersionResource("1.2.3");
		PolicySet deletedPolicy = policyVersionRes.deletePolicyVersion();
		matchPolicySets(deletedPolicy, policySet1, "deleteAndTryGetPolicy");

		try
		{
			policyVersionRes.getPolicyVersion();
			org.testng.Assert.fail("Policy version removal failed (resource still there");
		} catch (NotFoundException e)
		{
			// OK
		}

		Resources policyVersionsResources = testDomain.getPapResource().getPoliciesResource()
				.getPolicyResource("testPolicyDelete").getPolicyVersions();
		assertEquals(policyVersionsResources.getLinks().size(), 1);
		assertTrue(isHrefMatched("1.3.1", policyVersionsResources.getLinks()));

	}

	@Test(dependsOnMethods = { "getRootPolicy", "deleteAndTryGetUnusedPolicy" })
	public void deleteRootPolicy()
	{
		final IdReferenceType rootPolicyRef = testDomain.getPapResource().getPdpPropertiesResource()
				.getOtherPdpProperties().getRootPolicyRef();

		// must be rejected
		try
		{
			testDomain.getPapResource().getPoliciesResource().getPolicyResource(rootPolicyRef.getValue())
					.deletePolicy();
			fail("Wrongly accepted to remove root policy");
		} catch (BadRequestException e)
		{
			// OK, expected
		}

		assertNotNull(testDomain.getPapResource().getPoliciesResource().getPolicyResource(rootPolicyRef.getValue())
				.getPolicyVersions(),
				"Root policy was actually removed although server return HTTP 400 for removal attempt");

		// make sure rootPolicyRef unchanged
		assertEquals(rootPolicyRef, testDomain.getPapResource().getPdpPropertiesResource().getOtherPdpProperties()
				.getRootPolicyRef(), "rootPolicyRef changed although root policy removal rejected");
	}

	private IdReferenceType setRootPolicy(String policyId, String version)
	{
		PdpPropertiesResource propsRes = testDomain.getPapResource().getPdpPropertiesResource();
		IdReferenceType newRootPolicyRef = new IdReferenceType(policyId, version, null, null);
		propsRes.updateOtherPdpProperties(new PdpPropertiesUpdate(newRootPolicyRef));
		return newRootPolicyRef;
	}

	private IdReferenceType setRootPolicy(PolicySet policySet)
	{
		testAddAndGetPolicy(policySet);

		return setRootPolicy(policySet.getPolicySetId(), policySet.getVersion());
	}

	@Test(dependsOnMethods = { "addAndGetPolicy", "deleteRootPolicy", "updateDomainProperties" })
	public void updateRootPolicyRefToValidPolicy() throws JAXBException
	{
		PdpPropertiesResource propsRes = testDomain.getPapResource().getPdpPropertiesResource();

		// get current root policy ID
		final IdReferenceType rootPolicyRef = propsRes.getOtherPdpProperties().getRootPolicyRef();
		PolicyResource oldRootPolicyRes = testDomain.getPapResource().getPoliciesResource()
				.getPolicyResource(rootPolicyRef.getValue());

		// point rootPolicyRef to another one
		String oldRootPolicyId = rootPolicyRef.getValue();
		String newRootPolicyId = oldRootPolicyId + ".new";
		PolicySet policySet = RestServiceTest.createDumbPolicySet(newRootPolicyId, "1.0");
		IdReferenceType newRootPolicyRef = setRootPolicy(policySet);

		// verify update
		final PdpProperties newProps = testDomain.getPapResource().getPdpPropertiesResource().getOtherPdpProperties();
		assertEquals(
				newProps.getRootPolicyRef(),
				newRootPolicyRef,
				"Root PolicyRef returned by getOtherPdpProperties() does not match last set rootPolicyRef via updateOtherPdpProperties()");
		assertEquals(
				newProps.getEnabledPolicies().get(0),
				newRootPolicyRef,
				"First enabled policy returned by getOtherPdpProperties() does not match last set rootPolicyRef via updateOtherPdpProperties()");

		// delete previous root policy ref to see if it succeeds
		oldRootPolicyRes.deletePolicy();
	}

	@Test(dependsOnMethods = { "updateRootPolicyRefToValidPolicy" })
	public void updateRootPolicyRefToMissingPolicy() throws JAXBException
	{
		PdpPropertiesResource propsRes = testDomain.getPapResource().getPdpPropertiesResource();
		PdpProperties props = propsRes.getOtherPdpProperties();

		// get current root policy ID
		final IdReferenceType rootPolicyRef = props.getRootPolicyRef();

		// point rootPolicyRef to another one
		String oldRootPolicyId = rootPolicyRef.getValue();
		String newRootPolicyId = oldRootPolicyId + ".new";

		IdReferenceType newRootPolicyRef = new IdReferenceType(newRootPolicyId, null, null, null);

		// MUST fail
		try
		{
			propsRes.updateOtherPdpProperties(new PdpPropertiesUpdate(newRootPolicyRef));
			fail("Setting rootPolicyRef to missing policy did not fail as expected");
		} catch (BadRequestException e)
		{
			// OK
		}

		// make sure rootPolicyRef unchanged
		assertEquals(rootPolicyRef, testDomain.getPapResource().getPdpPropertiesResource().getOtherPdpProperties()
				.getRootPolicyRef());
	}

	@Test(dependsOnMethods = { "updateRootPolicyRefToValidPolicy" })
	public void updateRootPolicyRefToValidVersion() throws JAXBException
	{
		PdpPropertiesResource propsRes = testDomain.getPapResource().getPdpPropertiesResource();
		PdpProperties props = propsRes.getOtherPdpProperties();

		PolicySet policySet = RestServiceTest.createDumbPolicySet("testUpdateRootPolicyRefToValidVersion", "4.4");
		testAddAndGetPolicy(policySet);

		PolicySet policySet2 = RestServiceTest.createDumbPolicySet("testUpdateRootPolicyRefToValidVersion", "4.5");
		testAddAndGetPolicy(policySet2);

		PolicySet policySet3 = RestServiceTest.createDumbPolicySet("testUpdateRootPolicyRefToValidVersion", "4.6");
		testAddAndGetPolicy(policySet3);

		IdReferenceType newRootPolicyRef = new IdReferenceType("testUpdateRootPolicyRefToValidVersion", "4.5", null,
				null);
		propsRes.updateOtherPdpProperties(new PdpPropertiesUpdate(newRootPolicyRef));

		// verify update
		final PdpProperties newProps = testDomain.getPapResource().getPdpPropertiesResource().getOtherPdpProperties();
		assertEquals(newProps.getRootPolicyRef(), newRootPolicyRef);

		// delete other policy versions (must succeed) to check that the root
		// policyRef is not pointing to one of them by mistake
		PolicyResource policyRes = testDomain.getPapResource().getPoliciesResource()
				.getPolicyResource("testUpdateRootPolicyRefToValidVersion");
		policyRes.getPolicyVersionResource("4.4").deletePolicyVersion();
		policyRes.getPolicyVersionResource("4.6").deletePolicyVersion();
	}

	@Test(dependsOnMethods = { "updateRootPolicyRefToValidVersion" })
	public void updateRootPolicyRefToMissingVersion() throws JAXBException
	{
		PdpPropertiesResource propsRes = testDomain.getPapResource().getPdpPropertiesResource();
		PdpProperties props = propsRes.getOtherPdpProperties();

		// get current root policy ID
		final IdReferenceType rootPolicyRef = props.getRootPolicyRef();

		// point rootPolicyRef to same policy id but different version
		String rootPolicyId = rootPolicyRef.getValue();
		IdReferenceType newRootPolicyRef = new IdReferenceType(rootPolicyId, "0.0.0.1", null, null);
		// MUST FAIL
		try
		{
			propsRes.updateOtherPdpProperties(new PdpPropertiesUpdate(newRootPolicyRef));
			fail("Setting rootPolicyRef to missing policy version did not fail as expected.");
		} catch (BadRequestException e)
		{
			// OK
		}

		// make sure rootPolicyRef unchanged
		assertEquals(rootPolicyRef, testDomain.getPapResource().getPdpPropertiesResource().getOtherPdpProperties()
				.getRootPolicyRef());
	}

	private void resetPolicies() throws JAXBException
	{
		final PolicySet rootPolicySet = (PolicySet) unmarshaller.unmarshal(new File(RestServiceTest.XACML_SAMPLES_DIR,
				TEST_DEFAULT_POLICYSET_FILENAME));
		final PoliciesResource policiesRes = testDomain.getPapResource().getPoliciesResource();

		// If already exists, remove to start from scratch
		PolicyResource rootPolicyRes = policiesRes.getPolicyResource(rootPolicySet.getPolicySetId());
		try
		{
			rootPolicyRes.deletePolicy();
			setRootPolicy(rootPolicySet);
		} catch (NotFoundException e)
		{
			// not exists on the server, so add and set as root
			// start from scratch with one policy version
			setRootPolicy(rootPolicySet);
		} catch (BadRequestException e)
		{
			// already root (removal rejected), so nothing to do
		}

		// Delete all other policies
		Resources policiesResources = policiesRes.getPolicies();
		for (Link link : policiesResources.getLinks())
		{
			String policyId = link.getHref();
			// skip if this is the root policy
			if (policyId.equals(rootPolicySet.getPolicySetId()))
			{
				continue;
			}

			policiesRes.getPolicyResource(policyId).deletePolicy();
		}
	}

	@Test(dependsOnMethods = { "updateRootPolicyRefToValidPolicy" })
	public void setRootPolicyWithGoodRefs() throws JAXBException
	{
		/*
		 * Before putting the empty refPolicySets (to make a policySets with references wrong), first put good root
		 * policySet without policy references (in case the current root PolicySet has references, uploading the empty
		 * refPolicySets before will be rejected because it makes the policySet with references invalid)
		 */
		resetPolicies();

		final PolicySet refPolicySet = (PolicySet) unmarshaller.unmarshal(new File(RestServiceTest.XACML_SAMPLES_DIR,
				"pdp/PolicyReference.Valid/refPolicies/pps-employee.xml"));
		String refPolicyResId = testAddAndGetPolicy(refPolicySet);

		// Set root policy referencing ref policy above
		final PolicySet policySetWithRef = (PolicySet) unmarshaller.unmarshal(new File(
				RestServiceTest.XACML_SAMPLES_DIR, "pdp/PolicyReference.Valid/policy.xml"));
		// Add the policy and point the rootPolicyRef to new policy with refs to
		// instantiate it as root policy (validate, etc.)
		setRootPolicy(policySetWithRef);

		// Delete referenced policy -> must fail (because root policy is still
		// referencing one of them)
		final PolicyResource refPolicyResource = testDomain.getPapResource().getPoliciesResource()
				.getPolicyResource(refPolicyResId);
		try
		{
			refPolicyResource.deletePolicy();
			fail("Policy used/referenced by root policy was deleted, making root policy invalid");
		} catch (BadRequestException e)
		{
			// Bad request as expected
		}

		/*
		 * Check that referenced policy set was not actually deleted, is still there
		 */
		final PolicySet getPolicyVersion = refPolicyResource.getPolicyVersionResource(refPolicySet.getVersion())
				.getPolicyVersion();
		assertNotNull(getPolicyVersion);
	}

	@Test(dependsOnMethods = { "setRootPolicyWithGoodRefs" })
	public void setRootPolicyWithRefToMissingPolicy() throws JAXBException
	{
		/*
		 * Before putting the empty refPolicySets (to make a policySets with references wrong), first put good root
		 * policySet without policy references (in case the current root PolicySet has references, uploading the empty
		 * refPolicySets before will be rejected because it makes the policySet with references invalid)
		 */
		resetPolicies();
		// Get current rootPolicy
		final IdReferenceType rootPolicyRef = testDomain.getPapResource().getPdpPropertiesResource()
				.getOtherPdpProperties().getRootPolicyRef();

		// Then attempt to put bad root policy set (referenced policysets in
		// Policy(Set)IdReferences do
		// not exist since refPolicySets empty)
		final PolicySet policySetWithRefs = (PolicySet) unmarshaller.unmarshal(new File(
				RestServiceTest.XACML_SAMPLES_DIR, "PolicyReference.Undef/policy.xml"));
		try
		{
			setRootPolicy(policySetWithRefs);
			fail("Invalid Root PolicySet (with invalid references) accepted");
		} catch (BadRequestException e)
		{
			// Bad request as expected
		}

		// make sure the rootPolicyRef is unchanged
		assertEquals(rootPolicyRef, testDomain.getPapResource().getPdpPropertiesResource().getOtherPdpProperties()
				.getRootPolicyRef(), "rootPolicyRef changed although root policy update rejected");
	}

	@Test(dependsOnMethods = { "setRootPolicyWithGoodRefs" })
	public void setRootPolicyWithCircularRef() throws JAXBException
	{
		/*
		 * Before putting the empty refPolicySets (to make a policySets with references wrong), first put good root
		 * policySet without policy references (in case the current root PolicySet has references, uploading the empty
		 * refPolicySets before will be rejected because it makes the policySet with references invalid)
		 */
		resetPolicies();
		// Get current rootPolicy
		final IdReferenceType rootPolicyRef = testDomain.getPapResource().getPdpPropertiesResource()
				.getOtherPdpProperties().getRootPolicyRef();

		// add refPolicies
		final PolicySet refPolicySet = (PolicySet) unmarshaller.unmarshal(new File(RestServiceTest.XACML_SAMPLES_DIR,
				"PolicyReference.Circular/refPolicies/invalid-pps-employee.xml"));
		testAddAndGetPolicy(refPolicySet);
		final PolicySet refPolicySet2 = (PolicySet) unmarshaller.unmarshal(new File(RestServiceTest.XACML_SAMPLES_DIR,
				"PolicyReference.Circular/refPolicies/pps-manager.xml"));
		testAddAndGetPolicy(refPolicySet2);

		// Then attempt to put bad root policy set (referenced policysets in
		// Policy(Set)IdReferences do
		// not exist since refPolicySets empty)
		final PolicySet policySetWithRefs = (PolicySet) unmarshaller.unmarshal(new File(
				RestServiceTest.XACML_SAMPLES_DIR, "PolicyReference.Circular/policy.xml"));
		try
		{
			setRootPolicy(policySetWithRefs);
			fail("Invalid Root PolicySet (with invalid references) accepted");
		} catch (BadRequestException e)
		{
			// Bad request as expected
		}

		// make sure the rootPolicyRef is unchanged
		assertEquals(rootPolicyRef, testDomain.getPapResource().getPdpPropertiesResource().getOtherPdpProperties()
				.getRootPolicyRef(), "rootPolicyRef changed although root policy update rejected");
	}

	@Test(dependsOnMethods = { "addAndGetPolicy" }, expectedExceptions = NotFoundException.class)
	public void addPolicyWithTooManyChildElements() throws JAXBException
	{
		// Then attempt to put bad root policy set (referenced policysets in
		// Policy(Set)IdReferences do
		// not exist since refPolicySets empty)
		final PolicySet badPolicySet = (PolicySet) unmarshaller.unmarshal(new File(RestServiceTest.XACML_SAMPLES_DIR,
				"policySetWithTooManyChildElements.xml"));
		try
		{
			testAddAndGetPolicy(badPolicySet);
			fail("Invalid PolicySet (too many child elements) accepted");
		} catch (ClientErrorException e)
		{
			assertEquals(e.getResponse().getStatus(), Status.REQUEST_ENTITY_TOO_LARGE.getStatusCode());
		}

		// make sure the policy is not there
		// MUST throw NotFoundException
		testDomain.getPapResource().getPoliciesResource().getPolicyResource(badPolicySet.getPolicySetId())
				.getPolicyVersions();
	}

	@Test(dependsOnMethods = { "addAndGetPolicy" }, expectedExceptions = NotFoundException.class)
	public void addPolicyTooDeep() throws JAXBException
	{
		// Then attempt to put bad root policy set (referenced policysets in
		// Policy(Set)IdReferences do
		// not exist since refPolicySets empty)
		final PolicySet badPolicySet = (PolicySet) unmarshaller.unmarshal(new File(RestServiceTest.XACML_SAMPLES_DIR,
				"policySetTooDeep.xml"));
		try
		{
			testAddAndGetPolicy(badPolicySet);
			fail("Invalid PolicySet (too deep element(s)) accepted");
		} catch (BadRequestException e)
		{
			// Bad request as expected
		}

		// make sure the policy is not there
		// MUST throw NotFoundException
		testDomain.getPapResource().getPoliciesResource().getPolicyResource(badPolicySet.getPolicySetId())
				.getPolicyVersions();
	}

	/**
	 * Create PDP evaluation test data. Various PolicySets/Requests/Responses from conformance tests.
	 * 
	 * 
	 * @return iterator over test data
	 * @throws URISyntaxException
	 * @throws IOException
	 */
	@DataProvider(name = "pdpTestFiles")
	public Iterator<Object[]> createData() throws URISyntaxException, IOException
	{
		final Collection<Object[]> testParams = new ArrayList<>();
		/*
		 * Each sub-directory of the root directory is data for a specific test. So we configure a test for each
		 * directory
		 */
		final File testRootDir = new File(RestServiceTest.XACML_SAMPLES_DIR, "pdp");
		for (final File subDir : testRootDir.listFiles(DIRECTORY_FILTER))
		{
			// specific test's resources directory location, used as parameter
			// to PdpTest(String)
			testParams.add(new Object[] { subDir });
		}

		return testParams.iterator();
	}

	private static void assertNormalizedEquals(Response expectedResponse, Response actualResponseFromPDP)
			throws JAXBException
	{
		// normalize responses for comparison
		final Response normalizedExpectedResponse = TestUtils.normalizeForComparison(expectedResponse);
		final Response normalizedActualResponse = TestUtils.normalizeForComparison(actualResponseFromPDP);
		assertEquals(normalizedExpectedResponse, normalizedActualResponse,
				"Actual and expected responses don't match (Status elements removed/ignored for comparison)");
	}

	@Test(dependsOnMethods = { "setRootPolicyWithGoodRefs" }, dataProvider = "pdpTestFiles")
	public void requestPDP(File testDirectory) throws JAXBException
	{
		resetPolicies();
		// reset attribute providers
		testDomain
				.getPapResource()
				.getAttributeProvidersResource()
				.updateAttributeProviderList(
						new AttributeProviders(Collections.<AbstractAttributeProvider> emptyList()));

		LOGGER.debug("Starting PDP test of directory '{}'", testDirectory);

		final File attributeProviderFile = new File(testDirectory, TEST_ATTRIBUTE_PROVIDER_FILENAME);
		if (attributeProviderFile.exists())
		{
			JAXBElement<AbstractAttributeProvider> jaxbElt = (JAXBElement<AbstractAttributeProvider>) unmarshaller
					.unmarshal(attributeProviderFile);
			testDomain
					.getPapResource()
					.getAttributeProvidersResource()
					.updateAttributeProviderList(
							new AttributeProviders(Collections.<AbstractAttributeProvider> singletonList(jaxbElt
									.getValue())));
		}

		final File refPoliciesDir = new File(testDirectory, TEST_REF_POLICIES_DIRECTORY_NAME);
		if (refPoliciesDir.exists() && refPoliciesDir.isDirectory())
		{
			for (final File policyFile : refPoliciesDir.listFiles())
			{
				if (policyFile.isFile())
				{
					final PolicySet policy = (PolicySet) unmarshaller.unmarshal(policyFile);
					testAddAndGetPolicy(policy);
					LOGGER.debug("Added policy from file: " + policyFile);
				}
			}
		}

		final PolicySet policy = (PolicySet) unmarshaller.unmarshal(new File(testDirectory,
				RestServiceTest.TEST_POLICY_FILENAME));
		// Add the policy and point the rootPolicyRef to new policy with refs to
		// instantiate it as root policy (validate, etc.)
		setRootPolicy(policy);

		final Request xacmlReq = (Request) unmarshaller.unmarshal(new File(testDirectory,
				RestServiceTest.REQUEST_FILENAME));
		final Response expectedResponse = (Response) unmarshaller.unmarshal(new File(testDirectory,
				RestServiceTest.EXPECTED_RESPONSE_FILENAME));
		final Response actualResponse = testDomain.getPdpResource().requestPolicyDecision(xacmlReq);
		assertNormalizedEquals(expectedResponse, actualResponse);
	}

	@Parameters("start.server")
	@Test(dependsOnMethods = { "requestPDP" })
	public void getRootPolicyRefAfterChangingPdpConfFile(@Optional("true") boolean startServer) throws JAXBException
	{
		// skip this if server not started locally (files not local)
		if (!startServer)
		{
			return;
		}

		File testDir = new File(RestServiceTest.XACML_SAMPLES_DIR, "IIIG301");

		final PolicySet policy = (PolicySet) unmarshaller.unmarshal(new File(testDir,
				RestServiceTest.TEST_POLICY_FILENAME));
		IdReferenceType newRootPolicyRef = new IdReferenceType(policy.getPolicySetId(), policy.getVersion(), null, null);
		testAddAndGetPolicy(policy);

		// change root policyref in PDP conf file
		Pdp pdpConf = pdpModelHandler.unmarshal(new StreamSource(testDomainPDPConfFile), Pdp.class);
		final StaticRefBasedRootPolicyProvider staticRefBasedRootPolicyProvider = (StaticRefBasedRootPolicyProvider) pdpConf
				.getRootPolicyProvider();
		staticRefBasedRootPolicyProvider.setPolicyRef(newRootPolicyRef);
		pdpModelHandler.marshal(pdpConf, testDomainPDPConfFile);
		long timeBeforeSync = System.currentTimeMillis();

		// Manual sync via GET /domains/{domainId}/pap/properties
		PdpProperties pdpProps = testDomain.getPapResource().getPdpPropertiesResource().getOtherPdpProperties();
		long lastModifiedTime = pdpProps.getLastModifiedTime().toGregorianCalendar().getTimeInMillis();
		assertTrue(lastModifiedTime > timeBeforeSync,
				"Manual sync with API getOtherPdpProperties() failed: returned lastModifiedTime is not up-to-date");

		assertEquals(
				pdpProps.getRootPolicyRef(),
				newRootPolicyRef,
				"Manual sync with API getOtherPdpProperties() failed: returned root policyRef does not match the one in PDP configuration file");

		assertEquals(
				pdpProps.getEnabledPolicies().get(0),
				newRootPolicyRef,
				"Manual sync with API getOtherPdpProperties() failed: returned first enabled policy does not match the root policyRef in PDP configuration file");

		// check PDP returned policy identifier
		final Request xacmlReq = (Request) unmarshaller.unmarshal(new File(testDir, RestServiceTest.REQUEST_FILENAME));
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
						"Manual sync with API getOtherPdpProperties() failed: PolicySetIdReference returned by PDP does not match the root policyRef in PDP configuration file");
				return;
			}
		}

		fail("Manual sync with API getOtherPdpProperties() failed: PolicySetIdReference returned by PDP does not match the root policyRef in PDP configuration file");
	}

	@Test(dependsOnMethods = { "setRootPolicyWithGoodRefs" })
	public void getPdpPropertiesAfterModifyingUsedPolicyDirectory() throws JAXBException
	{
		resetPolicies();

		final PolicySet refPolicySet = (PolicySet) unmarshaller.unmarshal(new File(RestServiceTest.XACML_SAMPLES_DIR,
				"pdp/PolicyReference.Valid/refPolicies/pps-employee.xml"));
		testAddAndGetPolicy(refPolicySet);

		// Set root policy referencing ref policy above
		final PolicySet policySetWithRef = (PolicySet) unmarshaller.unmarshal(new File(
				RestServiceTest.XACML_SAMPLES_DIR, "pdp/PolicyReference.Valid/policy.xml"));
		// Add the policy and point the rootPolicyRef to new policy with refs to
		// instantiate it as root policy (validate, etc.)
		setRootPolicy(policySetWithRef);

		// update referenced policy version on disk (we add ".1" to old version to have later version)
		PolicySet newRefPolicySet = new PolicySet(refPolicySet.getDescription(), refPolicySet.getPolicyIssuer(),
				refPolicySet.getPolicySetDefaults(), refPolicySet.getTarget(),
				refPolicySet.getPolicySetsAndPoliciesAndPolicySetIdReferences(),
				refPolicySet.getObligationExpressions(), refPolicySet.getAdviceExpressions(),
				refPolicySet.getPolicySetId(), refPolicySet.getVersion() + ".1",
				refPolicySet.getPolicyCombiningAlgId(), refPolicySet.getMaxDelegationDepth());
		Marshaller marshaller = RestServiceTest.JAXB_CTX.createMarshaller();
		File refPolicyDir = new File(testDomainPoliciesDirName, FlatFileDAOUtils.base64UrlEncode(newRefPolicySet
				.getPolicySetId()));
		File refPolicyFile = new File(refPolicyDir, newRefPolicySet.getVersion() + ".xml");
		marshaller.marshal(newRefPolicySet, refPolicyFile);

		// Manual sync of PDP's active policies via GET /domains/{domainId}/pap/policies
		long timeBeforeGetPolicies = System.currentTimeMillis();
		testDomain.getPapResource().getPoliciesResource().getPolicies().getLinks();
		long timeAfterGetPolicies = System.currentTimeMillis();

		// check new PDP lastModifiedTime
		PdpProperties pdpProps = testDomain.getPapResource().getPdpPropertiesResource().getOtherPdpProperties();
		long lastModifiedTime = pdpProps.getLastModifiedTime().toGregorianCalendar().getTimeInMillis();
		assertTrue(
				lastModifiedTime < timeBeforeGetPolicies && lastModifiedTime > timeAfterGetPolicies,
				"Manual sync with API getPolicies() failed: lastModifiedTime returned by getOtherPdpProperties() does not match the time when getPolicies() was called");
		// check enabled policies
		IdReferenceType newRefPolicySetRef = new IdReferenceType(newRefPolicySet.getPolicySetId(),
				newRefPolicySet.getVersion(), null, null);
		assertTrue(
				pdpProps.getEnabledPolicies().contains(newRefPolicySetRef),
				"Manual sync with API getPolicies() failed: enabledPolicies returned by getOtherPdpProperties() does not contain last policy version");

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

		// Manual sync of PDP's active policies via GET /domains/{domainId}/pap/policies
		long timeBeforeGetPolicies2 = System.currentTimeMillis();
		testDomain.getPapResource().getPoliciesResource().getPolicies().getLinks();
		long timeAfterGetPolicies2 = System.currentTimeMillis();

		// check new PDP lastModifiedTime
		long lastModifiedTime2 = testDomain.getPapResource().getPdpPropertiesResource().getOtherPdpProperties()
				.getLastModifiedTime().toGregorianCalendar().getTimeInMillis();
		assertTrue(
				lastModifiedTime2 < timeBeforeGetPolicies2 && lastModifiedTime2 > timeAfterGetPolicies2,
				"Manual sync with API getPolicies() failed: lastModifiedTime returned by getOtherPdpProperties() does not match the time when getPolicies() was called");
		// check enabled policies
		IdReferenceType newRootPolicySetRef = new IdReferenceType(newRootPolicySet.getPolicySetId(),
				newRootPolicySet.getVersion(), null, null);
		assertTrue(
				pdpProps.getEnabledPolicies().contains(newRootPolicySetRef),
				"Manual sync with API getPolicies() failed: enabledPolicies returned by getOtherPdpProperties() does not contain last policy version");
	}

}
