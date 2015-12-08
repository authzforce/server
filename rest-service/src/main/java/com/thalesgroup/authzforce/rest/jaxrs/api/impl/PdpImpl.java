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
package com.thalesgroup.authzforce.rest.jaxrs.api.impl;

import javax.validation.constraints.NotNull;

import com.sun.xacml.PDP;
import com.thalesgroup.authzforce.api.jaxrs.Pdp;

import oasis.names.tc.xacml._3_0.core.schema.wd_17.Request;
import oasis.names.tc.xacml._3_0.core.schema.wd_17.Response;

/**
 * EndUserDomainPDP implementation
 * 
 */
public class PdpImpl implements Pdp
{	
	private final PDP pdp;

	/**
	 * @param pdp domain PDP
	 */
	public PdpImpl(@NotNull PDP pdp)
	{
		this.pdp = pdp;
	}

	@Override
	public Response requestPolicyDecision(Request request) {
//		final ResponseCtx respCtx = pdp.evaluate(request);
//		// convert sunxacmlResp to JAXB Response type
//		final Response jaxbResponse = new Response();
//		jaxbResponse.getResults().addAll(respCtx.getResults());
		return  pdp.evaluate(request);
	}

}
