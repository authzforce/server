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

import com.sun.xml.fastinfoset.tools.FI_SAX_XML;
import com.sun.xml.fastinfoset.tools.XML_SAX_FI;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ClientErrorException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.Response.Status;
import jakarta.xml.bind.JAXBElement;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Marshaller;
import jakarta.xml.bind.Unmarshaller;
import oasis.names.tc.xacml._3_0.core.schema.wd_17.IdReferenceType;
import oasis.names.tc.xacml._3_0.core.schema.wd_17.PolicySet;
import oasis.names.tc.xacml._3_0.core.schema.wd_17.Request;
import oasis.names.tc.xacml._3_0.core.schema.wd_17.Response;
import org.apache.cxf.jaxrs.client.WebClient;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.ow2.authzforce.core.pdp.api.XmlUtils;
import org.ow2.authzforce.core.pdp.api.io.XacmlJaxbParsingUtils;
import org.ow2.authzforce.core.pdp.impl.PdpModelHandler;
import org.ow2.authzforce.core.pdp.testutil.TestUtils;
import org.ow2.authzforce.core.xmlns.pdp.Pdp;
import org.ow2.authzforce.core.xmlns.pdp.TopLevelPolicyElementRef;
import org.ow2.authzforce.pap.dao.flatfile.FlatFileDAOUtils;
import org.ow2.authzforce.rest.api.jaxrs.*;
import org.ow2.authzforce.rest.api.xmlns.*;
import org.ow2.authzforce.xacml.Xacml3JaxbHelper;
import org.ow2.authzforce.xacml.json.model.XacmlJsonUtils;
import org.ow2.authzforce.xmlns.pdp.ext.AbstractAttributeProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.w3._2005.atom.Link;
import org.xml.sax.InputSource;

import javax.xml.transform.stream.StreamSource;
import java.io.*;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

import static org.testng.Assert.*;

class DomainAPIHelper
{
    private static final UnsupportedOperationException UNSUPPORTED_READ_WRITE_PDP_CONF_FILE_OPERATION_EXCEPTION = new UnsupportedOperationException(
            "Operation not supported with undefined pdpModelHandler");

    private static final Logger LOGGER = LoggerFactory.getLogger(DomainAPIHelper.class);

    private final Unmarshaller restApiEntityUnmarshaller;

    private final String domainId;
    private final DomainResource domain;
    private final File domainPropertiesFile;

    private final File domainPDPConfFile;

    private final File domainPoliciesDirName;

    private final PdpModelHandler pdpModelHandler;

    private final Unmarshaller xacmlUnmarshaller;

    protected DomainAPIHelper(final String domainId, final DomainResource domain, final Unmarshaller apiXmlUnmarshaller, final PdpModelHandler pdpModelHandler) throws JAXBException
    {
        this.domainId = domainId;
        this.domain = domain;
        final File testDomainDir = new File(RestServiceTest.DOMAINS_DIR, domainId);
        this.domainPropertiesFile = new File(testDomainDir, RestServiceTest.DOMAIN_PROPERTIES_FILENAME);
        this.domainPDPConfFile = new File(testDomainDir, RestServiceTest.DOMAIN_PDP_CONF_FILENAME);
        this.domainPoliciesDirName = new File(testDomainDir, RestServiceTest.DOMAIN_POLICIES_DIRNAME);
        this.restApiEntityUnmarshaller = apiXmlUnmarshaller;
        this.xacmlUnmarshaller = Xacml3JaxbHelper.createXacml3Unmarshaller();
        this.pdpModelHandler = pdpModelHandler;
    }

    protected static void matchPolicySets(final PolicySet actual, final PolicySet expected, final String testedMethodId)
    {
        assertEquals(actual.getPolicySetId(), expected.getPolicySetId(),
                String.format("Actual PolicySetId (='%s') from %s() != expected PolicySetId (='%s')", actual.getPolicySetId(), testedMethodId, expected.getPolicySetId()));
        assertEquals(actual.getVersion(), expected.getVersion(),
                String.format("Actual PolicySet Version (='%s') from %s() != expected PolicySet Version (='%s')", actual.getVersion(), testedMethodId, expected.getVersion()));
    }

    /**
     * Get link with href matching a given href
     *
     * @param hrefToBeMatched
     *            href to be matched
     * @param links
     *            links where to look for the matching link
     * @return matching link, null if none
     *
     */
    protected static Link getMatchingLink(final String hrefToBeMatched, final List<Link> links)
    {
        for (final Link link : links)
        {
            if (link.getHref().equals(hrefToBeMatched))
            {
                return link;
            }
        }

        return null;
    }

