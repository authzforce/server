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
package org.ow2.authzforce.rest.service.jaxrs;

import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Set;
import java.util.TimeZone;

import javax.ws.rs.BadRequestException;
import javax.ws.rs.ClientErrorException;
import javax.ws.rs.ForbiddenException;
import javax.ws.rs.InternalServerErrorException;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.Path;
import javax.ws.rs.ServerErrorException;
import javax.ws.rs.core.Response.Status;
import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;

import oasis.names.tc.xacml._3_0.core.schema.wd_17.PolicySet;
import oasis.names.tc.xacml._3_0.core.schema.wd_17.Request;
import oasis.names.tc.xacml._3_0.core.schema.wd_17.Response;

import org.json.JSONObject;
import org.ow2.authzforce.core.pap.api.dao.DomainDao;
import org.ow2.authzforce.core.pap.api.dao.DomainDaoClient;
import org.ow2.authzforce.core.pap.api.dao.PdpFeature;
import org.ow2.authzforce.core.pap.api.dao.PolicyDaoClient;
import org.ow2.authzforce.core.pap.api.dao.PrpRwProperties;
import org.ow2.authzforce.core.pap.api.dao.ReadableDomainProperties;
import org.ow2.authzforce.core.pap.api.dao.ReadablePdpProperties;
import org.ow2.authzforce.core.pap.api.dao.TooManyPoliciesException;
import org.ow2.authzforce.core.pap.api.dao.WritablePdpProperties;
import org.ow2.authzforce.core.pdp.api.io.PdpEngineInoutAdapter;
import org.ow2.authzforce.rest.api.jaxrs.AttributeProvidersResource;
import org.ow2.authzforce.rest.api.jaxrs.DomainPropertiesResource;
import org.ow2.authzforce.rest.api.jaxrs.DomainResource;
import org.ow2.authzforce.rest.api.jaxrs.PapResource;
import org.ow2.authzforce.rest.api.jaxrs.PdpPropertiesResource;
import org.ow2.authzforce.rest.api.jaxrs.PdpResource;
import org.ow2.authzforce.rest.api.jaxrs.PoliciesResource;
import org.ow2.authzforce.rest.api.jaxrs.PolicyResource;
import org.ow2.authzforce.rest.api.jaxrs.PrpPropertiesResource;
import org.ow2.authzforce.rest.api.xmlns.ApplicablePolicies;
import org.ow2.authzforce.rest.api.xmlns.AttributeProviders;
import org.ow2.authzforce.rest.api.xmlns.Domain;
import org.ow2.authzforce.rest.api.xmlns.DomainProperties;
import org.ow2.authzforce.rest.api.xmlns.Feature;
import org.ow2.authzforce.rest.api.xmlns.PdpProperties;
import org.ow2.authzforce.rest.api.xmlns.PdpPropertiesUpdate;
import org.ow2.authzforce.rest.api.xmlns.PrpProperties;
import org.ow2.authzforce.rest.api.xmlns.ResourceContent;
import org.ow2.authzforce.rest.api.xmlns.Resources;
import org.ow2.authzforce.xmlns.pdp.ext.AbstractAttributeProvider;
import org.w3._2005.atom.Link;
import org.w3._2005.atom.Relation;

import com.google.common.escape.Escaper;
import com.google.common.net.UrlEscapers;

/**
 * Domain Resource implementation. Each domain managed by {@link DomainsResourceImpl} is an instance of this class.
 * 
 * @param <DAO>
 *            Domain DAO implementation class
 *
 */
