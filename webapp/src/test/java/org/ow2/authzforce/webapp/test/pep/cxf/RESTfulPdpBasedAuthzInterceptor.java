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
package org.ow2.authzforce.webapp.test.pep.cxf;

import java.security.Principal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import javax.xml.namespace.QName;

import oasis.names.tc.xacml._3_0.core.schema.wd_17.Attribute;
import oasis.names.tc.xacml._3_0.core.schema.wd_17.AttributeValueType;
import oasis.names.tc.xacml._3_0.core.schema.wd_17.Attributes;
import oasis.names.tc.xacml._3_0.core.schema.wd_17.DecisionType;
import oasis.names.tc.xacml._3_0.core.schema.wd_17.Request;
import oasis.names.tc.xacml._3_0.core.schema.wd_17.Response;
import oasis.names.tc.xacml._3_0.core.schema.wd_17.Result;

import org.apache.cxf.interceptor.Fault;
import org.apache.cxf.interceptor.security.AccessDeniedException;
import org.apache.cxf.message.Message;
import org.apache.cxf.phase.AbstractPhaseInterceptor;
import org.apache.cxf.phase.Phase;
import org.apache.cxf.rt.security.saml.xacml.CXFMessageParser;
import org.apache.cxf.rt.security.saml.xacml.XACMLConstants;
import org.apache.cxf.security.LoginSecurityContext;
import org.apache.cxf.security.SecurityContext;
import org.apache.wss4j.common.ext.WSSecurityException;
import org.ow2.authzforce.core.pdp.api.HashCollections;
import org.ow2.authzforce.rest.api.jaxrs.PdpResource;
import org.ow2.authzforce.xacml.identifiers.XacmlAttributeCategory;
import org.ow2.authzforce.xacml.identifiers.XacmlAttributeId;
import org.ow2.authzforce.xacml.identifiers.XacmlDatatypeId;
import org.slf4j.LoggerFactory;

/**
 * This class represents a so-called XACML PEP that, for every CXF service request, creates an XACML 3.0 authorization decision Request to a PDP using AuthzForce Server's RESTful API, given a
 * Principal, list of roles - typically coming from SAML token - and MessageContext. The principal name is inserted as the Subject ID, and the list of roles associated with that principal are inserted
 * as Subject roles. The action to send defaults to "execute". It is an adaptation of
 * https://github.com/coheigea/testcases/blob/master/apache/cxf/cxf-sts-xacml/src/test/java/org/apache/coheigea/cxf/sts/xacml/authorization/xacml3/XACML3AuthorizingInterceptor.java, except it uses
 * AuthzForce RESTful API for PDP evaluation instead of OpenAZ API.
 * 
 * For a SOAP Service, the resource-id Attribute refers to the "{serviceNamespace}serviceName#{operationNamespace}operationName" String (shortened to "{serviceNamespace}serviceName#operationName" if
 * the namespaces are identical). The "{serviceNamespace}serviceName", "{operationNamespace}operationName" and resource URI are also sent to simplify processing at the PDP side.
 * 
 * For a REST service the request URL is the resource. You can also configure the ability to send the truncated request URI instead for a SOAP or REST service.
 */
public class RESTfulPdpBasedAuthzInterceptor extends AbstractPhaseInterceptor<Message>
{

	private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(RESTfulPdpBasedAuthzInterceptor.class);

	private static final String DEFAULT_SOAP_ACTION = "execute";

	private final PdpResource pdp;

	/**
	 * Create Authorization interceptor (XACML PEP) using input {@code pdp} as XACML PDP
	 * 
	 * @param pdp
	 *            XACML PDP
	 */
	public RESTfulPdpBasedAuthzInterceptor(final PdpResource pdp)
	{
		super(Phase.PRE_INVOKE);
		this.pdp = pdp;
	}

