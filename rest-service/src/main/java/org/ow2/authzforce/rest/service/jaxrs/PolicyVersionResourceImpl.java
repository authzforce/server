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

import java.io.IOException;

import javax.ws.rs.InternalServerErrorException;
import javax.ws.rs.NotFoundException;

import oasis.names.tc.xacml._3_0.core.schema.wd_17.PolicySet;

import org.ow2.authzforce.core.pap.api.dao.DomainDao;
import org.ow2.authzforce.core.pap.api.dao.PolicyVersionDaoClient;
import org.ow2.authzforce.core.pdp.api.policy.PolicyVersion;
import org.ow2.authzforce.rest.api.jaxrs.PolicyVersionResource;

/**
 * Policy Version resource implementation. Each policy version managed by {@link PolicyResourceImpl} is an instance of this class.
 *
 */
public class PolicyVersionResourceImpl implements PolicyVersionDaoClient, PolicyVersionResource
{
	private static final NotFoundException NOT_FOUND_EXCEPTION = new NotFoundException();

	private static final IllegalArgumentException ILLEGAL_VERSION_ARGUMENT_EXCEPTION = new IllegalArgumentException("Version for policy version resource undefined");

	private static final IllegalArgumentException ILLEGAL_VERSION_DAO_ARGUMENT_EXCEPTION = new IllegalArgumentException("Policy version DAO for policy version resource undefined");

	/**
	 * Policy version resource Factory
	 *
	 */
	public static final PolicyVersionDaoClient.Factory<PolicyVersionResourceImpl> FACTORY = new PolicyVersionDaoClient.Factory<PolicyVersionResourceImpl>()
	{

		@Override
		public PolicyVersionResourceImpl getInstance(final String policyId, final PolicyVersion versionId, final DomainDao<?, ?> domainDAO)
		{
			if (versionId == null)
			{
				throw ILLEGAL_VERSION_ARGUMENT_EXCEPTION;
			}

			if (domainDAO == null)
			{
				throw ILLEGAL_VERSION_DAO_ARGUMENT_EXCEPTION;
			}

			return new PolicyVersionResourceImpl(policyId, versionId, domainDAO);
		}

	};

	private final PolicyVersion versionId;
	private final DomainDao<?, ?> domainDAO;

	private final String policyId;

	private PolicyVersionResourceImpl(final String policyId, final PolicyVersion versionId, final DomainDao<?, ?> domainDAO)
	{
		assert versionId != null && domainDAO != null;
		this.policyId = policyId;
		this.versionId = versionId;
		this.domainDAO = domainDAO;
	}

	@Override
	public PolicySet getPolicyVersion()
	{
		final PolicySet policyVersion;
		try
		{
			policyVersion = domainDAO.getPolicyVersion(policyId, versionId);
		}
		catch (final IOException e)
		{
			throw new InternalServerErrorException("Error getting policy version '" + versionId + "'", e);
		}

		if (policyVersion == null)
		{
			throw NOT_FOUND_EXCEPTION;
		}

		return policyVersion;
	}

	@Override
	public PolicySet deletePolicyVersion() throws IllegalArgumentException
	{
		final PolicySet deletedPolicyVersion;
		try
		{
			deletedPolicyVersion = domainDAO.removePolicyVersion(policyId, versionId);
		}
		catch (final IOException e)
		{
			throw new InternalServerErrorException("Error removing policy version '" + versionId + "'", e);
		}

		if (deletedPolicyVersion == null)
		{
			throw NOT_FOUND_EXCEPTION;
		}

		return deletedPolicyVersion;
	}

}