    protected void resetPdpAndPrp(final List<Feature> pdpFeaturesToEnable) throws JAXBException
    {
        final JAXBElement<PolicySet> jaxbElt = unmarshalXacml(new File(RestServiceTest.XACML_SAMPLES_DIR, RestServiceTest.TEST_DEFAULT_POLICYSET_FILENAME), PolicySet.class);
        final PolicySet newRootPolicySet = jaxbElt.getValue();
        final PoliciesResource policiesRes = domain.getPapResource().getPoliciesResource();

        try
        {
            policiesRes.addPolicy(newRootPolicySet);
        } catch (final ClientErrorException e)
        {
            // If this is a conflict, it means it is already added, else something bad happened
            if (e.getResponse().getStatus() != Status.CONFLICT.getStatusCode())
            {
                throw e;
            }
        }

        final PdpPropertiesResource propsRes = domain.getPapResource().getPdpPropertiesResource();
        final IdReferenceType newRootPolicyRef = new IdReferenceType(newRootPolicySet.getPolicySetId(), null, null, null);
        propsRes.updateOtherPdpProperties(new PdpPropertiesUpdate(pdpFeaturesToEnable, newRootPolicyRef));

        // Delete all other policies
        final Resources policiesResources = policiesRes.getPolicies();
        for (final Link link : policiesResources.getLinks())
        {
            final String policyId = link.getHref();
            // skip if this is the root policy
            if (policyId.equals(newRootPolicySet.getPolicySetId()))
            {
                continue;
            }

            policiesRes.getPolicyResource(policyId).deletePolicy();
        }
    }

    protected void resetPdpAndPrp() throws JAXBException
    {
        resetPdpAndPrp(Collections.emptyList());
    }

    protected <T> JAXBElement<T> unmarshalXacml(final File file, final Class<T> clazz) throws JAXBException
    {
        return xacmlUnmarshaller.unmarshal(new StreamSource(file), clazz);
    }

    protected <T> JAXBElement<T> unmarshalRestApiEntity(final File file, final Class<T> clazz) throws JAXBException
    {
        return restApiEntityUnmarshaller.unmarshal(new StreamSource(file), clazz);
    }

    protected void modifyDomainPropertiesFile(final String newExternalId, final boolean isFilesystemLegacy) throws InterruptedException, JAXBException
    {
        /*
         * If filesystem is legacy, it means here that file timestamp (mtime in particular) resolution is 1 sec, i.e. milliseconds are rounded to zero always. Therefore, in this unit test, with such a
         * filesystem, the file modification does undetected because it occurs in less than 1 sec after the last PDP sync/reload. See also:
         * http://www.coderanch.com/t/384700/java/java/File-lastModified-windows-linux
         *
         * So if a legacy filesystem mode is enabled, we wait at least 1 sec before updating the file, so that the file's mtime is different and the change detected a result on such legacy.
         *
         * NOTE: ext3 has only second resolution (therefore considered legacy), whereas ext4 has nanosecond resolution. If you have resolution higher than the millisecond in Java (microsec, nanosec),
         * e.g. with ext4, JAVA 8 is also required: http://bugs.java.com/view_bug.do?bug_id=6939260 (A possible workaround would record the last-modified-time in all monitored files. We consider it
         * not worth the trouble since ext4 is now the default option on modern Linux distributions such Ubuntu.)
         */

        // test sync with properties file
        final DomainProperties props = domain.getDomainPropertiesResource().getDomainProperties();
        final org.ow2.authzforce.pap.dao.flatfile.xmlns.DomainProperties newProps = new org.ow2.authzforce.pap.dao.flatfile.xmlns.DomainProperties(props.getDescription(), newExternalId, null, null,
                false);
        /*
         * Wait at least 1 sec before updating the file, if filesystem is "legacy" (file timestamp limited to second resolution) as explained above; otherwise the change remains unseen
         */
        if (isFilesystemLegacy)
        {
            Thread.sleep(1000);
        }

        RestServiceTest.JAXB_CTX.createMarshaller().marshal(newProps, domainPropertiesFile);
        if (LOGGER.isDebugEnabled())
        {
            LOGGER.debug("Updated externalId in file '{}' - lastModifiedTime = {}", domainPropertiesFile,
                    RestServiceTest.UTC_DATE_WITH_MILLIS_FORMATTER.format(new Date(domainPropertiesFile.lastModified())));
        }
    }