	@Override
	public void handleMessage(final Message message) throws Fault
	{
		final SecurityContext sc = message.get(SecurityContext.class);
		if (sc instanceof LoginSecurityContext)
		{
			final Principal principal = sc.getUserPrincipal();
			final LoginSecurityContext loginSecurityContext = (LoginSecurityContext) sc;
			final Set<Principal> principalRoles = loginSecurityContext.getUserRoles();
			final Set<String> roles;
			if (principalRoles == null)
			{
				roles = Collections.emptySet();
			}
			else
			{
				roles = HashCollections.newUpdatableSet(principalRoles.size());
				for (final Principal p : principalRoles)
				{
					if (p != principal)
					{
						roles.add(p.getName());
					}
				}
			}

			try
			{
				if (authorize(principal, roles, message))
				{
					return;
				}
			}
			catch (final Exception e)
			{
				LOGGER.debug("Unauthorized", e);
				throw new AccessDeniedException("Unauthorized");
			}
		}
		else
		{
			LOGGER.debug("The SecurityContext was not an instance of LoginSecurityContext. No authorization is possible as a result");
		}

		throw new AccessDeniedException("Unauthorized");
	}

	protected boolean authorize(final Principal principal, final Set<String> roles, final Message message) throws Exception
	{
		final Request request = createRequest(principal, roles, message);
		LOGGER.debug("XACML Request: {}", request);

		// Evaluate the request
		final Response response = pdp.requestPolicyDecision(request);

		if (response == null || response.getResults().isEmpty())
		{
			return false;
		}

		final Result result = response.getResults().get(0);
		// Handle any Obligations returned by the PDP
		handleObligationsOrAdvice(request, principal, message, result);

		LOGGER.debug("XACML authorization result: {}", result);
		return result.getDecision() == DecisionType.PERMIT;
	}

