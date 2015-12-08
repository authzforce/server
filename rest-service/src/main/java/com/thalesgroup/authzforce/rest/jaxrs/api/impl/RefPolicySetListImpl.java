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

import java.io.IOException;

import javax.ws.rs.BadRequestException;
import javax.ws.rs.InternalServerErrorException;
import javax.xml.bind.JAXBException;

import com.thalesgroup.authz.model._3.PolicySets;
import com.thalesgroup.authzforce.api.jaxrs.RefPolicySetList;
import com.thalesgroup.authzforce.rest.SecurityDomain;

public class RefPolicySetListImpl implements RefPolicySetList
{

	private final SecurityDomain policyDomain;

	/**
	 * Create resource for accessing candidate PolicySets for PolicySetIdReference targets
	 * 
	 * @param policyDomain
	 */
	public RefPolicySetListImpl(SecurityDomain policyDomain)
	{
		this.policyDomain = policyDomain;
	}

	@Override
	public PolicySets getRefPolicySets()
	{
		return this.policyDomain.getRefPolicySets();
	}

	@Override
	public PolicySets updateRefPolicySets(PolicySets policysets)
	{
		try
		{
			this.policyDomain.setRefPolicySets(policysets);
		} catch (IOException e)
		{
			throw new InternalServerErrorException(e);
		} catch (JAXBException e)
		{
			throw new BadRequestException(e);
		} catch (IllegalArgumentException e) {
			throw new BadRequestException(e);
		}
		
		return policysets;
	}
	
}
