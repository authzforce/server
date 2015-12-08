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

import javax.validation.constraints.NotNull;
import javax.ws.rs.InternalServerErrorException;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.Path;

import org.w3._2005.atom.Link;
import org.w3._2005.atom.Relation;

import com.sun.xacml.PDP;
import com.thalesgroup.authz.model._3_0.resource.Domain;
import com.thalesgroup.authz.model._3_0.resource.Properties;
import com.thalesgroup.authz.model._3_0.resource.Resources;
import com.thalesgroup.authzforce.api.jaxrs.EndUserDomain;
import com.thalesgroup.authzforce.api.jaxrs.Pap;
import com.thalesgroup.authzforce.api.jaxrs.Pdp;
import com.thalesgroup.authzforce.rest.SecurityDomain;
import com.thalesgroup.authzforce.rest.jaxrs.api.impl.EndUserDomainSetImpl.DomainEntry;


public class EndUserDomainImpl implements EndUserDomain
{
	private static final String GET_PAP_RESOURCE_METHOD_NAME = "getPap";
	private static final String GET_PDP_RESOURCE_METHOD_NAME = "getPdp";

	// private static final Logger LOGGER = LoggerFactory.getLogger(EndUserDomainImpl.class);
	private final DomainEntry domainEntry;

	/**
	 * Constructs end-user policy admin domain
	 * 
	 * @param domainEntry
	 *            entry of the domain in the global domain manager
	 */
	public EndUserDomainImpl(@NotNull DomainEntry domainEntry)
	{
		this.domainEntry = domainEntry;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.thalesgroup.authzforce.api.jaxrs.EndUserDomain#updateDomain(com.thalesgroup.authzforce
	 * .model.Properties)
	 */
	@Override
	public Properties updateDomain(Properties properties)
	{
		final SecurityDomain domain = this.domainEntry.getValue();
		domain.setProperties(properties);
		return properties;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.thalesgroup.authzforce.api.jaxrs.EndUserDomain#getDomain()
	 */
	@Override
	public Domain getDomain()
	{
		final Domain domain = new Domain();
		domain.setProperties(this.domainEntry.getValue().getProperties());

		// Links to child resources (pap, pdp)
		final Resources childResources = new Resources();
		domain.setChildResources(childResources);

		// PAP link
		final Link papLink = new Link();
		childResources.getLinks().add(papLink);
		// For the link, get Path annotation of getPap method
		final Path papResourcePath;
		try
		{
			papResourcePath = EndUserDomain.class.getDeclaredMethod(GET_PAP_RESOURCE_METHOD_NAME).getAnnotation(Path.class);
		} catch (SecurityException e)
		{
			throw new InternalServerErrorException(e);
		} catch (NoSuchMethodException e)
		{
			throw new InternalServerErrorException(e);
		}

		papLink.setHref(papResourcePath.value());
		papLink.setRel(Relation.ITEM);

		// PDP link
		final Link pdpLink = new Link();
		childResources.getLinks().add(pdpLink);
		// For the link, get Path annotation of getPap method
		final Path pdpResourcePath;
		try
		{
			pdpResourcePath = EndUserDomain.class.getDeclaredMethod(GET_PDP_RESOURCE_METHOD_NAME).getAnnotation(Path.class);
		} catch (SecurityException e)
		{
			throw new InternalServerErrorException(e);
		} catch (NoSuchMethodException e)
		{
			throw new InternalServerErrorException(e);
		}

		pdpLink.setHref(pdpResourcePath.value());
		pdpLink.setRel(Relation.ITEM);

		return domain;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.thalesgroup.authzforce.api.jaxrs.EndUserDomain#deleteDomain()
	 */
	@Override
	public Properties deleteDomain()
	{
		final Properties removedDomainProps = this.domainEntry.selfRemove();
		if (removedDomainProps == null)
		{
			// domain not managed anymore (e.g. removed by another thread)
			throw new NotFoundException();
		}

		return removedDomainProps;
	}

	@Override
	public Pap getPap()
	{
		final SecurityDomain domain = this.domainEntry.getValue();
		return new PapImpl(domain);
	}

	@Override
	public Pdp getPdp()
	{
		final PDP pdp = this.domainEntry.getValue().getPDP();
		return new PdpImpl(pdp);
	}
}