	private static Request createRequest(final Principal principal, final Set<String> roles, final Message message) throws WSSecurityException
	{
		assert roles != null;

		final CXFMessageParser messageParser = new CXFMessageParser(message);
		final String issuer = messageParser.getIssuer();

		/*
		 * 3 attribute categories, 7 total attributes
		 */

		// Subject attributes
		// Subject ID
		final AttributeValueType subjectIdVal = new AttributeValueType(Collections.singletonList(principal.getName()), XacmlDatatypeId.STRING.value(), null);
		final Attribute subjectIdAtt = new Attribute(Collections.singletonList(subjectIdVal), XacmlAttributeId.XACML_1_0_SUBJECT_ID.value(), issuer, false);

		// Subject role(s)
		final Attribute subjectRoleAtt = new Attribute(stringsToAttributeValues(roles, XacmlDatatypeId.ANY_URI.value()), XacmlAttributeId.XACML_2_0_SUBJECT_ROLE.value(), issuer, false);

		final Attributes subjectCategory = new Attributes(null, Arrays.asList(subjectIdAtt, subjectRoleAtt), XacmlAttributeCategory.XACML_1_0_ACCESS_SUBJECT.value(), null);

		// Resource attributes
		// Resource ID
		final AttributeValueType resourceIdVal = new AttributeValueType(Collections.singletonList(getResourceId(messageParser)), XacmlDatatypeId.STRING.value(), null);
		final Attribute resourceIdAtt = new Attribute(Collections.singletonList(resourceIdVal), XacmlAttributeId.XACML_1_0_RESOURCE_ID.value(), null, false);

		// Resource - WSDL-defined Service ID / Operation / Endpoint
		List<Attribute> resourceAtts;
		if (messageParser.isSOAPService())
		{
			// WSDL Service
			final QName wsdlService = messageParser.getWSDLService();
			if (wsdlService == null)
			{
				resourceAtts = new ArrayList<>(3);
				resourceAtts.add(resourceIdAtt);
			}
			else
			{
				resourceAtts = new ArrayList<>(4);
				resourceAtts.add(resourceIdAtt);

				final AttributeValueType resourceServiceIdAttVal = new AttributeValueType(Collections.singletonList(wsdlService.toString()), XacmlDatatypeId.STRING.value(), null);
				final Attribute resourceServiceIdAtt = new Attribute(Collections.singletonList(resourceServiceIdAttVal), XACMLConstants.RESOURCE_WSDL_SERVICE_ID, null, false);
				resourceAtts.add(resourceServiceIdAtt);
			}

			// WSDL Operation
			final QName wsdlOperation = messageParser.getWSDLOperation();
			final AttributeValueType resourceOperationIdAttVal = new AttributeValueType(Collections.singletonList(wsdlOperation.toString()), XacmlDatatypeId.STRING.value(), null);
			final Attribute resourceOperationIdAtt = new Attribute(Collections.singletonList(resourceOperationIdAttVal), XACMLConstants.RESOURCE_WSDL_OPERATION_ID, null, false);
			resourceAtts.add(resourceOperationIdAtt);

			// WSDL Endpoint
			final String endpointURI = messageParser.getResourceURI(false);
			final AttributeValueType resourceWSDLEndpointAttVal = new AttributeValueType(Collections.singletonList(endpointURI), XacmlDatatypeId.STRING.value(), null);
			final Attribute resourceWSDLEndpointAtt = new Attribute(Collections.singletonList(resourceWSDLEndpointAttVal), XACMLConstants.RESOURCE_WSDL_ENDPOINT, null, false);
			resourceAtts.add(resourceWSDLEndpointAtt);
		}
		else
		{
			resourceAtts = Collections.singletonList(resourceIdAtt);
		}

		final Attributes resourceCategory = new Attributes(null, resourceAtts, XacmlAttributeCategory.XACML_3_0_RESOURCE.value(), null);

		// Action ID
		final String actionToUse = messageParser.getAction(DEFAULT_SOAP_ACTION);
		final AttributeValueType actionIdAttVal = new AttributeValueType(Collections.singletonList(actionToUse), XacmlDatatypeId.STRING.value(), null);
		final Attribute actionIdAtt = new Attribute(Collections.singletonList(actionIdAttVal), XacmlAttributeId.XACML_1_0_ACTION_ID.value(), null, false);

		final Attributes actionCategory = new Attributes(null, Collections.singletonList(actionIdAtt), XacmlAttributeCategory.XACML_3_0_ACTION.value(), null);

		// Environment - current date/time will be set by the PDP

		return new Request(null, Arrays.asList(subjectCategory, resourceCategory, actionCategory), null, false, false);
	}

	private static List<AttributeValueType> stringsToAttributeValues(final Set<String> strings, final String datatype)
	{
		assert strings != null;

		final List<AttributeValueType> attVals = new ArrayList<>(strings.size());
		for (final String string : strings)
		{
			attVals.add(new AttributeValueType(Collections.singletonList(string), datatype, null));
		}

		return attVals;
	}

	private static String getResourceId(final CXFMessageParser messageParser)
	{
		final String resourceId;
		if (messageParser.isSOAPService())
		{
			final QName serviceName = messageParser.getWSDLService();
			final QName operationName = messageParser.getWSDLOperation();

			if (serviceName != null)
			{
				final String resourceIdPrefix = serviceName.toString() + "#";
				if (serviceName.getNamespaceURI() != null && serviceName.getNamespaceURI().equals(operationName.getNamespaceURI()))
				{
					resourceId = resourceIdPrefix + operationName.getLocalPart();
				}
				else
				{
					resourceId = resourceIdPrefix + operationName.toString();
				}
			}
			else
			{
				resourceId = operationName.toString();
			}
		}
		else
		{
			resourceId = messageParser.getResourceURI(false);
		}

		return resourceId;
	}

	/**
	 * Handle any Obligations returned by the PDP. Does nothing by default. Override this method if you want to handle Obligations/Advice in a specific way
	 */
	protected void handleObligationsOrAdvice(final Request request, final Principal principal, final Message message, final Result result) throws Exception
	{
		// Do nothing by default
	}

}
