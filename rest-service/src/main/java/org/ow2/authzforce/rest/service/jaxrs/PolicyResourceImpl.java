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
import java.util.ArrayList;
import java.util.List;
import java.util.NavigableSet;

import javax.ws.rs.BadRequestException;
import javax.ws.rs.InternalServerErrorException;
import javax.ws.rs.NotFoundException;

import org.ow2.authzforce.core.pap.api.dao.DomainDao;
import org.ow2.authzforce.core.pap.api.dao.PolicyDaoClient;
import org.ow2.authzforce.core.pap.api.dao.PolicyVersionDaoClient;
import org.ow2.authzforce.core.pdp.api.policy.PolicyVersion;
import org.ow2.authzforce.rest.api.jaxrs.PolicyResource;
import org.ow2.authzforce.rest.api.jaxrs.PolicyVersionResource;
import org.ow2.authzforce.rest.api.xmlns.Resources;
import org.w3._2005.atom.Link;
import org.w3._2005.atom.Relation;

/**
 * Policy Resource implementation. Each policy managed by {@link DomainResourceImpl} is an instance of this class.
 *
 */
public class PolicyResourceImpl implements PolicyDaoClient, PolicyResource
{
	private static final String LATEST_VERSION_ID = "latest";
	private static final NotFoundException NOT_FOUND_EXCEPTION = new NotFoundException();
	private static final BadRequestException INVALID_ARG_BAD_REQUEST_EXCEPTION = new BadRequestException("Invalid argument");
	private static final IllegalArgumentException ILLEGAL_POLICY_ID_ARGUMENT_EXCEPTION = new IllegalArgumentException("Policy ID for policy resource undefined");

	private static final IllegalArgumentException ILLEGAL_POLICY_DAO_ARGUMENT_EXCEPTION = new IllegalArgumentException("Policy DAO for policy resource undefined");

	/**
	 * Policy Resource Factory
	 *
	 */
	public static final PolicyDaoClient.Factory<PolicyVersionResourceImpl, PolicyResourceImpl> FACTORY = new PolicyDaoClient.Factory<PolicyVersionResourceImpl, PolicyResourceImpl>()
	{

		@Override
		public PolicyResourceImpl getInstance(final String policyId, final DomainDao<PolicyVersionResourceImpl, ?> policyDAO)
		{
			if (policyId == null)
			{
				throw ILLEGAL_POLICY_ID_ARGUMENT_EXCEPTION;
			}

			if (policyDAO == null)
			{
				throw ILLEGAL_POLICY_DAO_ARGUMENT_EXCEPTION;
			}

			return new PolicyResourceImpl(policyId, policyDAO);
		}

		@Override
		public PolicyVersionDaoClient.Factory<PolicyVersionResourceImpl> getVersionDaoClientFactory()
		{
			return PolicyVersionResourceImpl.FACTORY;
		}

	};

	private final DomainDao<PolicyVersionResourceImpl, ?> domainDAO;
	private final String policyId;

	private PolicyResourceImpl(final String policyId, final DomainDao<PolicyVersionResourceImpl, ?> domainDAO)
	{
		assert domainDAO != null;
		this.policyId = policyId;
		this.domainDAO = domainDAO;
	}

	private static Resources getVersionResources(final NavigableSet<PolicyVersion> versions)
	{
		// versions expected to be ordered from latest to oldest
		final List<Link> policyVersionLinks = new ArrayList<>(versions.size());
		for (final PolicyVersion v : versions)
		{
			final Link link = new Link();
			policyVersionLinks.add(link);
			link.setHref(v.toString());
			link.setRel(Relation.ITEM);
		}

		return new Resources(policyVersionLinks);
	}

	@Override
	public Resources deletePolicy()
	{
		final NavigableSet<PolicyVersion> removedPolicyVersions;
		try
		{
			removedPolicyVersions = domainDAO.removePolicy(policyId);
		}
		catch (final IOException e)
		{
			throw new InternalServerErrorException("Error removing policy '" + policyId + "' (all versions)", e);
		}
		catch (final IllegalArgumentException e)
		{
			throw new BadRequestException(e);
		}

		if (removedPolicyVersions.isEmpty())
		{
			throw NOT_FOUND_EXCEPTION;
		}

		return getVersionResources(removedPolicyVersions);
	}

	@Override
	public Resources getPolicyVersions()
	{
		final NavigableSet<PolicyVersion> policyVersions;
		try
		{
			policyVersions = domainDAO.getPolicyVersions(policyId);
		}
		catch (final IOException e)
		{
			throw new InternalServerErrorException("Error getting all versions of policy '" + policyId + "'", e);
		}

		if (policyVersions.isEmpty())
		{
			throw NOT_FOUND_EXCEPTION;
		}

		return getVersionResources(policyVersions);
	}

	@Override
	public PolicyVersionResource getPolicyVersionResource(final String version)
	{
		if (version == null)
		{
			throw INVALID_ARG_BAD_REQUEST_EXCEPTION;
		}

		final PolicyVersion policyVersion;
		if (version.equals(LATEST_VERSION_ID))
		{
			try
			{
				policyVersion = domainDAO.getLatestPolicyVersionId(policyId);
			}
			catch (final IOException e)
			{
				throw new InternalServerErrorException("Error getting latest version of policy '" + policyId + "'", e);
			}
		}
		else
		{
			try
			{
				policyVersion = new PolicyVersion(version);
			}
			catch (final IllegalArgumentException e)
			{
				throw INVALID_ARG_BAD_REQUEST_EXCEPTION;
			}
		}

		final PolicyVersionResource versionResource = domainDAO.getVersionDaoClient(policyId, policyVersion);
		if (versionResource == null)
		{
			throw NOT_FOUND_EXCEPTION;
		}

		return versionResource;
	}

}