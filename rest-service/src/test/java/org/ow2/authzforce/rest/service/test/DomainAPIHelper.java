/**
 * Copyright (C) 2012-2016 Thales Services SAS.
 *
 * This file is part of AuthZForce CE.
 *
 * AuthZForce CE is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * AuthZForce CE is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with AuthZForce CE.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.ow2.authzforce.rest.service.test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import java.io.File;
import java.util.Date;
import java.util.List;

import javax.ws.rs.ClientErrorException;
import javax.ws.rs.core.Response.Status;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.transform.stream.StreamSource;

import oasis.names.tc.xacml._3_0.core.schema.wd_17.IdReferenceType;
import oasis.names.tc.xacml._3_0.core.schema.wd_17.PolicySet;

import org.ow2.authzforce.core.pdp.impl.PdpModelHandler;
import org.ow2.authzforce.core.xmlns.pdp.Pdp;
import org.ow2.authzforce.core.xmlns.pdp.StaticRefBasedRootPolicyProvider;
import org.ow2.authzforce.pap.dao.flatfile.FlatFileDAOUtils;
import org.ow2.authzforce.rest.api.jaxrs.DomainResource;
import org.ow2.authzforce.rest.api.jaxrs.PdpPropertiesResource;
import org.ow2.authzforce.rest.api.jaxrs.PoliciesResource;
import org.ow2.authzforce.rest.api.jaxrs.PolicyResource;
import org.ow2.authzforce.rest.api.xmlns.DomainProperties;
import org.ow2.authzforce.rest.api.xmlns.PdpPropertiesUpdate;
import org.ow2.authzforce.rest.api.xmlns.Resources;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3._2005.atom.Link;

class DomainAPIHelper
{
	private static final UnsupportedOperationException UNSUPPORTED_MODIFY_PDP_CONF_FILE_OPERATION_EXCEPTION = new UnsupportedOperationException(
			"Operation not supported with undefined pdpModelHandler");

	private static final Logger LOGGER = LoggerFactory.getLogger(DomainAPIHelper.class);

	private final Unmarshaller unmarshaller;

	private final DomainResource domain;
	private final File domainPropertiesFile;

	private final File domainPDPConfFile;

	private final File domainPoliciesDirName;

	private final PdpModelHandler pdpModelHandler;

	DomainAPIHelper(String domainId, DomainResource domain, Unmarshaller apiXmlUnmarshaller,
			PdpModelHandler pdpModelHandler)
	{
		this.domain = domain;
		final File testDomainDir = new File(RestServiceTest.DOMAINS_DIR, domainId);
		this.domainPropertiesFile = new File(testDomainDir, RestServiceTest.DOMAIN_PROPERTIES_FILENAME);
		this.domainPDPConfFile = new File(testDomainDir, RestServiceTest.DOMAIN_PDP_CONF_FILENAME);
		this.domainPoliciesDirName = new File(testDomainDir, RestServiceTest.DOMAIN_POLICIES_DIRNAME);
		this.unmarshaller = apiXmlUnmarshaller;
		this.pdpModelHandler = pdpModelHandler;
	}

	static void matchPolicySets(PolicySet actual, PolicySet expected, String testedMethodId)
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

	static boolean isHrefMatched(String href, List<Link> links)
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

	void modifyDomainPropertiesFile(String newExternalId, boolean isFilesystemLegacy) throws InterruptedException,
			JAXBException
	{
		/*
		 * If filesystem is legacy, it means here that file timestamp (mtime in particular) resolution is 1 sec, i.e.
		 * milliseconds are rounded to zero always. Therefore, in this unit test, with such a filesystem, the file
		 * modification does undetected because it occurs in less than 1 sec after the last PDP sync/reload. See also:
		 * http://www.coderanch.com/t/384700/java/java/File-lastModified-windows-linux
		 * 
		 * So if a legacy filesystem mode is enabled, we wait at least 1 sec before updating the file, so that the
		 * file's mtime is different and the change detected a result on such legacy.
		 * 
		 * NOTE: ext3 has only second resolution (therefore considered legacy), whereas ext4 has nanosecond resolution.
		 * If you have resolution higher than the millisecond in Java (microsec, nanosec), e.g. with ext4, JAVA 8 is
		 * also required: http://bugs.java.com/view_bug.do?bug_id=6939260 (A possible workaround would record the
		 * last-modified-time in all monitored files. We consider it not worth the trouble since ext4 is now the default
		 * option on modern Linux distributions such Ubuntu.)
		 */

		// test sync with properties file
		final DomainProperties props = domain.getDomainPropertiesResource().getDomainProperties();
		final org.ow2.authzforce.pap.dao.flatfile.xmlns.DomainProperties newProps = new org.ow2.authzforce.pap.dao.flatfile.xmlns.DomainProperties(
				props.getDescription(), newExternalId);
		/*
		 * Wait at least 1 sec before updating the file, if filesystem is "legacy" (file timestamp limited to second
		 * resolution) as explained above
		 */
		if (isFilesystemLegacy)
		{
			Thread.sleep(1000);
		}

		RestServiceTest.JAXB_CTX.createMarshaller().marshal(newProps, domainPropertiesFile);
		if (LOGGER.isDebugEnabled())
		{
			LOGGER.debug(
					"Updated externalId in file '{}' - lastModifiedTime = {}",
					domainPropertiesFile,
					RestServiceTest.UTC_DATE_WITH_MILLIS_FORMATTER.format(new Date(domainPropertiesFile.lastModified())));
		}
	}

	/**
	 * 
	 * @param isFilesystemLegacy
	 * @return new root policy reference
	 * @throws JAXBException
	 * @throws InterruptedException
	 */
	IdReferenceType modifyPdpConfFile(boolean isFilesystemLegacy) throws JAXBException, InterruptedException
	{
		if (pdpModelHandler == null)
		{
			throw UNSUPPORTED_MODIFY_PDP_CONF_FILE_OPERATION_EXCEPTION;
		}

		resetPolicies();

		final JAXBElement<PolicySet> jaxbPolicy = unmarshal(new File(RestServiceTest.XACML_IIIG301_PDP_TEST_DIR,
				RestServiceTest.TEST_POLICY_FILENAME), PolicySet.class);
		final PolicySet policy = jaxbPolicy.getValue();
		IdReferenceType newRootPolicyRef = new IdReferenceType(policy.getPolicySetId(), policy.getVersion(), null, null);
		testAddAndGetPolicy(policy);

		// change root policyref in PDP conf file
		Pdp pdpConf = pdpModelHandler.unmarshal(new StreamSource(domainPDPConfFile), Pdp.class);
		final StaticRefBasedRootPolicyProvider staticRefBasedRootPolicyProvider = (StaticRefBasedRootPolicyProvider) pdpConf
				.getRootPolicyProvider();
		staticRefBasedRootPolicyProvider.setPolicyRef(newRootPolicyRef);
		/*
		 * Wait at least 1 sec before updating the file, if filesystem is "legacy" (file timestamp limited to second
		 * resolution), to make sure the lastModifiedTime will be actually changed after updating
		 */
		if (isFilesystemLegacy)
		{
			Thread.sleep(1000);
		}

		pdpModelHandler.marshal(pdpConf, domainPDPConfFile);
		return newRootPolicyRef;
	}

	/**
	 * Updates either the root policy or another policy referenced from root
	 * 
	 * @param oldRootPolicyFile
	 *            root policy to be updated if {@code updateRoot}
	 * @param oldRefPolicyFile
	 *            referenced policy to be updated if {@code !updateRoot}
	 * @param updateRoot
	 *            true iff we update root, else update the ref policy
	 * @param isFileSystemLegacy
	 * @return reference to new policy from update
	 * @throws JAXBException
	 * @throws InterruptedException
	 */
	IdReferenceType addRootPolicyWithRefAndUpdate(File oldRootPolicyFile, File oldRefPolicyFile, boolean updateRoot,
			boolean isFileSystemLegacy) throws JAXBException, InterruptedException
	{
		resetPolicies();

		final JAXBElement<PolicySet> jaxbElt = unmarshal(oldRefPolicyFile, PolicySet.class);
		final PolicySet refPolicySet = jaxbElt.getValue();
		testAddAndGetPolicy(refPolicySet);

		// Set root policy referencing ref policy above
		final JAXBElement<PolicySet> jaxbElt2 = unmarshal(oldRootPolicyFile, PolicySet.class);
		final PolicySet rootPolicySetWithRef = jaxbElt2.getValue();
		// Add the policy and point the rootPolicyRef to new policy with refs to
		// instantiate it as root policy (validate, etc.)
		setRootPolicy(rootPolicySetWithRef, true);

		final PolicySet oldPolicyToUpdate = updateRoot ? rootPolicySetWithRef : refPolicySet;

		// update policy version on disk (we add ".1" to old version to have later version)
		final PolicySet newPolicy = new PolicySet(oldPolicyToUpdate.getDescription(),
				oldPolicyToUpdate.getPolicyIssuer(), oldPolicyToUpdate.getPolicySetDefaults(),
				oldPolicyToUpdate.getTarget(), oldPolicyToUpdate.getPolicySetsAndPoliciesAndPolicySetIdReferences(),
				oldPolicyToUpdate.getObligationExpressions(), oldPolicyToUpdate.getAdviceExpressions(),
				oldPolicyToUpdate.getPolicySetId(), oldPolicyToUpdate.getVersion() + ".1",
				oldPolicyToUpdate.getPolicyCombiningAlgId(), oldPolicyToUpdate.getMaxDelegationDepth());
		Marshaller marshaller = RestServiceTest.JAXB_CTX.createMarshaller();
		File policyDir = new File(domainPoliciesDirName, FlatFileDAOUtils.base64UrlEncode(oldPolicyToUpdate
				.getPolicySetId()));
		File policyVersionFile = new File(policyDir, newPolicy.getVersion() + ".xml");
		/*
		 * Wait at least 1 sec before updating the file, if filesystem is "legacy" (file timestamp limited to second
		 * resolution), to make sure the lastModifiedTime will be actually changed after updating
		 */
		if (isFileSystemLegacy)
		{
			Thread.sleep(1000);
		}

		marshaller.marshal(newPolicy, policyVersionFile);
		if (LOGGER.isDebugEnabled())
		{
			LOGGER.debug("Updated directory of ref-policy '{}' with file '{}' - lastModifiedTime = {}",
					newPolicy.getPolicySetId(), policyVersionFile,
					RestServiceTest.UTC_DATE_WITH_MILLIS_FORMATTER.format(new Date(policyVersionFile.lastModified())));
		}

		return new IdReferenceType(newPolicy.getPolicySetId(), newPolicy.getVersion(), null, null);
	}

	/**
	 * Adds a given PolicySet and returns allocated resource ID. It also checks 2 things: 1) The response's PolicySet
	 * matches the input PolicySet (simply checking PolicySet IDs and versions) 2) The reponse to a getPolicySet() also
	 * matches the input PolicySet, to make sure the new PolicySet was actually committed succesfully and no error
	 * occurred in the process. 3) Response to getPolicyResources() with same PolicySetId and Version matches.
	 * <p>
	 * Returns created policy resource path segment
	 * 
	 * @throws ClientErrorException
	 *             with status code 409 when policy conflicts with existing one (same policy id and version)
	 */
	String testAddAndGetPolicy(PolicySet policySet) throws ClientErrorException
	{
		// put new policyset
		final Link link = domain.getPapResource().getPoliciesResource().addPolicy(policySet);
		final String policyHref = link.getHref();
		final String[] parts = policyHref.split("/");
		assertTrue(parts.length >= 2, "Link returned by addPolicy does not match pattern 'policyId/version'");
		final String policyResId = parts[0];
		final String versionResId = parts[1];

		// Check result was committed
		// check added policy link is in policies list
		PoliciesResource policiesRes = domain.getPapResource().getPoliciesResource();
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

	private IdReferenceType setRootPolicy(String policyId, String version)
	{
		PdpPropertiesResource propsRes = domain.getPapResource().getPdpPropertiesResource();
		IdReferenceType newRootPolicyRef = new IdReferenceType(policyId, version, null, null);
		propsRes.updateOtherPdpProperties(new PdpPropertiesUpdate(newRootPolicyRef));
		return newRootPolicyRef;
	}

	IdReferenceType setRootPolicy(PolicySet policySet, boolean ignoreVersion)
	{
		try
		{
			testAddAndGetPolicy(policySet);
		} catch (ClientErrorException e)
		{
			// If this is a conflict, it means it is already added, else something bad happened
			if (e.getResponse().getStatus() != Status.CONFLICT.getStatusCode())
			{
				throw e;
			}
		}

		return setRootPolicy(policySet.getPolicySetId(), ignoreVersion ? null : policySet.getVersion());
	}

	protected void resetPolicies() throws JAXBException
	{
		final JAXBElement<PolicySet> jaxbElt = unmarshal(new File(RestServiceTest.XACML_SAMPLES_DIR,
				RestServiceTest.TEST_DEFAULT_POLICYSET_FILENAME), PolicySet.class);
		final PolicySet newRootPolicySet = jaxbElt.getValue();
		final PoliciesResource policiesRes = domain.getPapResource().getPoliciesResource();
		setRootPolicy(newRootPolicySet, false);

		// Delete all other policies
		Resources policiesResources = policiesRes.getPolicies();
		for (Link link : policiesResources.getLinks())
		{
			String policyId = link.getHref();
			// skip if this is the root policy
			if (policyId.equals(newRootPolicySet.getPolicySetId()))
			{
				continue;
			}

			policiesRes.getPolicyResource(policyId).deletePolicy();
		}
	}

	protected <T> JAXBElement<T> unmarshal(File file, Class<T> clazz) throws JAXBException
	{
		return unmarshaller.unmarshal(new StreamSource(file), clazz);
	}
}