    /**
     *
     * @param isFilesystemLegacy is the filesystem "legacy", i.e. file timestamps are limited to second-precision resolution
     * @return new root policy reference
     * @throws JAXBException JAVA-XML (JAXB) conversion issue
     * @throws InterruptedException legacy filesystem issue
     */
    protected IdReferenceType modifyRootPolicyRefInPdpConfFile(final boolean isFilesystemLegacy) throws JAXBException, InterruptedException
    {
        if (pdpModelHandler == null)
        {
            throw UNSUPPORTED_READ_WRITE_PDP_CONF_FILE_OPERATION_EXCEPTION;
        }

        resetPdpAndPrp();

        final JAXBElement<PolicySet> jaxbPolicy = unmarshalXacml(new File(RestServiceTest.XACML_IIIG301_PDP_TEST_DIR, RestServiceTest.TEST_POLICY_FILENAME), PolicySet.class);
        final PolicySet policy = jaxbPolicy.getValue();
        testAddAndGetPolicy(policy);

        // change root policyref in PDP conf file
        final Pdp pdpConf = pdpModelHandler.unmarshal(new StreamSource(domainPDPConfFile), Pdp.class);
        final TopLevelPolicyElementRef newRootPolicyRef = new TopLevelPolicyElementRef(policy.getPolicySetId(), policy.getVersion(), true);
        final Pdp newPdpConf = new Pdp(pdpConf.getAttributeDatatypes(), pdpConf.getFunctions(), pdpConf.getCombiningAlgorithms(), pdpConf.getAttributeProviders(), pdpConf.getPolicyProviders(), newRootPolicyRef, pdpConf.getDecisionCache(), pdpConf.getIoProcChains(), pdpConf.getVersion(), pdpConf.isStandardDatatypesEnabled(), pdpConf.isStandardFunctionsEnabled(), pdpConf.isStandardCombiningAlgorithmsEnabled(), pdpConf.isStandardAttributeProvidersEnabled(), pdpConf.isXPathEnabled(), pdpConf.isStrictAttributeIssuerMatch(), pdpConf.getMaxIntegerValue(), pdpConf.getMaxVariableRefDepth(), pdpConf.getMaxPolicyRefDepth(), pdpConf.getClientRequestErrorVerbosityLevel() );
        /*
         * Wait at least 1 sec before updating the file, if filesystem is "legacy" (file timestamp limited to second resolution), to make sure the lastModifiedTime will be actually changed after
         * updating
         */
        if (isFilesystemLegacy)
        {
            Thread.sleep(1000);
        }

        pdpModelHandler.marshal(newPdpConf, domainPDPConfFile);
        return new IdReferenceType(policy.getPolicySetId(), policy.getVersion(), null, null);
    }

    protected void modifyMaxPolicyRefDepthInPdpConfFile(final boolean isFilesystemLegacy, final int maxPolicyRefDepth) throws JAXBException, InterruptedException
    {
        if (pdpModelHandler == null)
        {
            throw UNSUPPORTED_READ_WRITE_PDP_CONF_FILE_OPERATION_EXCEPTION;
        }

        resetPdpAndPrp();

        // change maxPolicyRefDepth in PDP conf file
        final Pdp pdpConf = pdpModelHandler.unmarshal(new StreamSource(domainPDPConfFile), Pdp.class);
        final Pdp newPdpConf = new Pdp(pdpConf.getAttributeDatatypes(), pdpConf.getFunctions(), pdpConf.getCombiningAlgorithms(), pdpConf.getAttributeProviders(), pdpConf.getPolicyProviders(), pdpConf.getRootPolicyRef(), pdpConf.getDecisionCache(), pdpConf.getIoProcChains(), pdpConf.getVersion(), pdpConf.isStandardDatatypesEnabled(), pdpConf.isStandardFunctionsEnabled(), pdpConf.isStandardCombiningAlgorithmsEnabled(), pdpConf.isStandardAttributeProvidersEnabled(), pdpConf.isXPathEnabled(), pdpConf.isStrictAttributeIssuerMatch(), pdpConf.getMaxIntegerValue(), pdpConf.getMaxVariableRefDepth(), BigInteger.valueOf(maxPolicyRefDepth), pdpConf.getClientRequestErrorVerbosityLevel() );
        /*
         * Wait at least 1 sec before updating the file, if filesystem is "legacy" (file timestamp limited to second resolution), to make sure the lastModifiedTime will be actually changed after
         * updating
         */
        if (isFilesystemLegacy)
        {
            Thread.sleep(1000);
        }

        pdpModelHandler.marshal(newPdpConf, domainPDPConfFile);
    }

