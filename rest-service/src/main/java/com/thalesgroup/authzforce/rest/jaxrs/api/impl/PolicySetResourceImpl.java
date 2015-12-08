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

import javax.validation.constraints.NotNull;
import javax.ws.rs.BadRequestException;
import javax.ws.rs.InternalServerErrorException;
import javax.xml.bind.JAXBException;

import com.thalesgroup.authzforce.api.jaxrs.PolicySetResource;
import com.thalesgroup.authzforce.rest.SecurityDomain;

import oasis.names.tc.xacml._3_0.core.schema.wd_17.PolicySet;

public class PolicySetResourceImpl implements PolicySetResource
{
	private final SecurityDomain policyDomain;


	/**
	 * Creates PolicySetResource
	 * 
	 * @param policyDomain domain to which the policySet applies
	 */
	public PolicySetResourceImpl(@NotNull SecurityDomain policyDomain)
	{		
		this.policyDomain = policyDomain;
	}

	/* (non-Javadoc)
	 * @see com.thalesgroup.authzforce.api.jaxrs.PolicySetResource#getPolicySet()
	 */
	@Override
	public PolicySet getPolicySet()
	{
		return this.policyDomain.getPolicySet();
	}
	
	
	/* (non-Javadoc)
	 * @see com.thalesgroup.authzforce.api.jaxrs.PolicySetResource#updatePolicySet(oasis.names.tc.xacml._2_0.policy.schema.os.PolicySet)
	 */
	@Override
	public PolicySet updatePolicySet(PolicySet policyset)
	{
		try
		{
			this.policyDomain.setPolicySet(policyset);
		} catch (IOException e)
		{
			throw new InternalServerErrorException(e);
		} catch (JAXBException e)
		{
			throw new BadRequestException(e);
		}
		return policyset;
	}

}
