/**
 * Copyright (C) 2012-2016 Thales Services SAS.
 *
 * This file is part of AuthZForce CE.
 *
 * AuthZForce CE is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * AuthZForce CE is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with AuthZForce CE. If not, see <http://www.gnu.org/licenses/>.
 */
package org.ow2.authzforce.rest.service.jaxrs;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import javax.ws.rs.BadRequestException;
import javax.ws.rs.ClientErrorException;
import javax.ws.rs.ForbiddenException;
import javax.ws.rs.InternalServerErrorException;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.Path;

import oasis.names.tc.xacml._3_0.core.schema.wd_17.PolicySet;
import oasis.names.tc.xacml._3_0.core.schema.wd_17.Request;
import oasis.names.tc.xacml._3_0.core.schema.wd_17.Response;

import org.ow2.authzforce.core.pap.api.dao.DomainDAO;
import org.ow2.authzforce.core.pap.api.dao.DomainDAOClient;
import org.ow2.authzforce.core.pap.api.dao.PolicyDAOClient;
import org.ow2.authzforce.core.pap.api.dao.ReadableDomainProperties;
import org.ow2.authzforce.core.pap.api.dao.TooManyPoliciesException;
import org.ow2.authzforce.rest.api.jaxrs.AttributeProvidersResource;
import org.ow2.authzforce.rest.api.jaxrs.DomainPropertiesResource;
import org.ow2.authzforce.rest.api.jaxrs.DomainResource;
import org.ow2.authzforce.rest.api.jaxrs.PapResource;
import org.ow2.authzforce.rest.api.jaxrs.PdpResource;
import org.ow2.authzforce.rest.api.jaxrs.PoliciesResource;
import org.ow2.authzforce.rest.api.jaxrs.PolicyResource;
import org.ow2.authzforce.rest.api.xmlns.AttributeProviders;
import org.ow2.authzforce.rest.api.xmlns.Domain;
import org.ow2.authzforce.rest.api.xmlns.DomainProperties;
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
public class DomainResourceImpl<DAO extends DomainDAO<PolicyVersionResourceImpl, PolicyResourceImpl>> implements DomainDAOClient<DAO>, DomainResource,
		DomainPropertiesResource, PapResource, PdpResource, PoliciesResource, AttributeProvidersResource
{

	private static final ClientErrorException ADD_POLICY_CONFLICT_EXCEPTION = new ClientErrorException(
			"PolicySet already exists with same PolicySetId and Version", javax.ws.rs.core.Response.Status.CONFLICT);
	private static final NotFoundException NOT_FOUND_EXCEPTION = new NotFoundException();
	private static final Escaper URL_PATH_SEGMENT_ESCAPER = UrlEscapers.urlPathSegmentEscaper();
	private static final BadRequestException INVALID_ARG_BAD_REQUEST_EXCEPTION = new BadRequestException("Invalid argument");

	private static final String GET_PAP_RESOURCE_METHOD_NAME = "getPapResource";
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
	public static class Factory<DOMAIN_DAO extends DomainDAO<PolicyVersionResourceImpl, PolicyResourceImpl>> implements
			DomainDAOClient.Factory<PolicyVersionResourceImpl, PolicyResourceImpl, DOMAIN_DAO, DomainResourceImpl<DOMAIN_DAO>>
	{
		private static final IllegalArgumentException ILLEGAL_DOMAIN_ID_ARGUMENT_EXCEPTION = new IllegalArgumentException(
				"Domain ID for domain resource undefined");
		private static final IllegalArgumentException ILLEGAL_DOMAIN_DAO_ARGUMENT_EXCEPTION = new IllegalArgumentException(
				"Domain DAO for domain resource undefined");

		@Override
		public DomainResourceImpl<DOMAIN_DAO> getInstance(String domainId, DOMAIN_DAO domainDAO)
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
		public PolicyDAOClient.Factory<PolicyVersionResourceImpl, PolicyResourceImpl> getPolicyDAOClientFactory()
		{
			return PolicyResourceImpl.FACTORY;
		}

	}

	private final String domainId;
	private final DAO domainDAO;

	private DomainResourceImpl(String domainId, DAO domainDAO)
	{
		assert domainDAO != null;
		this.domainId = domainId;
		this.domainDAO = domainDAO;
	}

	@Override
	public Domain getDomain()
	{

		// Links to child resources (pap, pdp)
		// PAP link
		final Link papLink = new Link();
		// For the link, get Path annotation of getPap method
		final Path papResourcePath;
		try
		{
			papResourcePath = DomainResource.class.getDeclaredMethod(GET_PAP_RESOURCE_METHOD_NAME).getAnnotation(Path.class);
		} catch (SecurityException | NoSuchMethodException e)
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
		} catch (SecurityException | NoSuchMethodException e)
		{
			throw new InternalServerErrorException("Error getting the 'pdp' resource of domain '" + domainId + "'", e);
		}

		pdpLink.setHref(pdpResourcePath.value());
		pdpLink.setTitle("Policy Decision Point");
		pdpLink.setRel(Relation.ITEM);

		final Resources childResources = new Resources(Arrays.asList(papLink, pdpLink));
		final ReadableDomainProperties props;
		try
		{
			props = domainDAO.getDomainProperties();
		} catch (IOException e)
		{
			throw new InternalServerErrorException("Error getting the properties of domain '" + domainId + "'", e);
		}

		if (props == null)
		{
			// domain not managed anymore (e.g. removed by another thread)
			throw NOT_FOUND_EXCEPTION;
		}

		return new Domain(new DomainProperties(props.getDescription(), props.getExternalId(), props.getRootPolicyRef()), childResources);
	}

	@Override
	public DomainProperties deleteDomain()
	{
		final ReadableDomainProperties props;
		try
		{
			props = domainDAO.removeDomain();
		} catch (IOException e)
		{
			throw new InternalServerErrorException("Error removing the domain '" + domainId + "'", e);
		}

		if (props == null)
		{
			// domain not managed anymore (e.g. removed by another thread)
			throw NOT_FOUND_EXCEPTION;
		}

		return new DomainProperties(props.getDescription(), props.getExternalId(), props.getRootPolicyRef());
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
	public Response requestPolicyDecision(Request request)
	{
		return domainDAO.getPDP().evaluate(request);
	}

	@Override
	public ResourceContent getPAP()
	{

		// Link to child resource 'policies'
		final Link policiesLink = new Link();
		// For the link, get Path annotation of getPoliciesResource method
		final Path policiesResourcePath;
		try
		{
			policiesResourcePath = PapResource.class.getDeclaredMethod(GET_POLICIES_RESOURCE_METHOD_NAME).getAnnotation(Path.class);
		} catch (SecurityException | NoSuchMethodException e)
		{
			throw new InternalServerErrorException("Error getting the 'policies' resource of the domain '" + domainId + "'", e);
		}

		policiesLink.setHref(policiesResourcePath.value());
		policiesLink.setRel(Relation.ITEM);

		// Link to child resource 'attributeProviders'
		final Link attrProvidersLink = new Link();
		// For the link, get Path annotation of getAttributeProvidersResource method
		final Path attrProvidersResourcePath;
		try
		{
			attrProvidersResourcePath = PapResource.class.getDeclaredMethod(GET_ATTRIBUTE_PROVIDERS_RESOURCE_METHOD_NAME).getAnnotation(Path.class);
		} catch (SecurityException | NoSuchMethodException e)
		{
			throw new InternalServerErrorException("Error getting the 'attributeProviders' resource of the domain '" + domainId + "'", e);
		}

		attrProvidersLink.setHref(attrProvidersResourcePath.value());
		attrProvidersLink.setTitle("PDP Attribute Providers");
		attrProvidersLink.setRel(Relation.ITEM);

		final Resources childResources = new Resources(Arrays.asList(policiesLink, attrProvidersLink));
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
		} catch (IOException e)
		{
			throw new InternalServerErrorException("Error getting the properties of domain '" + domainId + "'", e);
		}

		if (props == null)
		{
			// domain not managed anymore (e.g. removed by another thread)
			throw NOT_FOUND_EXCEPTION;
		}

		return new DomainProperties(props.getDescription(), props.getExternalId(), props.getRootPolicyRef());
	}

	@Override
	public DomainProperties updateDomainProperties(DomainProperties properties)
	{
		if (properties == null)
		{
			throw INVALID_ARG_BAD_REQUEST_EXCEPTION;
		}

		final WritableDomainPropertiesImpl newProps = new WritableDomainPropertiesImpl(properties);
		try
		{
			domainDAO.setDomainProperties(newProps);
		} catch (IOException e)
		{
			throw new InternalServerErrorException("Error updating the properties of domain '" + domainId + "'", e);
		} catch (IllegalArgumentException e)
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
		} catch (IOException e)
		{
			throw new InternalServerErrorException("Error getting the attributeProviders configuration of domain '" + domainId + "'", e);
		}

		return new AttributeProviders(attributeProviders);
	}

	@Override
	public AttributeProviders updateAttributeProviderList(AttributeProviders attributeproviders)
	{
		if (attributeproviders == null)
		{
			throw INVALID_ARG_BAD_REQUEST_EXCEPTION;
		}

		try
		{
			domainDAO.setAttributeProviders(attributeproviders.getAttributeProviders());
		} catch (IOException e)
		{
			throw new InternalServerErrorException("Error updating the attributeProviders configuration of domain '" + domainId + "'", e);
		} catch (IllegalArgumentException e)
		{
			throw new BadRequestException(e);
		}

		return attributeproviders;
	}

	@Override
	public Link addPolicy(PolicySet policy)
	{
		if (policy == null)
		{
			throw INVALID_ARG_BAD_REQUEST_EXCEPTION;
		}

		final PolicySet conflictingPolicy;
		try
		{
			conflictingPolicy = domainDAO.addPolicy(policy);
		} catch (IOException e)
		{
			throw new InternalServerErrorException("Error adding policy to domain '" + domainId + "'", e);
		} catch (IllegalArgumentException e)
		{
			throw new BadRequestException(e);
		} catch (TooManyPoliciesException e)
		{
			throw new ForbiddenException(e);
		}

		if (conflictingPolicy != null)
		{
			throw ADD_POLICY_CONFLICT_EXCEPTION;
		}

		// Policy ID is xs:anyURI, therefore may contain invalid characters for URL paths -> needs escaping to be used as URL path segment
		final String policyIdUrlPathSegment = URL_PATH_SEGMENT_ESCAPER.escape(policy.getPolicySetId());
		final Link policyResourceLink = new Link();
		policyResourceLink.setHref(policyIdUrlPathSegment + "/" + policy.getVersion());
		policyResourceLink.setTitle("Policy '" + policy.getPolicySetId() + "' v" + policy.getVersion());
		policyResourceLink.setRel(Relation.ITEM);
		return policyResourceLink;
	}

	@Override
	public PolicyResource getPolicyResource(String policyId)
	{
		if (policyId == null)
		{
			throw INVALID_ARG_BAD_REQUEST_EXCEPTION;
		}

		final PolicyResource policyRes = domainDAO.getPolicyDAOClient(policyId);
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
			policyResourceIDs = domainDAO.getPolicyIDs();
		} catch (IOException e)
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
	public DAO getDAO()
	{
		return domainDAO;
	}

}