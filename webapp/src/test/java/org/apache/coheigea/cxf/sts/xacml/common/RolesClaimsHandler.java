/**
 * Copyright (C) 2012-2020 THALES.
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
package org.apache.coheigea.cxf.sts.xacml.common;

import java.util.ArrayList;
import java.util.List;

import org.apache.cxf.rt.security.claims.Claim;
import org.apache.cxf.rt.security.claims.ClaimCollection;
import org.apache.cxf.sts.claims.ClaimsHandler;
import org.apache.cxf.sts.claims.ClaimsParameters;
import org.apache.cxf.sts.claims.ProcessedClaim;
import org.apache.cxf.sts.claims.ProcessedClaimCollection;

/**
 * A ClaimsHandler implementation that works with Roles.
 */
public class RolesClaimsHandler implements ClaimsHandler
{

	public static final String ROLE = "http://schemas.xmlsoap.org/ws/2005/05/identity/claims/role";

	@Override
	public ProcessedClaimCollection retrieveClaimValues(final ClaimCollection claims, final ClaimsParameters parameters)
	{

		if (claims != null && claims.size() > 0)
		{
			final ProcessedClaimCollection claimCollection = new ProcessedClaimCollection();
			for (final Claim requestClaim : claims)
			{
				final ProcessedClaim claim = new ProcessedClaim();
				claim.setClaimType(requestClaim.getClaimType());
				if (ROLE.equals(requestClaim.getClaimType()))
				{
					claim.setIssuer("STS");
					if ("alice".equals(parameters.getPrincipal().getName()))
					{
						claim.addValue("boss");
						claim.addValue("employee");
					}
					else if ("bob".equals(parameters.getPrincipal().getName()))
					{
						claim.addValue("employee");
					}
				}
				claimCollection.add(claim);
			}
			return claimCollection;
		}
		return null;
	}

	@Override
	public List<String> getSupportedClaimTypes()
	{
		final List<String> list = new ArrayList<>();
		list.add(ROLE);
		return list;
	}

}