    /**
     * Updates either the root policy or another policy referenced from root in the backend policy repository (i.e. filesystem)
     *
     * @param oldRootPolicyFile
     *            root policy to be updated if {@code updateRoot}
     * @param oldRefPolicyFile
     *            referenced policy to be updated if {@code !updateRoot}
     * @param updateRoot
     *            true iff we update root, else update the ref policy
     * @param isFileSystemLegacy is the filesystem "legacy", i.e. file timestamps are limited to second-precision resolution
     * @return reference to new policy from update
     * @throws JAXBException JAVA-XML conversion issue (JAXB)
     * @throws InterruptedException legacy filesystem issue
     */
    protected IdReferenceType addRootPolicyWithRefAndUpdate(final File oldRootPolicyFile, final File oldRefPolicyFile, final boolean updateRoot, final boolean isFileSystemLegacy)
            throws JAXBException, InterruptedException
    {
        resetPdpAndPrp();

        final JAXBElement<PolicySet> jaxbElt = unmarshalXacml(oldRefPolicyFile, PolicySet.class);
        final PolicySet refPolicySet = jaxbElt.getValue();
        testAddAndGetPolicy(refPolicySet);

        // Set root policy referencing ref policy above
        final JAXBElement<PolicySet> jaxbElt2 = unmarshalXacml(oldRootPolicyFile, PolicySet.class);
        final PolicySet rootPolicySetWithRef = jaxbElt2.getValue();
        // Add the policy and point the rootPolicyRef to new policy with refs to
        // instantiate it as root policy (validate, etc.)
        setRootPolicy(rootPolicySetWithRef, true);

        final PolicySet oldPolicyToUpdate = updateRoot ? rootPolicySetWithRef : refPolicySet;

        // update policy version on disk (we add ".1" to old version to have later version)
        final PolicySet newPolicy = new PolicySet(oldPolicyToUpdate.getDescription(), oldPolicyToUpdate.getPolicyIssuer(), oldPolicyToUpdate.getPolicySetDefaults(), oldPolicyToUpdate.getTarget(),
                oldPolicyToUpdate.getPolicySetsAndPoliciesAndPolicySetIdReferences(), oldPolicyToUpdate.getObligationExpressions(), oldPolicyToUpdate.getAdviceExpressions(),
                oldPolicyToUpdate.getPolicySetId(), oldPolicyToUpdate.getVersion() + ".1", oldPolicyToUpdate.getPolicyCombiningAlgId(), oldPolicyToUpdate.getMaxDelegationDepth());
        final Marshaller marshaller = RestServiceTest.JAXB_CTX.createMarshaller();
        final File policyDir = new File(domainPoliciesDirName, FlatFileDAOUtils.base64UrlEncode(oldPolicyToUpdate.getPolicySetId()));
        final File policyVersionFile = new File(policyDir, newPolicy.getVersion() + ".xml");
        /*
         * Wait at least 1 sec before updating the file, if filesystem is "legacy" (file timestamp limited to second resolution), to make sure the lastModifiedTime will be actually changed after
         * updating
         */
        if (isFileSystemLegacy)
        {
            Thread.sleep(1000);
        }

        marshaller.marshal(newPolicy, policyVersionFile);
        if (LOGGER.isDebugEnabled())
        {
            LOGGER.debug("Updated directory of ref-policy '{}' with file '{}' - lastModifiedTime = {}", newPolicy.getPolicySetId(), policyVersionFile,
                    RestServiceTest.UTC_DATE_WITH_MILLIS_FORMATTER.format(new Date(policyVersionFile.lastModified())));
        }

        return new IdReferenceType(newPolicy.getPolicySetId(), newPolicy.getVersion(), null, null);
    }

    /**
     *
     * @param policyId Policy(Set)Id
     * @return the policy matching {@code policyId} and {@code version}
     * @throws NotFoundException
     *             if no such policy exists
     */
    protected PolicySet getPolicy(final String policyId, final String version) throws NotFoundException
    {
        final PolicyResource policyRes = domain.getPapResource().getPoliciesResource().getPolicyResource(policyId);
        if (policyRes == null)
        {
            return null;
        }

        return policyRes.getPolicyVersionResource(version).getPolicyVersion();
    }

