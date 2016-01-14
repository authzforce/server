/**
 * Copyright (C) 2015-2015 Thales Services SAS. All rights reserved. No warranty, explicit or implicit, provided.
 */
package org.ow2.authzforce.rest.service.test;

/**
 * @author Cyril DANGERVILLE
 *
 */
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.math.BigInteger;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

import javax.ws.rs.BadRequestException;
import javax.ws.rs.ClientErrorException;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.core.Response.Status;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;

import oasis.names.tc.xacml._3_0.core.schema.wd_17.IdReferenceType;
import oasis.names.tc.xacml._3_0.core.schema.wd_17.PolicySet;
import oasis.names.tc.xacml._3_0.core.schema.wd_17.Request;
import oasis.names.tc.xacml._3_0.core.schema.wd_17.Response;
import oasis.names.tc.xacml._3_0.core.schema.wd_17.Target;

import org.apache.cxf.jaxrs.utils.schemas.SchemaHandler;
import org.ow2.authzforce.core.test.utils.TestUtils;
import org.ow2.authzforce.core.xmlns.test.TestAttributeProvider;
import org.ow2.authzforce.pap.dao.file.FileBasedDAOUtils;
import org.ow2.authzforce.rest.api.jaxrs.AttributeProvidersResource;
import org.ow2.authzforce.rest.api.jaxrs.DomainPropertiesResource;
import org.ow2.authzforce.rest.api.jaxrs.DomainResource;
import org.ow2.authzforce.rest.api.jaxrs.DomainsResource;
import org.ow2.authzforce.rest.api.jaxrs.PoliciesResource;
import org.ow2.authzforce.rest.api.jaxrs.PolicyResource;
import org.ow2.authzforce.rest.api.jaxrs.PolicyVersionResource;
import org.ow2.authzforce.rest.api.xmlns.AttributeProviders;
import org.ow2.authzforce.rest.api.xmlns.Domain;
import org.ow2.authzforce.rest.api.xmlns.DomainProperties;
import org.ow2.authzforce.rest.api.xmlns.ResourceContent;
import org.ow2.authzforce.rest.api.xmlns.Resources;
import org.ow2.authzforce.xmlns.pdp.ext.AbstractAttributeProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import org.testng.ITestContext;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import org.w3._2005.atom.Link;

/**
 * Tests specific to a domain
 * <p>
 * TODO: tests for max number of policies per domain, and max number of versions per policy.
 *
 */
@ContextConfiguration(locations = { "classpath:META-INF/spring/client.xml" })
public class DomainTest extends AbstractTestNGSpringContextTests
{
	private static final String REF_POLICIES_DIRECTORY_NAME = "refPolicies";

	private static final String ATTRIBUTE_PROVIDER_FILENAME = "attributeProvider.xml";

	private static final Logger LOGGER = LoggerFactory.getLogger(DomainTest.class);

	private static final Random PRNG = new Random();

	private static final String DEFAULT_POLICYSET_FILENAME = "policySet.xml";

	private static final String EXTERNAL_ID = "test";

	private static final FileFilter DIRECTORY_FILTER = new FileFilter()
	{

		@Override
		public boolean accept(File pathname)
		{
			return pathname.isDirectory();
		}

	};

	/*
	 * JAXB context for (un)marshalling XACML
	 */
	private static final JAXBContext JAXB_CTX;

	static
	{
		try
		{
			JAXB_CTX = JAXBContext.newInstance(PolicySet.class, Request.class, Resources.class, TestAttributeProvider.class);
		} catch (JAXBException e)
		{
			throw new RuntimeException("Error instantiating JAXB context for XML to Java binding", e);
		}
	}

	/**
	 * XACML policy filename used by default when no PDP configuration file found, i.e. no file named {@value #PDP_CONF_FILENAME} exists in the test directory
	 */
	public final static String POLICY_FILENAME = "policy.xml";

	/**
	 * XACML request filename
	 */
	public final static String REQUEST_FILENAME = "request.xml";

	/**
	 * Expected XACML response filename
	 */
	public final static String EXPECTED_RESPONSE_FILENAME = "response.xml";

	private Unmarshaller unmarshaller = null;

	@Autowired
	private SchemaHandler clientApiSchemaHandler;

