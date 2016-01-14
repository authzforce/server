/**
 * Copyright (C) 2012-2016 Thales Services SAS.
 *
 * This file is part of AuthZForce CE.
 *
 * AuthZForce CE is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * AuthZForce CE is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with AuthZForce CE. If not, see <http://www.gnu.org/licenses/>.
 */
package org.ow2.authzforce.rest.service.jaxrs;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NavigableSet;

import javax.ws.rs.BadRequestException;
import javax.ws.rs.InternalServerErrorException;
import javax.ws.rs.NotFoundException;

import org.ow2.authzforce.core.pap.api.dao.DomainDAO;
import org.ow2.authzforce.core.pap.api.dao.PolicyDAOClient;
import org.ow2.authzforce.core.pap.api.dao.PolicyVersionDAOClient;
import org.ow2.authzforce.core.pdp.api.PolicyVersion;
import org.ow2.authzforce.rest.api.jaxrs.PolicyResource;
import org.ow2.authzforce.rest.api.jaxrs.PolicyVersionResource;
import org.ow2.authzforce.rest.api.xmlns.Resources;
import org.w3._2005.atom.Link;
import org.w3._2005.atom.Relation;

/**
 * Policy Resource implementation. Each policy managed by {@link DomainResourceImpl} is an instance of this class.
 *
 */
public class PolicyResourceImpl implements PolicyDAOClient, PolicyResource
{
	private static final NotFoundException NOT_FOUND_EXCEPTION = new NotFoundException();
	private static final BadRequestException INVALID_ARG_BAD_REQUEST_EXCEPTION = new BadRequestException("Invalid argument");
	private static final IllegalArgumentException ILLEGAL_POLICY_ID_ARGUMENT_EXCEPTION = new IllegalArgumentException("Policy ID for policy resource undefined");

	private static final IllegalArgumentException ILLEGAL_POLICY_DAO_ARGUMENT_EXCEPTION = new IllegalArgumentException(
			"Policy DAO for policy resource undefined");

	/**
	 * Policy Resource Factory
	 *
	 */
	public static final PolicyDAOClient.Factory<PolicyVersionResourceImpl, PolicyResourceImpl> FACTORY = new PolicyDAOClient.Factory<PolicyVersionResourceImpl, PolicyResourceImpl>()
	{

		@Override
		public PolicyResourceImpl getInstance(String policyId, DomainDAO<PolicyVersionResourceImpl, ?> policyDAO)
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
		public PolicyVersionDAOClient.Factory<PolicyVersionResourceImpl> getVersionDAOClientFactory()
		{
			return PolicyVersionResourceImpl.FACTORY;
		}

	};

	private final DomainDAO<PolicyVersionResourceImpl, ?> domainDAO;
	private final String policyId;

	private PolicyResourceImpl(String policyId, DomainDAO<PolicyVersionResourceImpl, ?> domainDAO)
	{
		assert domainDAO != null;
		this.policyId = policyId;
		this.domainDAO = domainDAO;
	}

	private static Resources getVersionResources(NavigableSet<PolicyVersion> versions)
	{
		final List<Link> policyVersionLinks = new ArrayList<>(versions.size());
		// Iterate from last version to oldest
		final Iterator<PolicyVersion> versionIterator = versions.descendingIterator();
		while (versionIterator.hasNext())
		{
			final Link link = new Link();
			policyVersionLinks.add(link);
			link.setHref(versionIterator.next().toString());
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
		} catch (IOException e)
		{
			throw new InternalServerErrorException("Error removing policy '" + policyId + "' (all versions)", e);
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
		} catch (IOException e)
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
	public PolicyVersionResource getPolicyVersionResource(String version)
	{
		if (version == null)
		{
			throw INVALID_ARG_BAD_REQUEST_EXCEPTION;
		}

		final PolicyVersionResource versionResource = domainDAO.getVersionDAOClient(policyId, version);
		if (versionResource == null)
		{
			throw NOT_FOUND_EXCEPTION;
		}

		return versionResource;
	}

	// @Override
	// public DAO getDAO()
	// {
	// return domainDAO;
	// }

}