    /**
     * Adds a given PolicySet and returns allocated resource ID. It also checks 2 things: 1) The response's PolicySet matches the input PolicySet (simply checking PolicySet IDs and versions) 2) The
     * reponse to a getPolicySet() also matches the input PolicySet, to make sure the new PolicySet was actually committed succesfully and no error occurred in the process. 3) Response to
     * getPolicyResources() with same PolicySetId and Version matches.
     * <p>
     * Returns created policy resource path segment
     *
     * @throws ClientErrorException
     *             with status code 409 when policy conflicts with existing one (same policy id and version)
     */
    protected String testAddAndGetPolicy(final PolicySet policySet) throws ClientErrorException
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
        final PoliciesResource policiesRes = domain.getPapResource().getPoliciesResource();
        assertNotNull(getMatchingLink(policyResId, policiesRes.getPolicies().getLinks()), "Added policy resource link not found in links returned by getPoliciesResource()");

        // check added policy version is in policy versions list
        final PolicyResource policyRes = policiesRes.getPolicyResource(policyResId);
        final Resources policyVersionsResources = policyRes.getPolicyVersions();
        assertNotNull(getMatchingLink(versionResId, policyVersionsResources.getLinks()), "Added policy version resource link not found in links returned by getPolicyVersions()");

        // check PolicySet of added policy id/version is actually the one we
        // added
        final PolicySet getRespPolicySet = policyRes.getPolicyVersionResource(versionResId).getPolicyVersion();
        matchPolicySets(getRespPolicySet, policySet, "getPolicy");

