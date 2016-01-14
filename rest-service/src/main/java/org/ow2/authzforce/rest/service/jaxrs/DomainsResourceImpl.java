/**
 * Copyright (C) 2012-2015 Thales Services SAS.
 *
 * This file is part of AuthZForce.
 *
 * AuthZForce is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * AuthZForce is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with AuthZForce. If not, see <http://www.gnu.org/licenses/>.
 */
/**
 * 
 */
package org.ow2.authzforce.rest.service.jaxrs;

import java.beans.ConstructorProperties;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.ws.rs.BadRequestException;
import javax.ws.rs.InternalServerErrorException;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.core.Context;

import org.apache.cxf.jaxrs.ext.MessageContext;
import org.ow2.authzforce.core.pap.api.dao.DomainsDAO;
import org.ow2.authzforce.rest.api.jaxrs.DomainResource;
import org.ow2.authzforce.rest.api.jaxrs.DomainsResource;
import org.ow2.authzforce.rest.api.xmlns.DomainProperties;
import org.ow2.authzforce.rest.api.xmlns.Resources;
import org.w3._2005.atom.Link;
import org.w3._2005.atom.Relation;

import com.google.common.escape.Escaper;
import com.google.common.net.UrlEscapers;

/**
 * Client/End-User-managed domains resource implementation
 *
 */
public class DomainsResourceImpl implements DomainsResource
{
	private static final NotFoundException NOT_FOUND_EXCEPTION = new NotFoundException();

	private static final BadRequestException INVALID_ARG_BAD_REQUEST_EXCEPTION = new BadRequestException("Invalid argument");

	private static final Escaper URL_PATH_SEGMENT_ESCAPER = UrlEscapers.urlPathSegmentEscaper();

	@Context
	private MessageContext messageContext;

	private final DomainsDAO<DomainResourceImpl<?>> domainRepo;

	private final String authorizedResourceAttrId;

	private final String anyResourceId;

	/**
	 * Constructor
	 * 
	 * @param domainsDAO
	 *            domain repository
	 * @param authorizedResourceAttribute
	 *            name of ServletRequest attribute expected to give the list of authorized resource (<code>java.util.List</code>) IDs for the current user
	 * @param anyResourceId
	 *            identifier for "any resource" (access to any one)
	 */
	@ConstructorProperties({ "domainsDAO", "authorizedResourceAttribute", "anyResourceId" })
	public DomainsResourceImpl(DomainsDAO<DomainResourceImpl<?>> domainsDAO, String authorizedResourceAttribute, String anyResourceId)
	{
		this.domainRepo = domainsDAO;
		this.authorizedResourceAttrId = authorizedResourceAttribute;
		this.anyResourceId = anyResourceId;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.thalesgroup.authzforce.api.jaxrs.EndUserDomainSet#addDomain(com.thalesgroup.authzforce .model.Metadata)
	 */
	@Override
	public Link addDomain(DomainProperties props)
	{
		if (props == null)
		{
			throw INVALID_ARG_BAD_REQUEST_EXCEPTION;
		}

		/*
		 * If the 'rootPolicyRef' element is missing, a default root policy must be automatically created for the domain by the domainsDAO and a corresponding
		 * rootPolicyRef set by the Service Provider of this API in the domain properties. If the 'rootPolicyRef' element is present, it assumes that the
		 * Service Provider of this API initializes the domain with a fixed set of policies, and the client knows about those policies and therefore how to set
		 * the 'rootPolicyRef' properly to match one of those pre-set policies. If the 'rootPolicyRef' does not match any, the domain creation request will be
		 * rejected.
		 */
		final String domainId;
		try
		{
			domainId = domainRepo.addDomain(new WritableDomainPropertiesImpl(props));
		} catch (IOException e)
		{
			throw new InternalServerErrorException(e);
		} catch (IllegalArgumentException e)
		{
			throw new BadRequestException(e);
		}

		final String encodedUrlPathSegment = URL_PATH_SEGMENT_ESCAPER.escape(domainId);
		final Link link = new Link();
		link.setHref(encodedUrlPathSegment);
		link.setRel(Relation.ITEM);
		link.setTitle(domainId);
		return link;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.thalesgroup.authzforce.api.jaxrs.EndUserDomainSet#getDomains()
	 */
	@Override
	public Resources getDomains(String externalId)
	{
		// each sub-directory inside domainRootDir is a policy domain directory
		// add domain on the fly
		// rename to resourceCollection
		final Set<String> authorizedDomainIDs = new HashSet<>();
		final Object attrVal = messageContext.getHttpServletRequest().getAttribute(authorizedResourceAttrId);
		// attrVal may be null
		if (attrVal == null)
		{
			if (anyResourceId == null)
			{ // attrVal == anyResourceId
				authorizedDomainIDs.addAll(domainRepo.getDomainIDs(externalId));
			}
		} else
		{
			if (attrVal instanceof List)
			{
				final List<?> resourceIds = (List<?>) attrVal;
				if (resourceIds.contains(anyResourceId))
				{
					authorizedDomainIDs.addAll(domainRepo.getDomainIDs(externalId));
				} else
				{
					for (final Object resourceId : resourceIds)
					{
						final String domainId = resourceId.toString();
						if (domainRepo.containsDomain(domainId))
						{
							authorizedDomainIDs.add(domainId);
						}
					}
				}
			} else
			{
				throw new InternalServerErrorException(new IllegalArgumentException("Invalid type of value for ServletRequest attribute '"
						+ authorizedResourceAttrId + "' = " + attrVal + " used to specify autorized resource. Expected: java.util.List<String>"));
			}
		}

		final List<Link> domainResourceLinks = new ArrayList<>(authorizedDomainIDs.size());
		for (final String domainId : authorizedDomainIDs)
		{
			final String encodedUrlPathSegment = URL_PATH_SEGMENT_ESCAPER.escape(domainId);
			final Link link = new Link();
			link.setHref(encodedUrlPathSegment);
			link.setRel(Relation.ITEM);
			link.setTitle(domainId);
			domainResourceLinks.add(link);
		}

		return new Resources(domainResourceLinks);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.thalesgroup.authzforce.api.jaxrs.EndUserDomainSet#getEndUserDomain(java.lang.String)
	 */
	@Override
	public DomainResource getDomainResource(String domainId)
	{
		if (domainId == null)
		{
			throw INVALID_ARG_BAD_REQUEST_EXCEPTION;
		}

		final DomainResource domainRes = domainRepo.getDomainDAOClient(domainId);
		if (domainRes == null)
		{
			throw NOT_FOUND_EXCEPTION;
		}

		return domainRes;
	}
}
