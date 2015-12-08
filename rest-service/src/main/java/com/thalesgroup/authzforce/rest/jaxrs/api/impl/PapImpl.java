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
import javax.ws.rs.Path;

import org.w3._2005.atom.Link;
import org.w3._2005.atom.Relation;

import com.thalesgroup.authz.model._3_0.resource.Resources;
import com.thalesgroup.authzforce.api.jaxrs.AttributeFinderList;
import com.thalesgroup.authzforce.api.jaxrs.Pap;
import com.thalesgroup.authzforce.api.jaxrs.PolicySetResource;
import com.thalesgroup.authzforce.api.jaxrs.RefPolicySetList;
import com.thalesgroup.authzforce.rest.SecurityDomain;


public class PapImpl implements Pap
{
	private static final String GET_POLICYSET_RESOURCE_METHOD_NAME = "getPolicySetResource";
	private static final String GET_ATTRIBUTE_FINDERS_RESOURCE_METHOD_NAME = "getAttributeFinderList";
	private static final String GET_REFPOLICYSETLIST_RESOURCE_METHOD_NAME = "getRefPolicySetList";

	private final SecurityDomain policyDomain;

	/**
	 * Creates implementation of the PAP API
	 * 
	 * @param policyDomain
	 *            policy domain
	 */
	public PapImpl(@NotNull SecurityDomain policyDomain)
	{
		this.policyDomain = policyDomain;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.thalesgroup.authzforce.api.jaxrs.EndUserDomain#getPolicySetResource()
	 */
	@Override
	public PolicySetResource getPolicySetResource()
	{
		return new PolicySetResourceImpl(policyDomain);
	}

	@Override
	public AttributeFinderList getAttributeFinderList()
	{
		return new AttributeFinderListImpl(policyDomain);
	}

	@Override
	public Resources getPAP()
	{
		final Resources childResources = new Resources();

		// Link to child resource 'policySet'
		final Link policySetLink = new Link();
		childResources.getLinks().add(policySetLink);
		// For the link, get Path annotation of getPolicySetResource method
		final Path policySetResourcePath;
		try
		{
			policySetResourcePath = Pap.class.getDeclaredMethod(GET_POLICYSET_RESOURCE_METHOD_NAME).getAnnotation(Path.class);
		} catch (SecurityException e)
		{
			throw new InternalServerErrorException(e);
		} catch (NoSuchMethodException e)
		{
			throw new InternalServerErrorException(e);
		}

		policySetLink.setHref(policySetResourcePath.value());
		policySetLink.setRel(Relation.ITEM);

		// Link to child resource 'refPolicySets'
		final Link refPolicySetsLink = new Link();
		childResources.getLinks().add(refPolicySetsLink);
		// For the link, get Path annotation of getRefPolicySetsResource method
		final Path refPolicySetsResourcePath;
		try
		{
			refPolicySetsResourcePath = Pap.class.getDeclaredMethod(GET_REFPOLICYSETLIST_RESOURCE_METHOD_NAME).getAnnotation(Path.class);
		} catch (SecurityException e)
		{
			throw new InternalServerErrorException(e);
		} catch (NoSuchMethodException e)
		{
			throw new InternalServerErrorException(e);
		}

		refPolicySetsLink.setHref(refPolicySetsResourcePath.value());
		refPolicySetsLink.setRel(Relation.ITEM);

		// Link to child resource 'attributeFinders'
		final Link attrFindersLink = new Link();
		childResources.getLinks().add(attrFindersLink);
		// For the link, get Path annotation of getPolicySetResource method
		final Path attrFindersResourcePath;
		try
		{
			attrFindersResourcePath = Pap.class.getDeclaredMethod(GET_ATTRIBUTE_FINDERS_RESOURCE_METHOD_NAME).getAnnotation(Path.class);
		} catch (SecurityException e)
		{
			throw new InternalServerErrorException(e);
		} catch (NoSuchMethodException e)
		{
			throw new InternalServerErrorException(e);
		}

		attrFindersLink.setHref(attrFindersResourcePath.value());
		attrFindersLink.setRel(Relation.ITEM);

		return childResources;
	}

	@Override
	public RefPolicySetList getRefPolicySetList()
	{
		return new RefPolicySetListImpl(policyDomain);
	}

}