public class DomainResourceImpl<DAO extends DomainDao<PolicyVersionResourceImpl, PolicyResourceImpl>> implements DomainDaoClient<DAO>, DomainResource, DomainPropertiesResource, PapResource,
		PdpResource, PoliciesResource, AttributeProvidersResource, PdpPropertiesResource, PrpPropertiesResource
{
	/**
	 * Escapes strings so they can be safely included in URL path segments
	 */
	public static final Escaper URL_PATH_SEGMENT_ESCAPER = UrlEscapers.urlPathSegmentEscaper();

	private static final InternalServerErrorException NULL_PDP_INTERNAL_SERVER_ERROR_EXCEPTION = new InternalServerErrorException(
			"PDP is in erroneous state. Please contact the domain or system administrator.");
	private static final ClientErrorException ADD_POLICY_CONFLICT_EXCEPTION = new ClientErrorException("PolicySet already exists with same PolicySetId and Version", Status.CONFLICT);
	private static final NotFoundException NOT_FOUND_EXCEPTION = new NotFoundException();
	private static final BadRequestException INVALID_ARG_BAD_REQUEST_EXCEPTION = new BadRequestException("Invalid argument");

	private static final DatatypeFactory XML_DATATYPE_FACTORY;
	static
	{
		try
		{
			XML_DATATYPE_FACTORY = DatatypeFactory.newInstance();
		}
		catch (final DatatypeConfigurationException e)
		{
			throw new RuntimeException(e);
		}
	}

	private static final TimeZone UTC_TZ = TimeZone.getTimeZone("UTC");

	private static final String GET_PROPERTIES_RESOURCE_METHOD_NAME = "getDomainPropertiesResource";
	private static final String GET_PAP_RESOURCE_METHOD_NAME = "getPapResource";
	private static final String GET_PDP_PROPERTIES_RESOURCE_METHOD_NAME = "getPdpPropertiesResource";
	private static final String GET_PRP_PROPERTIES_RESOURCE_METHOD_NAME = "getPrpPropertiesResource";
	private static final String GET_PDP_RESOURCE_METHOD_NAME = "getPdpResource";
	private static final String GET_POLICIES_RESOURCE_METHOD_NAME = "getPoliciesResource";
	private static final String GET_ATTRIBUTE_PROVIDERS_RESOURCE_METHOD_NAME = "getAttributeProvidersResource";

	/**
	 * DomainResource Factory
	 * 
	 * @param <DOMAIN_DAO>
	 *            Domain DAO
	 * 
	 *
	 */
	public static class Factory<DOMAIN_DAO extends DomainDao<PolicyVersionResourceImpl, PolicyResourceImpl>> implements
			DomainDaoClient.Factory<PolicyVersionResourceImpl, PolicyResourceImpl, DOMAIN_DAO, DomainResourceImpl<DOMAIN_DAO>>
	{
		private static final IllegalArgumentException ILLEGAL_DOMAIN_ID_ARGUMENT_EXCEPTION = new IllegalArgumentException("Domain ID for domain resource undefined");
		private static final IllegalArgumentException ILLEGAL_DOMAIN_DAO_ARGUMENT_EXCEPTION = new IllegalArgumentException("Domain DAO for domain resource undefined");

		@Override
		public DomainResourceImpl<DOMAIN_DAO> getInstance(final String domainId, final DOMAIN_DAO domainDAO)
		{
			if (domainId == null)
			{
				throw ILLEGAL_DOMAIN_ID_ARGUMENT_EXCEPTION;
			}

			if (domainDAO == null)
			{
				throw ILLEGAL_DOMAIN_DAO_ARGUMENT_EXCEPTION;
			}

			return new DomainResourceImpl<>(domainId, domainDAO);
		}

		@Override
		public PolicyDaoClient.Factory<PolicyVersionResourceImpl, PolicyResourceImpl> getPolicyDaoClientFactory()
		{
			return PolicyResourceImpl.FACTORY;
		}

	}

	private final String domainId;
	private final DAO domainDAO;

	private DomainResourceImpl(final String domainId, final DAO domainDAO)
	{
		assert domainDAO != null;
		this.domainId = domainId;
		this.domainDAO = domainDAO;
	}

	@Override
	public Domain getDomain()
	{
		// Links to child resources (properties, pap, pdp)
		// domain properties link
		final Link propsLink = new Link();
		// For the link, get Path annotation of corresponding method
		final Path propsResourcePath;
		try
		{
			propsResourcePath = DomainResource.class.getDeclaredMethod(GET_PROPERTIES_RESOURCE_METHOD_NAME).getAnnotation(Path.class);
		}
		catch (SecurityException | NoSuchMethodException e)
		{
			throw new InternalServerErrorException("Error getting the 'properties' resource of domain '" + domainId + "'", e);
		}

		propsLink.setHref(propsResourcePath.value());
		propsLink.setTitle("Domain properties");
		propsLink.setRel(Relation.ITEM);

		// PAP link
		final Link papLink = new Link();
		// For the link, get Path annotation of getPap method
		final Path papResourcePath;
		try
		{
			papResourcePath = DomainResource.class.getDeclaredMethod(GET_PAP_RESOURCE_METHOD_NAME).getAnnotation(Path.class);
		}
		catch (SecurityException | NoSuchMethodException e)
		{
			throw new InternalServerErrorException("Error getting the 'pap' resource of domain '" + domainId + "'", e);
		}

		papLink.setHref(papResourcePath.value());
		papLink.setTitle("Policy Administration Point");
		papLink.setRel(Relation.ITEM);

		// PDP link
		final Link pdpLink = new Link();
		// For the link, get Path annotation of getPap method
		final Path pdpResourcePath;
		try
		{
			pdpResourcePath = DomainResource.class.getDeclaredMethod(GET_PDP_RESOURCE_METHOD_NAME).getAnnotation(Path.class);
		}
		catch (SecurityException | NoSuchMethodException e)
		{
			throw new InternalServerErrorException("Error getting the 'pdp' resource of domain '" + domainId + "'", e);
		}

		pdpLink.setHref(pdpResourcePath.value());
		pdpLink.setTitle("Policy Decision Point");

		/*
		 * Conformance with test assertion 'urn:oasis:names:tc:xacml:3.0:profile:rest:assertion:home:pdp' of REST Profile of XACML v3.0 Version 1.0:
		 * http://docs.oasis-open.org/xacml/xacml-rest/v1.0/cs02/xacml-rest-v1.0-cs02.html#_Toc399235433. Example:
		 * http://docs.oasis-open.org/xacml/xacml-rest/v1.0/cs02/xacml-rest-v1.0-cs02.html#_Toc399235419
		 */
		pdpLink.setRel(Relation.HTTP_DOCS_OASIS_OPEN_ORG_NS_XACML_RELATION_PDP);

		final Resources childResources = new Resources(Arrays.asList(propsLink, papLink, pdpLink));
		final ReadableDomainProperties props;
		try
		{
			props = domainDAO.getDomainProperties();
		}
		catch (final IOException e)
		{
			throw new InternalServerErrorException("Error getting the properties of domain '" + domainId + "'", e);
		}

		if (props == null)
		{
			// domain not managed anymore (e.g. removed by another thread)
			throw NOT_FOUND_EXCEPTION;
		}

		return new Domain(new DomainProperties(props.getDescription(), props.getExternalId()), childResources);
	}

	@Override
	public DomainProperties deleteDomain()
	{
		final ReadableDomainProperties props;
		try
		{
			props = domainDAO.removeDomain();
		}
		catch (final IOException e)
		{
			throw new InternalServerErrorException("Error removing the domain '" + domainId + "'", e);
		}

		if (props == null)
		{
			// domain not managed anymore (e.g. removed by another thread)
			throw NOT_FOUND_EXCEPTION;
		}

		return new DomainProperties(props.getDescription(), props.getExternalId());
	}

	@Override
	public PapResource getPapResource()
	{

		return this;
	}

	@Override
	public PdpResource getPdpResource()
	{
		return this;
	}

	@Override
	public DomainPropertiesResource getDomainPropertiesResource()
	{
		return this;
	}

	@Override
	public Response requestPolicyDecision(final Request request)
	{
		final PdpEngineInoutAdapter<Request, Response> pdp = domainDAO.getXacmlJaxbPdp();
		if (pdp == null)
		{
			throw NULL_PDP_INTERNAL_SERVER_ERROR_EXCEPTION;
		}

		return pdp.evaluate(request);
	}

	@Override
	public JSONObject requestPolicyDecisionXacmlJson(final JSONObject request)
	{
		final PdpEngineInoutAdapter<JSONObject, JSONObject> pdp = domainDAO.getXacmlJsonPdp();
		if (pdp == null)
		{
			throw NULL_PDP_INTERNAL_SERVER_ERROR_EXCEPTION;
		}

		return pdp.evaluate(request);
	}

	@Override
	public ResourceContent getPAP()
	{
		if (!domainDAO.isPapEnabled())
		{
			throw new ServerErrorException("PAP disabled", Status.NOT_IMPLEMENTED);
		}

		// Link to child resource 'pdp.properties'
		final Link pdpPropsLink = new Link();
		// For the link, get Path annotation of corresponding method
		final Path pdpPropsResourcePath;
		try
		{
			pdpPropsResourcePath = PapResource.class.getDeclaredMethod(GET_PDP_PROPERTIES_RESOURCE_METHOD_NAME).getAnnotation(Path.class);
		}
		catch (SecurityException | NoSuchMethodException e)
		{
			throw new InternalServerErrorException("Error getting the 'pdp.properties' resource of the domain '" + domainId + "'", e);
		}

		pdpPropsLink.setHref(pdpPropsResourcePath.value());
		pdpPropsLink.setTitle("PDP properties");
		pdpPropsLink.setRel(Relation.ITEM);

		// Link to child resource 'prp.properties'
		final Link prpPropsLink = new Link();
		// For the link, get Path annotation of corresponding method
		final Path prpPropsResourcePath;
		try
		{
			prpPropsResourcePath = PapResource.class.getDeclaredMethod(GET_PRP_PROPERTIES_RESOURCE_METHOD_NAME).getAnnotation(Path.class);
		}
		catch (SecurityException | NoSuchMethodException e)
		{
			throw new InternalServerErrorException("Error getting the 'prp.properties' resource of the domain '" + domainId + "'", e);
		}

		prpPropsLink.setHref(prpPropsResourcePath.value());
		prpPropsLink.setTitle("PRP properties");
		prpPropsLink.setRel(Relation.ITEM);

		// Link to child resource 'policies'
		final Link policiesLink = new Link();
		// For the link, get Path annotation of getPoliciesResource method
		final Path policiesResourcePath;
		try
		{
			policiesResourcePath = PapResource.class.getDeclaredMethod(GET_POLICIES_RESOURCE_METHOD_NAME).getAnnotation(Path.class);
		}
		catch (SecurityException | NoSuchMethodException e)
		{
			throw new InternalServerErrorException("Error getting the 'policies' resource of the domain '" + domainId + "'", e);
		}

		policiesLink.setHref(policiesResourcePath.value());
		policiesLink.setTitle("PRP policies");
		policiesLink.setRel(Relation.ITEM);

		// Link to child resource 'attributeProviders'
		final Link attrProvidersLink = new Link();
		// For the link, get Path annotation of getAttributeProvidersResource
		// method
		final Path attrProvidersResourcePath;
		try
		{
			attrProvidersResourcePath = PapResource.class.getDeclaredMethod(GET_ATTRIBUTE_PROVIDERS_RESOURCE_METHOD_NAME).getAnnotation(Path.class);
		}
		catch (SecurityException | NoSuchMethodException e)
		{
			throw new InternalServerErrorException("Error getting the 'attributeProviders' resource of the domain '" + domainId + "'", e);
		}

		attrProvidersLink.setHref(attrProvidersResourcePath.value());
		attrProvidersLink.setTitle("PDP Attribute Providers");
		attrProvidersLink.setRel(Relation.ITEM);

		final Resources childResources = new Resources(Arrays.asList(pdpPropsLink, prpPropsLink, policiesLink, attrProvidersLink));
		return new ResourceContent(null, childResources);
	}

	@Override
	public PoliciesResource getPoliciesResource()
	{
		return this;
	}

	@Override
	public AttributeProvidersResource getAttributeProvidersResource()
	{
		return this;
	}

	@Override
	public DomainProperties getDomainProperties()
	{
		final ReadableDomainProperties props;
		try
		{
			props = domainDAO.getDomainProperties();
		}
		catch (final IOException e)
		{
			throw new InternalServerErrorException("Error getting the properties of domain '" + domainId + "'", e);
		}

		if (props == null)
		{
			// domain not managed anymore (e.g. removed by another thread)
			throw NOT_FOUND_EXCEPTION;
		}

		return new DomainProperties(props.getDescription(), props.getExternalId());
	}

	@Override
	public DomainProperties updateDomainProperties(final DomainProperties properties)
	{
		if (properties == null)
		{
			throw INVALID_ARG_BAD_REQUEST_EXCEPTION;
		}

		final WritableDomainPropertiesImpl newProps = new WritableDomainPropertiesImpl(properties);
		try
		{
			domainDAO.setDomainProperties(newProps);
		}
		catch (final IOException e)
		{
			throw new InternalServerErrorException("Error updating the properties of domain '" + domainId + "'", e);
		}
		catch (final IllegalArgumentException e)
		{
			throw new BadRequestException(e);
		}

		return properties;
	}

	@Override
	public AttributeProviders getAttributeProviderList()
	{
		final List<AbstractAttributeProvider> attributeProviders;
		try
		{
			attributeProviders = domainDAO.getAttributeProviders();
		}
		catch (final IOException e)
		{
			throw new InternalServerErrorException("Error getting the attributeProviders configuration of domain '" + domainId + "'", e);
		}

		return new AttributeProviders(attributeProviders);
	}

	@Override
	public AttributeProviders updateAttributeProviderList(final AttributeProviders attributeproviders)
	{
		if (attributeproviders == null)
		{
			throw INVALID_ARG_BAD_REQUEST_EXCEPTION;
		}

		try
		{
			domainDAO.setAttributeProviders(attributeproviders.getAttributeProviders());
		}
		catch (final IOException e)
		{
			throw new InternalServerErrorException("Error updating the attributeProviders configuration of domain '" + domainId + "'", e);
		}
		catch (final IllegalArgumentException e)
		{
			throw new BadRequestException(e);
		}

		return attributeproviders;
	}

	@Override
	public Link addPolicy(final PolicySet policy)
	{
		if (policy == null)
		{
			throw INVALID_ARG_BAD_REQUEST_EXCEPTION;
		}

		final PolicySet conflictingPolicy;
		try
		{
			conflictingPolicy = domainDAO.addPolicy(policy);
		}
		catch (final IOException e)
		{
			throw new InternalServerErrorException("Error adding policy to domain '" + domainId + "'", e);
		}
		catch (final IllegalArgumentException e)
		{
			throw new BadRequestException(e);
		}
		catch (final TooManyPoliciesException e)
		{
			throw new ForbiddenException(e);
		}

		if (conflictingPolicy != null)
		{
			throw ADD_POLICY_CONFLICT_EXCEPTION;
		}

		// Policy ID is xs:anyURI, therefore may contain invalid characters for
		// URL paths -> needs escaping to be used as URL path segment
		final String policyIdUrlPathSegment = URL_PATH_SEGMENT_ESCAPER.escape(policy.getPolicySetId());
		final Link policyResourceLink = new Link();
		policyResourceLink.setHref(policyIdUrlPathSegment + "/" + policy.getVersion());
		policyResourceLink.setTitle("Policy '" + policy.getPolicySetId() + "' v" + policy.getVersion());
		policyResourceLink.setRel(Relation.ITEM);
		return policyResourceLink;
	}

	@Override
	public PolicyResource getPolicyResource(final String policyId)
	{
		if (policyId == null)
		{
			throw INVALID_ARG_BAD_REQUEST_EXCEPTION;
		}

		final PolicyResource policyRes = domainDAO.getPolicyDaoClient(policyId);
		if (policyRes == null)
		{
			throw NOT_FOUND_EXCEPTION;
		}

		return policyRes;
	}

	@Override
	public Resources getPolicies()
	{
		final Set<String> policyResourceIDs;
		try
		{
			policyResourceIDs = domainDAO.getPolicyIdentifiers();
		}
		catch (final IOException e)
		{
			throw new InternalServerErrorException("Error getting policy resource IDs in domain '" + domainId + "'", e);
		}

		if (policyResourceIDs.size() < 1)
		{
			throw new InternalServerErrorException("Missing root policy resource from DAO in domain '" + domainId + "'");
		}

		final List<Link> policyResourceLinks = new ArrayList<>(policyResourceIDs.size());
		for (final String policyResourceId : policyResourceIDs)
		{
			final Link link = new Link();
			policyResourceLinks.add(link);
			link.setHref(policyResourceId);
			link.setRel(Relation.ITEM);
		}

		return new Resources(policyResourceLinks);
	}

	@Override
	public DAO getDao()
	{
		return domainDAO;
	}

	@Override
	public PdpPropertiesResource getPdpPropertiesResource()
	{
		return this;
	}

	@Override
	public PdpProperties getOtherPdpProperties()
	{
		final ReadablePdpProperties props;
		try
		{
			props = domainDAO.getOtherPdpProperties();
		}
		catch (final IOException e)
		{
			throw new InternalServerErrorException("Error getting the properties of the PDP of domain '" + domainId + "'", e);
		}

		if (props == null)
		{
			// domain not managed anymore (e.g. removed by another thread)
			throw NOT_FOUND_EXCEPTION;
		}

		final GregorianCalendar cal = new GregorianCalendar(UTC_TZ);
		cal.setTimeInMillis(props.getLastModified());

		final List<PdpFeature> pdpFeatures = props.getFeatures();
		final List<Feature> features = new ArrayList<>(pdpFeatures.size());
		for (final PdpFeature pdpFeature : pdpFeatures)
		{
			features.add(new Feature(pdpFeature.getId(), pdpFeature.getType(), pdpFeature.isEnabled()));
		}
		return new PdpProperties(features, props.getRootPolicyRefExpression(), new ApplicablePolicies(props.getApplicableRootPolicyRef(), props.getApplicableRefPolicyRefs()),
				XML_DATATYPE_FACTORY.newXMLGregorianCalendar(cal));
	}

	@Override
	public PdpProperties updateOtherPdpProperties(final PdpPropertiesUpdate properties)
	{
		if (properties == null)
		{
			throw INVALID_ARG_BAD_REQUEST_EXCEPTION;
		}

		final WritablePdpProperties propsUpdate = new WritablePdpPropertiesImpl(properties);
		final ReadablePdpProperties allProps;
		try
		{
			allProps = domainDAO.setOtherPdpProperties(propsUpdate);
		}
		catch (final IOException e)
		{
			throw new InternalServerErrorException("Error updating the properties of the PDP of domain '" + domainId + "'", e);
		}
		catch (final IllegalArgumentException e)
		{
			throw new BadRequestException(e);
		}

		final GregorianCalendar cal = new GregorianCalendar(UTC_TZ);
		cal.setTimeInMillis(allProps.getLastModified());

		final List<PdpFeature> allPdpFeatures = allProps.getFeatures();
		final List<Feature> allFeatures = new ArrayList<>(allPdpFeatures.size());
		for (final PdpFeature pdpFeature : allPdpFeatures)
		{
			allFeatures.add(new Feature(pdpFeature.getId(), pdpFeature.getType(), pdpFeature.isEnabled()));
		}
		return new PdpProperties(allFeatures, allProps.getRootPolicyRefExpression(), new ApplicablePolicies(allProps.getApplicableRootPolicyRef(), allProps.getApplicableRefPolicyRefs()),
				XML_DATATYPE_FACTORY.newXMLGregorianCalendar(cal));
	}

	@Override
	public PrpPropertiesResource getPrpPropertiesResource()
	{
		return this;
	}

	@Override
	public PrpProperties updateOtherPrpProperties(final PrpProperties properties)
	{
		if (properties == null)
		{
			throw INVALID_ARG_BAD_REQUEST_EXCEPTION;
		}

		final PrpRwProperties propsUpdate = new PrpRWPropertiesImpl(properties);
		final PrpRwProperties allProps;
		try
		{
			allProps = domainDAO.setOtherPrpProperties(propsUpdate);
		}
		catch (final IOException e)
		{
			throw new InternalServerErrorException("Error updating the properties of the PRP of domain '" + domainId + "'", e);
		}
		catch (final IllegalArgumentException e)
		{
			throw new BadRequestException(e);
		}

		final int maxPolicyCount = allProps.getMaxPolicyCountPerDomain();
		final BigInteger mpc = maxPolicyCount > 0 ? BigInteger.valueOf(maxPolicyCount) : null;
		final int maxVersionCount = allProps.getMaxVersionCountPerPolicy();
		final BigInteger mvc = maxVersionCount > 0 ? BigInteger.valueOf(maxVersionCount) : null;
		return new PrpProperties(mpc, mvc, allProps.isVersionRollingEnabled());
	}

	@Override
	public PrpProperties getOtherPrpProperties()
	{
		final PrpRwProperties props;
		try
		{
			props = domainDAO.getOtherPrpProperties();
		}
		catch (final IOException e)
		{
			throw new InternalServerErrorException("Error getting the properties of the PRP of domain '" + domainId + "'", e);
		}

		if (props == null)
		{
			// domain not managed anymore (e.g. removed by another thread)
			throw NOT_FOUND_EXCEPTION;
		}

		final int maxPolicyCount = props.getMaxPolicyCountPerDomain();
		final BigInteger mpc = maxPolicyCount > 0 ? BigInteger.valueOf(maxPolicyCount) : null;
		final int maxVersionCount = props.getMaxVersionCountPerPolicy();
		final BigInteger mvc = maxVersionCount > 0 ? BigInteger.valueOf(maxVersionCount) : null;
		return new PrpProperties(mpc, mvc, props.isVersionRollingEnabled());
	}

}