        return policyResId;
    }

    private IdReferenceType setRootPolicy(final String policyId, final String version)
    {
        final PdpPropertiesResource propsRes = domain.getPapResource().getPdpPropertiesResource();
        final PdpProperties oldProps = propsRes.getOtherPdpProperties();
        final IdReferenceType newRootPolicyRef = new IdReferenceType(policyId, version, null, null);
        propsRes.updateOtherPdpProperties(new PdpPropertiesUpdate(oldProps.getFeatures(), newRootPolicyRef));
        return newRootPolicyRef;
    }

    protected IdReferenceType setRootPolicy(final PolicySet policySet, final boolean ignoreVersion)
    {
        try
        {
            domain.getPapResource().getPoliciesResource().addPolicy(policySet);
        } catch (final ClientErrorException e)
        {
            // If this is a conflict, it means it is already added, else something bad happened
            if (e.getResponse().getStatus() != Status.CONFLICT.getStatusCode())
            {
                throw e;
            }
        }

        return setRootPolicy(policySet.getPolicySetId(), ignoreVersion ? null : policySet.getVersion());
    }

    protected IdReferenceType setRootPolicy(final String rawUnparsedXacmlPolicy, String policyId, String policyVersion, WebClient httpClient, boolean enableFastInfoset) throws Exception
    {
        assert rawUnparsedXacmlPolicy != null && policyId != null && policyVersion != null && httpClient != null;

        final String returnedRawPolicySet;
        if(enableFastInfoset) {
            final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            new XML_SAX_FI().convert(new StringReader(rawUnparsedXacmlPolicy), byteArrayOutputStream);
            httpClient.reset().path("domains").path(domainId).path("pap").path("policies").type("application/fastinfoset").accept("application/fastinfoset").post(byteArrayOutputStream.toByteArray());
            final byte[] returnedFastInfosetBytes = httpClient.reset().path("domains").path(domainId).path("pap").path("policies").path(policyId).path(policyVersion).accept("application/fastinfoset").get(byte[].class);
            final ByteArrayOutputStream rawUnparsedPolicySetXmlOutputStream = new ByteArrayOutputStream();
            new FI_SAX_XML().parse(new ByteArrayInputStream(returnedFastInfosetBytes), rawUnparsedPolicySetXmlOutputStream);
            returnedRawPolicySet = rawUnparsedPolicySetXmlOutputStream.toString(StandardCharsets.UTF_8);
        } else {
            httpClient.reset().path("domains").path(domainId).path("pap").path("policies").type("application/xml").accept("application/xml").post(rawUnparsedXacmlPolicy);
            returnedRawPolicySet = httpClient.reset().path("domains").path(domainId).path("pap").path("policies").path(policyId).path(policyVersion).accept("application/xml").get(String.class);
        }

        // compare input raw and returned raw PolicySet to make sure XPath namespace contexts are preserved
        final XmlUtils.XmlnsFilteringParserFactory xacmlParserFactory = XacmlJaxbParsingUtils.getXacmlParserFactory(true);
        final XmlUtils.XmlnsFilteringParser xacmlParser = xacmlParserFactory.getInstance();
        xacmlParser.parse(new InputSource(new StringReader(rawUnparsedXacmlPolicy)));

        final XmlUtils.XmlnsFilteringParser returnedXacmlParser = xacmlParserFactory.getInstance();
        returnedXacmlParser.parse(new InputSource(new StringReader(returnedRawPolicySet)));

        assertTrue(returnedXacmlParser.getNamespacePrefixUriMap().entrySet().containsAll(xacmlParser.getNamespacePrefixUriMap().entrySet()));

        return setRootPolicy(policyId, policyVersion);
    }

    protected Pdp getPdpConfFromFile() throws JAXBException
    {
        if (pdpModelHandler == null)
        {
            throw UNSUPPORTED_READ_WRITE_PDP_CONF_FILE_OPERATION_EXCEPTION;
        }

        // change maxPolicyRefDepth in PDP conf file
        return pdpModelHandler.unmarshal(new StreamSource(domainPDPConfFile), Pdp.class);
    }

    protected PdpPropertiesResource updatePdpFeatures(final List<Feature> features)
    {
        final PdpPropertiesResource propsRes = domain.getPapResource().getPdpPropertiesResource();
        final PdpProperties oldProps = propsRes.getOtherPdpProperties();
        propsRes.updateOtherPdpProperties(new PdpPropertiesUpdate(features, oldProps.getRootPolicyRefExpression()));
        return propsRes;
    }

    protected List<Feature> updateAndGetPdpFeatures(final List<Feature> features)
    {
        final PdpPropertiesResource propsRes = updatePdpFeatures(features);
        return propsRes.getOtherPdpProperties().getFeatures();
    }

    public int updateAndGetMaxPolicyCount(final int maxPolicyCount)
    {
        final PrpPropertiesResource prpPropsRes = domain.getPapResource().getPrpPropertiesResource();
        final PrpProperties oldPrpProps = prpPropsRes.getOtherPrpProperties();
        prpPropsRes.updateOtherPrpProperties(
                new PrpProperties(maxPolicyCount > 0 ? BigInteger.valueOf(maxPolicyCount) : null, oldPrpProps.getMaxVersionCountPerPolicy(), oldPrpProps.isVersionRollingEnabled()));
        return prpPropsRes.getOtherPrpProperties().getMaxPolicyCount().intValue();
    }

    public PrpProperties updateMaxPolicyCount(final int maxPolicyCount)
    {
        final PrpPropertiesResource prpPropsRes = domain.getPapResource().getPrpPropertiesResource();
        final PrpProperties oldPrpProps = prpPropsRes.getOtherPrpProperties();
        return prpPropsRes.updateOtherPrpProperties(
                new PrpProperties(maxPolicyCount > 0 ? BigInteger.valueOf(maxPolicyCount) : null, oldPrpProps.getMaxVersionCountPerPolicy(), oldPrpProps.isVersionRollingEnabled()));
    }

    public int updateAndGetMaxPolicyVersionCount(final int maxPolicyVersionCount)
    {
        final PrpPropertiesResource prpPropsRes = domain.getPapResource().getPrpPropertiesResource();
        final PrpProperties oldPrpProps = prpPropsRes.getOtherPrpProperties();
        prpPropsRes.updateOtherPrpProperties(
                new PrpProperties(oldPrpProps.getMaxPolicyCount(), maxPolicyVersionCount > 0 ? BigInteger.valueOf(maxPolicyVersionCount) : null, oldPrpProps.isVersionRollingEnabled()));
        return prpPropsRes.getOtherPrpProperties().getMaxVersionCountPerPolicy().intValue();
    }

    public PrpProperties updateVersioningProperties(final int maxPolicyVersionCount, final boolean versionRolling)
    {
        final PrpPropertiesResource prpPropsRes = domain.getPapResource().getPrpPropertiesResource();
        final PrpProperties oldPrpProps = prpPropsRes.getOtherPrpProperties();
        return prpPropsRes.updateOtherPrpProperties(new PrpProperties(oldPrpProps.getMaxPolicyCount(), maxPolicyVersionCount > 0 ? BigInteger.valueOf(maxPolicyVersionCount) : null, versionRolling));
    }

    public boolean setPolicyVersionRollingAndGetStatus(final boolean isEnabled)
    {
        final PrpPropertiesResource prpPropsRes = domain.getPapResource().getPrpPropertiesResource();
        final PrpProperties oldPrpProps = prpPropsRes.getOtherPrpProperties();
        prpPropsRes.updateOtherPrpProperties(new PrpProperties(oldPrpProps.getMaxPolicyCount(), oldPrpProps.getMaxVersionCountPerPolicy(), isEnabled));
        return prpPropsRes.getOtherPrpProperties().isVersionRollingEnabled();
    }

    private static void assertNormalizedXacmlJaxbResponseEquals(final Response expectedResponse, final Response actualResponseFromPDP)
    {
        // normalize responses for comparison
        final Response normalizedActualResponse = TestUtils.normalizeForComparison(actualResponseFromPDP, true, true);
        final Response normalizedExpectedResponse = TestUtils.normalizeForComparison(expectedResponse, true, true);
        assertEquals(normalizedActualResponse, normalizedExpectedResponse, "Actual and expected XACML/XML responses don't match (Status elements removed/ignored for comparison)");
    }

    private static void assertNormalizedXacmlJsonResponseEquals(final JSONObject expectedResponse, final JSONObject actualResponseFromPDP)
    {
        // normalize responses for comparison
        final JSONObject normalizedActualResponse = XacmlJsonUtils.canonicalizeResponse(actualResponseFromPDP);
        final JSONObject normalizedExpectedResponse = XacmlJsonUtils.canonicalizeResponse(expectedResponse);
        Assert.assertTrue(normalizedActualResponse.similar(normalizedExpectedResponse), "Actual and expected XACML/JSON responses don't match (Status elements removed/ignored for comparison)");
    }

    private interface PdpRequestTest
    {
        void test() throws JAXBException, IOException;
    }

    private void requestPDP(final File testDirectory, final List<Feature> pdpFeaturesToEnable, final boolean isPdpRemote, Optional<WebClient> httpClientForSendingRawUnparsedPolicy, boolean enableFastInfoset, final PdpRequestTest pdpReqTest) throws Exception
    {
        if (pdpFeaturesToEnable != null)
        {
            resetPdpAndPrp(pdpFeaturesToEnable);
        }

        LOGGER.debug("Starting PDP test of directory '{}'", testDirectory);

        final File attributeProviderFile = new File(testDirectory, RestServiceTest.TEST_ATTRIBUTE_PROVIDER_FILENAME);
        if (attributeProviderFile.exists())
        {
            // replace old with new attribute providers
            // reset attribute providers
            domain.getPapResource().getAttributeProvidersResource().updateAttributeProviderList(new AttributeProviders(Collections.emptyList()));

            /*
             * This requires Attribute Provider extension to be deployed/configured in advance on the PDP. If we are testing a remote PDP, this may not be done, in which case we would get error 400
             * when trying to use it; so we skip this test in this case.
             */
            if (isPdpRemote)
            {
                return;
            }

            final JAXBElement<AbstractAttributeProvider> jaxbElt = unmarshalRestApiEntity(attributeProviderFile, AbstractAttributeProvider.class);
            domain.getPapResource().getAttributeProvidersResource().updateAttributeProviderList(new AttributeProviders(Collections.singletonList(jaxbElt.getValue())));
        }

        final File rootPolicyFile = new File(testDirectory, RestServiceTest.TEST_POLICY_FILENAME);
        /*
         * Change the root policy (and possible policyRefs) on the domain only if a root policy file exists in the directory, else we just test request/response on the existing policy in the domain
         */
        if (rootPolicyFile.exists())
        {
            final File refPoliciesDir = new File(testDirectory, RestServiceTest.TEST_REF_POLICIES_DIRECTORY_NAME);
            if (refPoliciesDir.exists() && refPoliciesDir.isDirectory())
            {
                for (final File policyFile : Objects.requireNonNull(refPoliciesDir.listFiles()))
                {
                    if (policyFile.isFile())
                    {
                        final JAXBElement<PolicySet> policy = unmarshalXacml(policyFile, PolicySet.class);
                        testAddAndGetPolicy(policy.getValue());
                        LOGGER.debug("Added policy from file: " + policyFile);
                    }
                }
            }

            final JAXBElement<PolicySet> rootPolicy = unmarshalXacml(rootPolicyFile, PolicySet.class);
            if(httpClientForSendingRawUnparsedPolicy.isEmpty())
            {
                // Add the policy and point the rootPolicyRef to new policy with refs to
                // instantiate it as root policy (validate, etc.)
                setRootPolicy(rootPolicy.getValue(), true);
            } else {
                final String rawUnparsedPolicy = Files.readString(rootPolicyFile.toPath());
                final PolicySet policySet = rootPolicy.getValue();
                setRootPolicy(rawUnparsedPolicy, policySet.getPolicySetId(), policySet.getVersion(), httpClientForSendingRawUnparsedPolicy.get(), enableFastInfoset);
            }
        }

        pdpReqTest.test();
    }

    /**
     *
     * @param testDirectory test resources directory
     * @param pdpFeaturesToEnable
     *            if and only if not null, reset policies and PDP with these features enabled
     * @param isPdpRemote id the PDP remote (RESTful API)
     * @throws JAXBException JAXB error
     * @throws IOException I/O error
     */
    protected void requestXacmlXmlPDP(final File testDirectory, final List<Feature> pdpFeaturesToEnable, final boolean isPdpRemote, Optional<WebClient> httpClientForSendingRawUnparsedPolicy, boolean enableFastInfoset) throws Exception
    {
        requestPDP(testDirectory, pdpFeaturesToEnable,  isPdpRemote, httpClientForSendingRawUnparsedPolicy, enableFastInfoset, () ->
        {
            final JAXBElement<Request> xacmlReq = unmarshalXacml(new File(testDirectory, RestServiceTest.REQUEST_FILENAME), Request.class);
            final JAXBElement<Response> expectedResponse = unmarshalXacml(new File(testDirectory, RestServiceTest.EXPECTED_RESPONSE_FILENAME), Response.class);
            final Response actualResponse = domain.getPdpResource().requestPolicyDecision(xacmlReq.getValue());
            assertNormalizedXacmlJaxbResponseEquals(expectedResponse.getValue(), actualResponse);
        });
    }

    public void requestXacmlJsonPDP(final File testDirectory, final List<Feature> pdpFeaturesToEnable, final boolean isPdpRemote, final WebClient httpClient)
            throws Exception {
        requestXacmlJsonPDP(testDirectory, pdpFeaturesToEnable, isPdpRemote, httpClient, Optional.empty());
    }

    public void requestXacmlJsonPDP(final File testDirectory, final List<Feature> pdpFeaturesToEnable, final boolean isPdpRemote, final WebClient httpClient, Optional<String> customMediaType)
            throws Exception
    {
        requestPDP(testDirectory, pdpFeaturesToEnable, isPdpRemote, Optional.empty(), false, () ->
        {
            final File xacmlReqFile = new File(testDirectory, "request.json");
            final JSONObject xacmlReq;
            try (final InputStream inputStream = new FileInputStream(xacmlReqFile))
            {

                xacmlReq = new JSONObject(new JSONTokener(inputStream));
                if (!xacmlReq.has("Request"))
                {
                    throw new IllegalArgumentException("Invalid XACML JSON Request file: " + xacmlReqFile + ". Expected root key: \"Request\"");
                }

                XacmlJsonUtils.REQUEST_SCHEMA.validate(xacmlReq);
            }

            final File xacmlRespFile = new File(testDirectory, "response.json");
            final JSONObject expectedResponse;
            if (xacmlRespFile.exists())
            {
                try (InputStream inputStream = new FileInputStream(xacmlRespFile))
                {

                    expectedResponse = new JSONObject(new JSONTokener(inputStream));
                    if (!expectedResponse.has("Response"))
                    {
                        throw new IllegalArgumentException("Invalid XACML JSON Response file: " + expectedResponse + ". Expected root key: \"Response\"");
                    }

                    XacmlJsonUtils.RESPONSE_SCHEMA.validate(expectedResponse);
                }
            } else
            {
                expectedResponse = null;
                // No expected JSON response means we except a 40X error
            }

            final String mediaType = customMediaType.orElse("application/xacml+json");
            try
            {
                final JSONObject actualResponse = httpClient.reset().path("domains").path(domainId).path("pdp").type(mediaType).accept(mediaType).post(xacmlReq,
                        JSONObject.class);

                // final Response actualResponse = testDomainFI.getPdpResource().requestPolicyDecision(xacmlReq.getValue());

                assertNormalizedXacmlJsonResponseEquals(expectedResponse, actualResponse);
            } catch(BadRequestException ex) {
                LOGGER.debug("PDP response error", ex);
                assertNull(expectedResponse, "Actual response was not an error as expected");
            }
        });
    }

    public File getPropertiesFile()
    {
        return this.domainPropertiesFile;
    }

}
