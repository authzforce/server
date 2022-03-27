/*
 * Copyright (C) 2012-2022 THALES.
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

import oasis.names.tc.xacml._3_0.core.schema.wd_17.PolicySet;
import org.ow2.authzforce.core.pap.api.dao.AuthzPolicy;
import org.ow2.authzforce.core.pap.api.dao.DomainDao;
import org.ow2.authzforce.core.pap.api.dao.PolicyVersionDaoClient;
import org.ow2.authzforce.core.pdp.api.policy.PolicyVersion;
import org.ow2.authzforce.rest.api.jaxrs.PolicyVersionResource;

import javax.ws.rs.InternalServerErrorException;
import javax.ws.rs.NotFoundException;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

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
	 * Name of the (Apache CXF) MessageContext property where the XML namespace contexts (Java {@link Map <String, String>} ) used for input XML (esp. usable in XACML PolicySet's XPath expressions) is to be injected.
	 */
	public   static final String XML_NS_CONTEXTS_CXF_MESSAGE_CONTEXT_PROPERTY_NAME = PolicyVersionResourceImpl.class.getName() + ".xmlnsContexts";

	public static final ThreadLocal<Map<String, String>> OUTPUT_POLICY_XPATH_NAMESPACE_CONTEXT = ThreadLocal.withInitial(HashMap::new);

	/**
	 * Policy version resource Factory
	 *
	 */
	public static final PolicyVersionDaoClient.Factory<PolicyVersionResourceImpl> FACTORY = (policyId, versionId, domainDAO) ->
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
		final AuthzPolicy policyVersion;
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

		// This does not work, PhaseInterceptorChain.getCurrentMessage() is replaced in response, so JAXBElementProvider does not have access to it
		//PhaseInterceptorChain.getCurrentMessage().put(DomainResourceImpl.XML_NS_CONTEXTS_CXF_MESSAGE_CONTEXT_PROPERTY_NAME, policyVersion.getXPathNamespaceContexts());

		OUTPUT_POLICY_XPATH_NAMESPACE_CONTEXT.get().putAll(policyVersion.getXPathNamespaceContexts());
		return policyVersion.toXacml();
	}

	@Override
	public PolicySet deletePolicyVersion() throws IllegalArgumentException
	{
		final AuthzPolicy deletedPolicyVersion;
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

		// This does not work, PhaseInterceptorChain.getCurrentMessage() is replaced in response, so JAXBElementProvider does not have access to it
		//PhaseInterceptorChain.getCurrentMessage().put(DomainResourceImpl.XML_NS_CONTEXTS_CXF_MESSAGE_CONTEXT_PROPERTY_NAME, policyVersion.getXPathNamespaceContexts());

		OUTPUT_POLICY_XPATH_NAMESPACE_CONTEXT.get().putAll(deletedPolicyVersion.getXPathNamespaceContexts());
		return deletedPolicyVersion.toXacml();
	}

}