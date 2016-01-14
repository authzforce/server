/**
 * Copyright (C) 2012-2016 Thales Services SAS.
 *
 * This file is part of AuthZForce CE.
 *
 * AuthZForce CE is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * AuthZForce CE is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with AuthZForce CE.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.ow2.authzforce.rest.service.jaxrs;

import oasis.names.tc.xacml._3_0.core.schema.wd_17.IdReferenceType;

import org.ow2.authzforce.core.pap.api.dao.WritableDomainProperties;
import org.ow2.authzforce.rest.api.xmlns.DomainProperties;

class WritableDomainPropertiesImpl implements WritableDomainProperties
{
	private final DomainProperties props;

	WritableDomainPropertiesImpl(DomainProperties props)
	{
		this.props = props;
	}

	@Override
	public String getExternalId()
	{
		return props.getExternalId();
	}

	@Override
	public String getDescription()
	{
		return props.getDescription();
	}

	@Override
	public IdReferenceType getRootPolicyRef()
	{
		return props.getRootPolicyRef();
	}

}