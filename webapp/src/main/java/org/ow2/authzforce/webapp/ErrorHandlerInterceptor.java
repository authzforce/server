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
package org.ow2.authzforce.webapp;

import java.io.IOException;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;

import org.apache.cxf.interceptor.Fault;
import org.apache.cxf.message.Message;
import org.apache.cxf.phase.AbstractPhaseInterceptor;
import org.apache.cxf.phase.Phase;
import org.apache.cxf.transport.http.AbstractHTTPDestination;
import org.ow2.authzforce.jaxrs.util.JaxbErrorMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 
 * CXF Interceptor for outbound faults that handles internal server errors. In particular, it remove sensitive info and maps it to Bad Request status code when it is actually a bad request. Based on
 * the CXF CustomOutFaultInterceptor example.
 * 
 * @see <a href="http://svn.apache.org/repos/asf/cxf/trunk/systests/jaxrs/src/test/java/org/apache/cxf/systest/jaxrs/CustomOutFaultInterceptor.java">org.apache.
 *      cxf.systest.jaxrs.CustomOutFaultInterceptor</a>
 */
public class ErrorHandlerInterceptor extends AbstractPhaseInterceptor<Message>
{
	private final static Logger LOGGER = LoggerFactory.getLogger(ErrorHandlerInterceptor.class);
	private final static String INTERNAL_SERVER_ERROR_MSG = "Internal Server error. Retry later or contact the administrator.";
	private final static RuntimeException INTERNAL_SERVER_ERROR = new RuntimeException(INTERNAL_SERVER_ERROR_MSG);
	private final static JAXBContext AUTHZ_API_JAXB_CONTEXT;
	static
	{
		try
		{
			AUTHZ_API_JAXB_CONTEXT = JAXBContext.newInstance(JaxbErrorMessage.class);
		}
		catch (final JAXBException e)
		{
			throw new RuntimeException("Failed to initialize Authorization API schema's JAXB context for marshalling Error elements", e);
		}
	}

	/**
	 * Registration of the interceptor to a specific CXF message processing phase
	 */
	public ErrorHandlerInterceptor()
	{
		super(Phase.POST_LOGICAL);
	}

	@Override
	public void handleMessage(final Message message) throws Fault
	{
		final Exception ex = message.getContent(Exception.class);
		// this is a fault message, override with minimal fault string to hide any internal info
		if (ex != null)
		{
			LOGGER.error("Fatal CXF service error", ex);
			final HttpServletResponse response = (HttpServletResponse) message.getExchange().getInMessage().get(AbstractHTTPDestination.HTTP_RESPONSE);
			final int respStatus;
			final String errMsg;

			final Throwable cause = ex.getCause();
			if (cause instanceof IllegalArgumentException)
			{
				respStatus = HttpServletResponse.SC_BAD_REQUEST;
				final Throwable causeBehind = cause.getCause();
				errMsg = cause.getMessage() + (causeBehind == null ? "" : ": " + causeBehind.getMessage());
			}
			else
			{
				respStatus = HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
				errMsg = INTERNAL_SERVER_ERROR_MSG;
			}

			final JaxbErrorMessage errorEntity = new JaxbErrorMessage(errMsg);
			response.setStatus(respStatus);
			try (final ServletOutputStream out = response.getOutputStream())
			{
				final Marshaller marshaller = AUTHZ_API_JAXB_CONTEXT.createMarshaller();
				marshaller.marshal(errorEntity, out);
				out.flush();
			}
			catch (JAXBException | IOException | IllegalStateException e)
			{
				LOGGER.error("Failed to override service response", e);
				throw INTERNAL_SERVER_ERROR;
			}

			message.getInterceptorChain().abort();
		}
	}
}
