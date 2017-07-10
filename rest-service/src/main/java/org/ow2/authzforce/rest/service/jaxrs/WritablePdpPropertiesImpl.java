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

import java.util.ArrayList;
import java.util.List;

import oasis.names.tc.xacml._3_0.core.schema.wd_17.IdReferenceType;

import org.ow2.authzforce.core.pap.api.dao.PdpFeature;
import org.ow2.authzforce.core.pap.api.dao.WritablePdpProperties;
import org.ow2.authzforce.rest.api.xmlns.Feature;
import org.ow2.authzforce.rest.api.xmlns.PdpPropertiesUpdate;

class WritablePdpPropertiesImpl implements WritablePdpProperties
{
	private final IdReferenceType rootPolicyRefExpression;
	private final List<PdpFeature> features;

	WritablePdpPropertiesImpl(PdpPropertiesUpdate props)
	{
		this.rootPolicyRefExpression = props.getRootPolicyRefExpression();
		final List<Feature> inputFeatures = props.getFeatures();
		this.features = new ArrayList<>(inputFeatures.size());
		for (final Feature inputFeature : inputFeatures)
		{
			this.features.add(new PdpFeature(inputFeature.getValue(), inputFeature.getType(), inputFeature.isEnabled()));
		}
	}

	@Override
	public IdReferenceType getRootPolicyRefExpression()
	{
		return rootPolicyRefExpression;
	}

	@Override
	public List<PdpFeature> getFeatures()
	{
		return features;
	}
}