	@Autowired
	private Resource xacmlSamplesDirResource;
	private File xacmlSamplesDir = null;

	private DomainResource testDomain = null;
	private String testDomainId = null;

	@BeforeClass
	public void addDomain(ITestContext testCtx) throws JAXBException, IOException
	{
		/*
		 * WARNING: if tests are to be multi-threaded, modify according to Thread-safety section of CXF JAX-RS client API documentation
		 * http://cxf.apache.org/docs/jax-rs-client-api.html#JAX-RSClientAPI-ThreadSafety
		 */
		final DomainsResource client = (DomainsResource) testCtx.getAttribute(DomainSetTest.REST_CLIENT_TEST_CONTEXT_ATTRIBUTE_ID);
		final Link domainLink = client.addDomain(new DomainProperties("Some description", EXTERNAL_ID, null));
		assertNotNull(domainLink, "Domain creation failure");

		// The link href gives the new domain ID
		testDomainId = domainLink.getHref();
		LOGGER.debug("Added domain ID={}", testDomainId);
		testDomain = client.getDomainResource(testDomainId);
		assertNotNull(testDomain, String.format("Error retrieving domain ID=%s", testDomainId));

		// Unmarshall test XACML PolicySet
		if (!xacmlSamplesDirResource.exists())
		{
			throw new RuntimeException("Test data directory '" + xacmlSamplesDirResource + "' does not exist");
		}

		xacmlSamplesDir = xacmlSamplesDirResource.getFile();

		unmarshaller = JAXB_CTX.createUnmarshaller();
		unmarshaller.setSchema(this.clientApiSchemaHandler.getSchema());
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
		assertNotNull(props.getRootPolicyRef());
	}

	@Test(dependsOnMethods = { "getDomainProperties" })
	public void updateDomainPropertiesExceptRootPolicyRef()
	{
		final DomainPropertiesResource propsResource = testDomain.getDomainPropertiesResource();
		assertNotNull(propsResource);

		byte[] randomBytes = new byte[128];
		PRNG.nextBytes(randomBytes);
		String description = FileBasedDAOUtils.base64UrlEncode(randomBytes);
		byte[] randomBytes2 = new byte[16];
		PRNG.nextBytes(randomBytes2);
		// externalId must be a xs:NCName, therefore cannot start with a number
		String externalId = "external" + FileBasedDAOUtils.base64UrlEncode(randomBytes2);
		IdReferenceType rootPolicyRef = propsResource.getDomainProperties().getRootPolicyRef();
		propsResource.updateDomainProperties(new DomainProperties(description, externalId, null));
		// verify result
		final DomainProperties newProps = testDomain.getDomainPropertiesResource().getDomainProperties();
		assertEquals(newProps.getDescription(), description);
		assertEquals(newProps.getExternalId(), externalId);
		assertEquals(newProps.getRootPolicyRef(), rootPolicyRef);
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
		final AttributeProviders attributeProviders = testDomain.getPapResource().getAttributeProvidersResource().getAttributeProviderList();
		assertNotNull(attributeProviders);
	}

