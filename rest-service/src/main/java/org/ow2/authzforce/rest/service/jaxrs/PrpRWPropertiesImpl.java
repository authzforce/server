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

import java.math.BigInteger;

import org.ow2.authzforce.core.pap.api.dao.PrpRwProperties;
import org.ow2.authzforce.rest.api.xmlns.PrpProperties;

class PrpRWPropertiesImpl implements PrpRwProperties
{
	private final int maxPolicyCountPerDomain;
	private final int maxVersionCountPerPolicy;
	private final boolean isVersionRollingEnabled;

	PrpRWPropertiesImpl(final PrpProperties props)
	{
		final BigInteger maxPolicyCountProp = props.getMaxPolicyCount();
		this.maxPolicyCountPerDomain = maxPolicyCountProp == null ? -1 : maxPolicyCountProp.intValue();
		final BigInteger maxVersionCountProp = props.getMaxVersionCountPerPolicy();
		this.maxVersionCountPerPolicy = maxVersionCountProp == null ? -1 : maxVersionCountProp.intValue();
		this.isVersionRollingEnabled = props.isVersionRollingEnabled();
	}

	@Override
	public int getMaxPolicyCountPerDomain()
	{
		return this.maxPolicyCountPerDomain;
	}

	@Override
	public int getMaxVersionCountPerPolicy()
	{
		return this.maxVersionCountPerPolicy;
	}

	@Override
	public boolean isVersionRollingEnabled()
	{
		return this.isVersionRollingEnabled;
	}

}