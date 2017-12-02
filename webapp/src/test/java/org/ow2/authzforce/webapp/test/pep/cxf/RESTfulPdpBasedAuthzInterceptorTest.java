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

import java.net.URL;

import javax.xml.namespace.QName;
import javax.xml.ws.BindingProvider;
import javax.xml.ws.Service;

import org.apache.coheigea.cxf.sts.xacml.common.STSServer;
import org.apache.coheigea.cxf.sts.xacml.common.TokenTestUtils;
import org.apache.cxf.BusFactory;
import org.apache.cxf.endpoint.Client;
import org.apache.cxf.frontend.ClientProxy;
import org.apache.cxf.testutil.common.AbstractBusClientServerTestBase;
import org.example.contract.doubleit.DoubleItPortType;
import org.junit.Before;

/**
 * The client authenticates to the STS using a username/password, and gets a signed holder-of-key SAML Assertion in return. This is presented to the service, who verifies proof-of-possession + the
 * signature of the STS on the assertion. The CXF endpoint extracts roles from the Assertion + populates the security context. Note that the CXF endpoint requires a "role" Claim via the security
 * policy.
 *
 * The CXF Endpoint has configured the XACMLAuthorizingInterceptor, which creates a XACML 3.0 request for dispatch to the PDP, and then enforces the PDP's decision. The PDP is a REST service, that
 * requires that a user must have role "boss" to access the "doubleIt" operation ("alice" has this role, "bob" does not).
 */
public class RESTfulPdpBasedAuthzInterceptorTest extends AbstractBusClientServerTestBase
{

	// public static final String PDP_PORT = allocatePort(PdpServer.class);
	static
	{
		allocatePort(PdpServer.class);
	}

	private static final String NAMESPACE = "http://www.example.org/contract/DoubleIt";
	private static final QName SERVICE_QNAME = new QName(NAMESPACE, "DoubleItService");

	private static final String PORT = allocatePort(Server.class);
	private static final String STS_PORT = allocatePort(STSServer.class);
	private static final URL DOUBLEIT_WSDL = RESTfulPdpBasedAuthzInterceptorTest.class.getResource("DoubleItSecure.wsdl");
	private static final QName DOUBLEIT_WS_PORT_QNAME = new QName(NAMESPACE, "DoubleItTransportPort");

	@org.junit.BeforeClass
	public static void startServers() throws Exception
	{
		assertTrue("DoubleIt WS Server failed to launch",
		// run the server in the same process
		// set this to false to fork
				launchServer(Server.class, true));
		assertTrue("STS Server failed to launch",
		// run the server in the same process
		// set this to false to fork
				launchServer(STSServer.class, true));
		assertTrue("PDP Server failed to launch",
		// run the server in the same process
		// set this to false to fork
				launchServer(PdpServer.class, true));
	}

	private DoubleItPortType doubleItWsPort = null;

	@Before
	public void beforeTest() throws Exception
	{
		/*
		 * Client
		 */
		final URL busFile = RESTfulPdpBasedAuthzInterceptorTest.class.getResource("cxf-ws-client.xml");
		this.createBus(busFile.toString());
		BusFactory.setThreadDefaultBus(this.getBus());
		final Service service = Service.create(DOUBLEIT_WSDL, SERVICE_QNAME);
		doubleItWsPort = service.getPort(DOUBLEIT_WS_PORT_QNAME, DoubleItPortType.class);
	}

	private static void doubleIt(final DoubleItPortType port, final int numToDouble)
	{
		final int resp = port.doubleIt(numToDouble);
		assertEquals(numToDouble * 2, resp);
	}

	@org.junit.Test
	public void testAuthorizedRequest() throws Exception
	{
		updateAddressPort(doubleItWsPort, PORT);
		final Client cxfClient = ClientProxy.getClient(doubleItWsPort);
		cxfClient.getRequestContext().put("ws-security.username", "alice");
		TokenTestUtils.updateSTSPort((BindingProvider) doubleItWsPort, STS_PORT);
		doubleIt(doubleItWsPort, 25);
	}

	@org.junit.Test
	public void testUnauthorizedRequest() throws Exception
	{
		updateAddressPort(doubleItWsPort, PORT);
		final Client cxfClient = ClientProxy.getClient(doubleItWsPort);
		cxfClient.getRequestContext().put("ws-security.username", "bob");
		TokenTestUtils.updateSTSPort((BindingProvider) doubleItWsPort, STS_PORT);
		try
		{
			doubleIt(doubleItWsPort, 25);
			fail("Failure expected on bob");
		}
		catch (final Exception ex)
		{
			// expected
		}
	}

}