	// FIXME: test attributeProviders resource
	@Test(dependsOnMethods = { "getAttributeProviders" })
	public void updateAttributeProviders() throws JAXBException
	{
		final AttributeProvidersResource attributeProvidersResource = testDomain.getPapResource().getAttributeProvidersResource();
		JAXBElement<TestAttributeProvider> jaxbElt = (JAXBElement<TestAttributeProvider>) JAXB_CTX.createUnmarshaller().unmarshal(
				new File(xacmlSamplesDir, "pdp/IIA002(PolicySet)/attributeProvider.xml"));
		TestAttributeProvider testAttrProvider = jaxbElt.getValue();
		AttributeProviders updateAttrProvidersResult = attributeProvidersResource.updateAttributeProviderList(new AttributeProviders(Collections
				.<AbstractAttributeProvider> singletonList(testAttrProvider)));
		assertNotNull(updateAttrProvidersResult);
		assertEquals(updateAttrProvidersResult.getAttributeProviders().size(), 1);
		AbstractAttributeProvider updateAttrProvidersItem = updateAttrProvidersResult.getAttributeProviders().get(0);
		if (updateAttrProvidersItem instanceof TestAttributeProvider)
		{
			assertEquals(((TestAttributeProvider) updateAttrProvidersItem).getAttributes(), testAttrProvider.getAttributes());
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
			assertEquals(((TestAttributeProvider) getAttrProvidersItem).getAttributes(), testAttrProvider.getAttributes());
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
		final IdReferenceType rootPolicyRef = testDomain.getDomainPropertiesResource().getDomainProperties().getRootPolicyRef();
		final List<Link> links = testDomain.getPapResource().getPoliciesResource().getPolicyResource(rootPolicyRef.getValue()).getPolicyVersions().getLinks();
		assertTrue(links.size() > 0, "No root policy version found");
	}

	private static PolicySet createDumbPolicySet(String policyId, String version)
	{
		return new PolicySet(null, null, null, new Target(null), null, null, null, policyId, version,
				"urn:oasis:names:tc:xacml:3.0:policy-combining-algorithm:deny-unless-permit", BigInteger.ZERO);
	}

	private static void matchPolicySets(PolicySet actual, PolicySet expected, String testedMethodId)
	{
		assertEquals(
				actual.getPolicySetId(),
				expected.getPolicySetId(),
				String.format("Actual PolicySetId (='%s') from %s() != expected PolicySetId (='%s')", actual.getPolicySetId(), testedMethodId,
						expected.getPolicySetId()));
		assertEquals(
				actual.getVersion(),
				expected.getVersion(),
				String.format("Actual PolicySet Version (='%s') from %s() != expected PolicySet Version (='%s')", actual.getVersion(), testedMethodId,
						expected.getVersion()));
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
	 * Adds a given PolicySet and returns allocated resource ID. It also checks 2 things: 1) The response's PolicySet matches the input PolicySet (simply
	 * checking PolicySet IDs and versions) 2) The reponse to a getPolicySet() also matches the input PolicySet, to make sure the new PolicySet was actually
	 * committed succesfully and no error occurred in the process. 3) Response to getPolicyResources() with same PolicySetId and Version matches.
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

		// check PolicySet of added policy id/version is actually the one we added
		final PolicySet getRespPolicySet = policyRes.getPolicyVersionResource(versionResId).getPolicyVersion();
		matchPolicySets(getRespPolicySet, policySet, "getPolicy");

		return policyResId;
	}

	@Test(dependsOnMethods = { "getRootPolicy" })
	public void addAndGetPolicy()
	{
		PolicySet policySet = createDumbPolicySet("testPolicyAdd", "1.0");
		testAddAndGetPolicy(policySet);

		PolicySet policySet2 = createDumbPolicySet("testPolicyAdd", "1.1");
		testAddAndGetPolicy(policySet2);

		PolicySet policySet3 = createDumbPolicySet("testPolicyAdd", "1.2");
		testAddAndGetPolicy(policySet3);
		Resources policyVersionsResources = testDomain.getPapResource().getPoliciesResource().getPolicyResource("testPolicyAdd").getPolicyVersions();
		assertEquals(policyVersionsResources.getLinks().size(), 3);

	}

	@Test(dependsOnMethods = { "addAndGetPolicy" })
	public void addConflictingPolicyVersion()
	{
		PolicySet policySet = createDumbPolicySet("testAddConflictingPolicyVersion", "1.0");
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
	public void deletedAndTryGetUnusedPolicy()
	{
		PolicySet policySet1 = createDumbPolicySet("testPolicyDelete", "1.2.3");
		testAddAndGetPolicy(policySet1);

		PolicySet policySet2 = createDumbPolicySet("testPolicyDelete", "1.3.1");
		testAddAndGetPolicy(policySet2);

		// delete policy (all versions)
		PolicyResource policyResource = testDomain.getPapResource().getPoliciesResource().getPolicyResource("testPolicyDelete");
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

	@Test(dependsOnMethods = { "deletedAndTryGetUnusedPolicy" })
	public void deletedAndTryGetPolicyVersion()
	{
		PolicySet policySet1 = createDumbPolicySet("testPolicyDelete", "1.2.3");
		testAddAndGetPolicy(policySet1);

		PolicySet policySet2 = createDumbPolicySet("testPolicyDelete", "1.3.1");
		testAddAndGetPolicy(policySet2);

		// delete one of the versions
		PolicyVersionResource policyVersionRes = testDomain.getPapResource().getPoliciesResource().getPolicyResource("testPolicyDelete")
				.getPolicyVersionResource("1.2.3");
		PolicySet deletedPolicy = policyVersionRes.deletePolicyVersion();
		matchPolicySets(deletedPolicy, policySet1, "deletedAndTryGetPolicy");

		try
		{
			policyVersionRes.getPolicyVersion();
			org.testng.Assert.fail("Policy version removal failed (resource still there");
		} catch (NotFoundException e)
		{
			// OK
		}

		Resources policyVersionsResources = testDomain.getPapResource().getPoliciesResource().getPolicyResource("testPolicyDelete").getPolicyVersions();
		assertEquals(policyVersionsResources.getLinks().size(), 1);
		assertTrue(isHrefMatched("1.3.1", policyVersionsResources.getLinks()));

	}

	@Test(dependsOnMethods = { "getRootPolicy", "deletedAndTryGetUnusedPolicy" })
	public void deleteRootPolicy()
	{
		final IdReferenceType rootPolicyRef = testDomain.getDomainPropertiesResource().getDomainProperties().getRootPolicyRef();

		// must be rejected
		try
		{
			testDomain.getPapResource().getPoliciesResource().getPolicyResource(rootPolicyRef.getValue()).deletePolicy();
			fail("Wrongly accepted to remove root policy");
		} catch (BadRequestException e)
		{
			// OK, expected
		}

		assertNotNull(testDomain.getPapResource().getPoliciesResource().getPolicyResource(rootPolicyRef.getValue()).getPolicyVersions(),
				"Root policy was actually removed although server return HTTP 400 for removal attempt");

		// make sure rootPolicyRef unchanged
		assertEquals(rootPolicyRef, testDomain.getDomainPropertiesResource().getDomainProperties().getRootPolicyRef(),
				"rootPolicyRef changed although root policy removal rejected");
	}

	private IdReferenceType setRootPolicy(String policyId, String version)
	{
		DomainPropertiesResource propsRes = testDomain.getDomainPropertiesResource();
		DomainProperties props = propsRes.getDomainProperties();
		String externalId = props.getExternalId();
		String desc = props.getDescription();
		IdReferenceType newRootPolicyRef = new IdReferenceType(policyId, version, null, null);
		propsRes.updateDomainProperties(new DomainProperties(desc, externalId, newRootPolicyRef));
		return newRootPolicyRef;
	}

	private IdReferenceType setRootPolicy(PolicySet policySet)
	{
		testAddAndGetPolicy(policySet);

		return setRootPolicy(policySet.getPolicySetId(), policySet.getVersion());
	}

	@Test(dependsOnMethods = { "addAndGetPolicy", "deleteRootPolicy", "updateDomainPropertiesExceptRootPolicyRef" })
	public void updateRootPolicyRefToValidPolicy() throws JAXBException
	{
		DomainPropertiesResource propsRes = testDomain.getDomainPropertiesResource();
		DomainProperties props = propsRes.getDomainProperties();
		String externalId = props.getExternalId();
		String desc = props.getDescription();

		// get current root policy ID
		final IdReferenceType rootPolicyRef = props.getRootPolicyRef();
		PolicyResource oldRootPolicyRes = testDomain.getPapResource().getPoliciesResource().getPolicyResource(rootPolicyRef.getValue());

		// point rootPolicyRef to another one
		String oldRootPolicyId = rootPolicyRef.getValue();
		String newRootPolicyId = oldRootPolicyId + ".new";
		PolicySet policySet = createDumbPolicySet(newRootPolicyId, "1.0");
		IdReferenceType newRootPolicyRef = setRootPolicy(policySet);

		// verify update
		final DomainProperties newProps = testDomain.getDomainPropertiesResource().getDomainProperties();
		assertEquals(newProps.getDescription(), desc);
		assertEquals(newProps.getExternalId(), externalId);
		assertEquals(newProps.getRootPolicyRef(), newRootPolicyRef);

		// delete previous root policy ref to see if it succeeds
		oldRootPolicyRes.deletePolicy();
	}

	@Test(dependsOnMethods = { "updateRootPolicyRefToValidPolicy" })
	public void updateRootPolicyRefToMissingPolicy() throws JAXBException
	{
		DomainPropertiesResource propsRes = testDomain.getDomainPropertiesResource();
		DomainProperties props = propsRes.getDomainProperties();
		String externalId = props.getExternalId();
		String desc = props.getDescription();

		// get current root policy ID
		final IdReferenceType rootPolicyRef = props.getRootPolicyRef();

		// point rootPolicyRef to another one
		String oldRootPolicyId = rootPolicyRef.getValue();
		String newRootPolicyId = oldRootPolicyId + ".new";

		IdReferenceType newRootPolicyRef = new IdReferenceType(newRootPolicyId, null, null, null);

		// MUST fail
		try
		{
			propsRes.updateDomainProperties(new DomainProperties(desc, externalId, newRootPolicyRef));
			fail("Setting rootPolicyRef to missing policy did not fail as expected");
		} catch (BadRequestException e)
		{
			// OK
		}

		// make sure rootPolicyRef unchanged
		assertEquals(rootPolicyRef, testDomain.getDomainPropertiesResource().getDomainProperties().getRootPolicyRef());
	}

	@Test(dependsOnMethods = { "updateRootPolicyRefToValidPolicy" })
	public void updateRootPolicyRefToValidVersion() throws JAXBException
	{
		DomainPropertiesResource propsRes = testDomain.getDomainPropertiesResource();
		DomainProperties props = propsRes.getDomainProperties();
		String externalId = props.getExternalId();
		String desc = props.getDescription();

		PolicySet policySet = createDumbPolicySet("testUpdateRootPolicyRefToValidVersion", "4.4");
		testAddAndGetPolicy(policySet);

		PolicySet policySet2 = createDumbPolicySet("testUpdateRootPolicyRefToValidVersion", "4.5");
		testAddAndGetPolicy(policySet2);

		PolicySet policySet3 = createDumbPolicySet("testUpdateRootPolicyRefToValidVersion", "4.6");
		testAddAndGetPolicy(policySet3);

		IdReferenceType newRootPolicyRef = new IdReferenceType("testUpdateRootPolicyRefToValidVersion", "4.5", null, null);
		propsRes.updateDomainProperties(new DomainProperties(desc, externalId, newRootPolicyRef));

		// verify update
		final DomainProperties newProps = testDomain.getDomainPropertiesResource().getDomainProperties();
		assertEquals(newProps.getDescription(), desc);
		assertEquals(newProps.getExternalId(), externalId);
		assertEquals(newProps.getRootPolicyRef(), newRootPolicyRef);

		// delete other policy versions (must succeed) to check that the root policyRef is not pointing to one of them by mistake
		PolicyResource policyRes = testDomain.getPapResource().getPoliciesResource().getPolicyResource("testUpdateRootPolicyRefToValidVersion");
		policyRes.getPolicyVersionResource("4.4").deletePolicyVersion();
		policyRes.getPolicyVersionResource("4.6").deletePolicyVersion();
	}

	@Test(dependsOnMethods = { "updateRootPolicyRefToValidVersion" })
	public void updateRootPolicyRefToMissingVersion() throws JAXBException
	{
		DomainPropertiesResource propsRes = testDomain.getDomainPropertiesResource();
		DomainProperties props = propsRes.getDomainProperties();
		String externalId = props.getExternalId();
		String desc = props.getDescription();

		// get current root policy ID
		final IdReferenceType rootPolicyRef = props.getRootPolicyRef();

		// point rootPolicyRef to same policy id but different version
		String rootPolicyId = rootPolicyRef.getValue();
		IdReferenceType newRootPolicyRef = new IdReferenceType(rootPolicyId, "0.0.0.1", null, null);
		// MUST FAIL
		try
		{
			propsRes.updateDomainProperties(new DomainProperties(desc, externalId, newRootPolicyRef));
			fail("Setting rootPolicyRef to missing policy version did not fail as expected.");
		} catch (BadRequestException e)
		{
			// OK
		}

		// make sure rootPolicyRef unchanged
		assertEquals(rootPolicyRef, testDomain.getDomainPropertiesResource().getDomainProperties().getRootPolicyRef());
	}

	private void resetPolicies() throws JAXBException
	{
		final PolicySet rootPolicySet = (PolicySet) unmarshaller.unmarshal(new File(xacmlSamplesDir, DEFAULT_POLICYSET_FILENAME));
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
		 * Before putting the empty refPolicySets (to make a policySets with references wrong), first put good root policySet without policy references (in case
		 * the current root PolicySet has references, uploading the empty refPolicySets before will be rejected because it makes the policySet with references
		 * invalid)
		 */
		resetPolicies();

		final PolicySet refPolicySet = (PolicySet) unmarshaller.unmarshal(new File(xacmlSamplesDir, "pdp/PolicyReference.Valid/refPolicies/pps-employee.xml"));
		String refPolicyResId = testAddAndGetPolicy(refPolicySet);

		// Set root policy referencing ref policy above
		final PolicySet policySetWithRef = (PolicySet) unmarshaller.unmarshal(new File(xacmlSamplesDir, "pdp/PolicyReference.Valid/policy.xml"));
		// Add the policy and point the rootPolicyRef to new policy with refs to instantiate it as root policy (validate, etc.)
		setRootPolicy(policySetWithRef);

		// Delete referenced policy -> must fail (because root policy is still referencing one of them)
		final PolicyResource refPolicyResource = testDomain.getPapResource().getPoliciesResource().getPolicyResource(refPolicyResId);
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
		final PolicySet getPolicyVersion = refPolicyResource.getPolicyVersionResource(refPolicySet.getVersion()).getPolicyVersion();
		assertNotNull(getPolicyVersion);
	}

	@Test(dependsOnMethods = { "setRootPolicyWithGoodRefs" })
	public void setRootPolicyWithRefToMissingPolicy() throws JAXBException
	{
		/*
		 * Before putting the empty refPolicySets (to make a policySets with references wrong), first put good root policySet without policy references (in case
		 * the current root PolicySet has references, uploading the empty refPolicySets before will be rejected because it makes the policySet with references
		 * invalid)
		 */
		resetPolicies();
		// Get current rootPolicy
		final IdReferenceType rootPolicyRef = testDomain.getDomainPropertiesResource().getDomainProperties().getRootPolicyRef();

		// Then attempt to put bad root policy set (referenced policysets in Policy(Set)IdReferences do
		// not exist since refPolicySets empty)
		final PolicySet policySetWithRefs = (PolicySet) unmarshaller.unmarshal(new File(xacmlSamplesDir, "PolicyReference.Undef/policy.xml"));
		try
		{
			setRootPolicy(policySetWithRefs);
			fail("Invalid Root PolicySet (with invalid references) accepted");
		} catch (BadRequestException e)
		{
			// Bad request as expected
		}

		// make sure the rootPolicyRef is unchanged
		assertEquals(rootPolicyRef, testDomain.getDomainPropertiesResource().getDomainProperties().getRootPolicyRef(),
				"rootPolicyRef changed although root policy update rejected");
	}

	@Test(dependsOnMethods = { "setRootPolicyWithGoodRefs" })
	public void setRootPolicyWithCircularRef() throws JAXBException
	{
		/*
		 * Before putting the empty refPolicySets (to make a policySets with references wrong), first put good root policySet without policy references (in case
		 * the current root PolicySet has references, uploading the empty refPolicySets before will be rejected because it makes the policySet with references
		 * invalid)
		 */
		resetPolicies();
		// Get current rootPolicy
		final IdReferenceType rootPolicyRef = testDomain.getDomainPropertiesResource().getDomainProperties().getRootPolicyRef();

		// add refPolicies
		final PolicySet refPolicySet = (PolicySet) unmarshaller.unmarshal(new File(xacmlSamplesDir,
				"PolicyReference.Circular/refPolicies/invalid-pps-employee.xml"));
		testAddAndGetPolicy(refPolicySet);
		final PolicySet refPolicySet2 = (PolicySet) unmarshaller.unmarshal(new File(xacmlSamplesDir, "PolicyReference.Circular/refPolicies/pps-manager.xml"));
		testAddAndGetPolicy(refPolicySet2);

		// Then attempt to put bad root policy set (referenced policysets in Policy(Set)IdReferences do
		// not exist since refPolicySets empty)
		final PolicySet policySetWithRefs = (PolicySet) unmarshaller.unmarshal(new File(xacmlSamplesDir, "PolicyReference.Circular/policy.xml"));
		try
		{
			setRootPolicy(policySetWithRefs);
			fail("Invalid Root PolicySet (with invalid references) accepted");
		} catch (BadRequestException e)
		{
			// Bad request as expected
		}

		// make sure the rootPolicyRef is unchanged
		assertEquals(rootPolicyRef, testDomain.getDomainPropertiesResource().getDomainProperties().getRootPolicyRef(),
				"rootPolicyRef changed although root policy update rejected");
	}

	@Test(dependsOnMethods = { "addAndGetPolicy" }, expectedExceptions = NotFoundException.class)
	public void addPolicyWithTooManyChildElements() throws JAXBException
	{
		// Then attempt to put bad root policy set (referenced policysets in Policy(Set)IdReferences do
		// not exist since refPolicySets empty)
		final PolicySet badPolicySet = (PolicySet) unmarshaller.unmarshal(new File(xacmlSamplesDir, "policySetWithTooManyChildElements.xml"));
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
		testDomain.getPapResource().getPoliciesResource().getPolicyResource(badPolicySet.getPolicySetId()).getPolicyVersions();
	}

	@Test(dependsOnMethods = { "addAndGetPolicy" }, expectedExceptions = NotFoundException.class)
	public void addPolicyTooDeep() throws JAXBException
	{
		// Then attempt to put bad root policy set (referenced policysets in Policy(Set)IdReferences do
		// not exist since refPolicySets empty)
		final PolicySet badPolicySet = (PolicySet) unmarshaller.unmarshal(new File(xacmlSamplesDir, "policySetTooDeep.xml"));
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
		testDomain.getPapResource().getPoliciesResource().getPolicyResource(badPolicySet.getPolicySetId()).getPolicyVersions();
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
		 * Each sub-directory of the root directory is data for a specific test. So we configure a test for each directory
		 */
		final File testRootDir = new File(xacmlSamplesDir, "pdp");
		for (final File subDir : testRootDir.listFiles(DIRECTORY_FILTER))
		{
			// specific test's resources directory location, used as parameter to PdpTest(String)
			testParams.add(new Object[] { subDir });
		}

		return testParams.iterator();
	}

	private static void assertNormalizedEquals(Response expectedResponse, Response actualResponseFromPDP) throws JAXBException
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
		testDomain.getPapResource().getAttributeProvidersResource()
				.updateAttributeProviderList(new AttributeProviders(Collections.<AbstractAttributeProvider> emptyList()));

		LOGGER.debug("Starting PDP test of directory '{}'", testDirectory);

		final File attributeProviderFile = new File(testDirectory, ATTRIBUTE_PROVIDER_FILENAME);
		if (attributeProviderFile.exists())
		{
			JAXBElement<AbstractAttributeProvider> jaxbElt = (JAXBElement<AbstractAttributeProvider>) JAXB_CTX.createUnmarshaller().unmarshal(
					attributeProviderFile);
			testDomain.getPapResource().getAttributeProvidersResource()
					.updateAttributeProviderList(new AttributeProviders(Collections.<AbstractAttributeProvider> singletonList(jaxbElt.getValue())));
		}

		final File refPoliciesDir = new File(testDirectory, REF_POLICIES_DIRECTORY_NAME);
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

		final PolicySet policy = (PolicySet) unmarshaller.unmarshal(new File(testDirectory, POLICY_FILENAME));
		// Add the policy and point the rootPolicyRef to new policy with refs to instantiate it as root policy (validate, etc.)
		setRootPolicy(policy);

		final Request xacmlReq = (Request) unmarshaller.unmarshal(new File(testDirectory, REQUEST_FILENAME));
		final Response expectedResponse = (Response) unmarshaller.unmarshal(new File(testDirectory, EXPECTED_RESPONSE_FILENAME));
		final Response actualResponse = testDomain.getPdpResource().requestPolicyDecision(xacmlReq);
		assertNormalizedEquals(expectedResponse, actualResponse);
	}

}
