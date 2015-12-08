/**
 * Copyright (C) 2012-2015 Thales Services SAS.
 *
 * This file is part of AuthZForce.
 *
 * AuthZForce is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * AuthZForce is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with AuthZForce.  If not, see <http://www.gnu.org/licenses/>.
 */
/**
 * 
 */
package com.thalesgroup.authzforce.rest.jaxrs.api.impl;

import java.beans.ConstructorProperties;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import javax.validation.constraints.NotNull;
import javax.ws.rs.InternalServerErrorException;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.core.Context;

import org.apache.cxf.jaxrs.ext.MessageContext;
import org.w3._2005.atom.Link;
import org.w3._2005.atom.Relation;

import com.thalesgroup.authz.model._3_0.resource.Properties;
import com.thalesgroup.authz.model._3_0.resource.Resources;
import com.thalesgroup.authzforce.api.jaxrs.EndUserDomain;
import com.thalesgroup.authzforce.api.jaxrs.EndUserDomainSet;
import com.thalesgroup.authzforce.rest.SecurityDomain;
import com.thalesgroup.authzforce.rest.SecurityDomainManager;

public class EndUserDomainSetImpl implements EndUserDomainSet
{
	@Context
	private MessageContext messageContext;

	private final SecurityDomainManager domainManager;

	private final String authorizedResourceAttrId;

	private final String anyResourceId;

	/**
	 * @param domainManager
	 *            security domain manager
	 * @param authorizedResourceAttribute
	 *            name of ServletRequest attribute expected to give the list of authorized resource
	 *            (<code>java.util.List</code>) IDs for the current user
	 * @param anyResourceId
	 *            identifier for "any resource" (access to any one)
	 */
	@ConstructorProperties({ "domainManager", "authorizedResourceAttribute", "anyResourceId" })
	public EndUserDomainSetImpl(@NotNull SecurityDomainManager domainManager, String authorizedResourceAttribute, String anyResourceId)
	{
		this.domainManager = domainManager;
		this.authorizedResourceAttrId = authorizedResourceAttribute;
		this.anyResourceId = anyResourceId;
	}

	/**
	 * @param domainManager
	 *            security domain manager
	 * @param authorizedResourceAttribute
	 *            name of ServletRequest attribute expected to give the list of authorized resource
	 *            (<code>java.util.List</code>) IDs for the current user
	 */
	@ConstructorProperties({ "domainManager", "authorizedResourceAttribute" })
	public EndUserDomainSetImpl(@NotNull SecurityDomainManager domainManager, String authorizedResourceAttribute)
	{
		this(domainManager, authorizedResourceAttribute, null);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.thalesgroup.authzforce.api.jaxrs.EndUserDomainSet#addDomain(com.thalesgroup.authzforce
	 * .model.Metadata)
	 */
	@Override
	public Link addDomain(Properties props)
	{
		final UUID domainId;
		try
		{
			domainId = domainManager.add(props);
			final Link link = new Link();
			link.setHref(domainId.toString());
			link.setRel(Relation.ITEM);
			return link;
		} catch (IOException e)
		{
			throw new InternalServerErrorException(e);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.thalesgroup.authzforce.api.jaxrs.EndUserDomainSet#getDomains()
	 */
	@Override
	public Resources getDomains()
	{
		// each sub-directory inside domainRootDir is a policy domain directory
		// add domain on the fly
		// rename to resourceCollection
		final Set<UUID> authorizedDomains = new HashSet<UUID>();
		final Object attrVal = messageContext.getHttpServletRequest().getAttribute(authorizedResourceAttrId);
		// attrVal may be null
		if (attrVal == null)
		{
			if (anyResourceId == null)
			{ // attrVal == anyResourceId
				authorizedDomains.addAll(domainManager.getDomainIds());
			}
		} else
		{
			if (attrVal instanceof List)
			{
				final List<?> resourceIds = (List<?>) attrVal;
				if (resourceIds.contains(anyResourceId))
				{
					authorizedDomains.addAll(domainManager.getDomainIds());
				} else
				{
					for (final Object resourceId : resourceIds)
					{
						final UUID domainId = UUID.fromString(resourceId.toString());
						if (domainManager.containsId(domainId))
						{
							authorizedDomains.add(domainId);
						}
					}
				}
			} else
			{
				throw new InternalServerErrorException(new IllegalArgumentException("Invalid type of value for ServletRequest attribute '"
						+ authorizedResourceAttrId + "' = " + attrVal + " used to specify autorized resource. Expected: java.util.List<String>"));
			}
		}

		final Resources domainSet = new Resources();
		for (final UUID domainId : authorizedDomains)
		{
			final Link link = new Link();
			domainSet.getLinks().add(link);
			link.setHref(domainId.toString());
			link.setRel(Relation.ITEM);
		}

		return domainSet;
	}

	/**
	 * Domain entry in the domain manager with operation to remove itself from the manager
	 * 
	 */
	public class DomainEntry
	{

		private final UUID domainId;

		/**
		 * Creates option
		 * 
		 * @param domainId
		 *            ID of domain that the created instance allows to remove from the domain
		 *            Manager
		 */
		public DomainEntry(UUID domainId)
		{
			this.domainId = domainId;
		}

		/**
		 * Remove oneself from the domain manager
		 * 
		 * @return domain properties, or null if this domain was not managed by/registered in the
		 *         domain manager anymore
		 */
		public Properties selfRemove()
		{
			try
			{
				return domainManager.remove(domainId);
			} catch (IOException e)
			{
				throw new InternalServerErrorException(e);
			}
		}

		/**
		 * @return value in domain entry, i.e. the managed domain object itself
		 */
		public SecurityDomain getValue()
		{
			return domainManager.get(domainId);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.thalesgroup.authzforce.api.jaxrs.EndUserDomainSet#getEndUserDomain(java.lang.String)
	 */
	@Override
	public EndUserDomain getEndUserDomain(String domainId)
	{
		final UUID domainUUID = UUID.fromString(domainId);
		if (!domainManager.containsId(domainUUID))
		{
			throw new NotFoundException();
		}
		
		return new EndUserDomainImpl(new DomainEntry(domainUUID));
	}
}
