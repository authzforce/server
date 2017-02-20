/**
 * Copyright (C) 2012-2017 Thales Services SAS.
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
package org.ow2.authzforce.web.test.pep.cxf;

import java.net.URL;

import javax.xml.namespace.QName;
import javax.xml.ws.BindingProvider;
import javax.xml.ws.Service;

import org.apache.coheigea.cxf.sts.xacml.common.STSServer;
import org.apache.coheigea.cxf.sts.xacml.common.TokenTestUtils;
import org.apache.cxf.Bus;
import org.apache.cxf.bus.spring.SpringBusFactory;
import org.apache.cxf.endpoint.Client;
import org.apache.cxf.frontend.ClientProxy;
import org.apache.cxf.testutil.common.AbstractBusClientServerTestBase;
import org.example.contract.doubleit.DoubleItPortType;
import org.testng.annotations.BeforeClass;

/**
 * The client authenticates to the STS using a username/password, and gets a signed holder-of-key SAML Assertion in return. This is presented to the service, who verifies proof-of-possession + the
 * signature of the STS on the assertion. The CXF endpoint extracts roles from the Assertion + populates the security context. Note that the CXF endpoint requires a "role" Claim via the security
 * policy.
 *
 * The CXF Endpoint has configured the XACMLAuthorizingInterceptor, which creates a XACML 3.0 request for dispatch to the PDP, and then enforces the PDP's decision. The PDP is a REST service,
 * that requires that a user must have role "boss" to access the "doubleIt" operation ("alice" has this role, "bob" does not).
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

	@BeforeClass
	public static void startServers() throws Exception
	{
		assertTrue("Server failed to launch",
		// run the server in the same process
		// set this to false to fork
				launchServer(Server.class, true));
		assertTrue("Server failed to launch",
		// run the server in the same process
		// set this to false to fork
				launchServer(STSServer.class, true));
		assertTrue("Server failed to launch",
		// run the server in the same process
		// set this to false to fork
				launchServer(PdpServer.class, true));
	}

	@org.junit.Test
	public void testAuthorizedRequest() throws Exception
	{

		final SpringBusFactory bf = new SpringBusFactory();
		final URL busFile = RESTfulPdpBasedAuthzInterceptorTest.class.getResource("cxf-ws-client.xml");

		final Bus bus = bf.createBus(busFile.toString());
		SpringBusFactory.setDefaultBus(bus);
		SpringBusFactory.setThreadDefaultBus(bus);

		final URL wsdl = RESTfulPdpBasedAuthzInterceptorTest.class.getResource("DoubleItSecure.wsdl");
		final Service service = Service.create(wsdl, SERVICE_QNAME);
		final QName portQName = new QName(NAMESPACE, "DoubleItTransportPort");
		final DoubleItPortType transportPort = service.getPort(portQName, DoubleItPortType.class);
		updateAddressPort(transportPort, PORT);

		final Client client = ClientProxy.getClient(transportPort);
		client.getRequestContext().put("ws-security.username", "alice");

		TokenTestUtils.updateSTSPort((BindingProvider) transportPort, STS_PORT);

		doubleIt(transportPort, 25);
	}

	@org.junit.Test
	public void testUnauthorizedRequest() throws Exception
	{

		final SpringBusFactory bf = new SpringBusFactory();
		final URL busFile = RESTfulPdpBasedAuthzInterceptorTest.class.getResource("cxf-ws-client.xml");

		final Bus bus = bf.createBus(busFile.toString());
		SpringBusFactory.setDefaultBus(bus);
		SpringBusFactory.setThreadDefaultBus(bus);

		final URL wsdl = RESTfulPdpBasedAuthzInterceptorTest.class.getResource("DoubleItSecure.wsdl");
		final Service service = Service.create(wsdl, SERVICE_QNAME);
		final QName portQName = new QName(NAMESPACE, "DoubleItTransportPort");
		final DoubleItPortType transportPort = service.getPort(portQName, DoubleItPortType.class);
		updateAddressPort(transportPort, PORT);

		final Client client = ClientProxy.getClient(transportPort);
		client.getRequestContext().put("ws-security.username", "bob");

		TokenTestUtils.updateSTSPort((BindingProvider) transportPort, STS_PORT);

		try
		{
			doubleIt(transportPort, 25);
			fail("Failure expected on bob");
		}
		catch (final Exception ex)
		{
			// expected
		}
	}

	private static void doubleIt(final DoubleItPortType port, final int numToDouble)
	{
		final int resp = port.doubleIt(numToDouble);
		assertEquals(numToDouble * 2, resp);
	}

}
