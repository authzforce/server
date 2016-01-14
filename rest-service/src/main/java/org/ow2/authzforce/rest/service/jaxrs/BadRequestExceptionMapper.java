/**
 * Copyright (C) 2012-2015 Thales Services SAS.
 *
 * This file is part of AuthZForce.
 *
 * AuthZForce is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * AuthZForce is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with AuthZForce. If not, see <http://www.gnu.org/licenses/>.
 */
/**
 * 
 */
package org.ow2.authzforce.rest.service.jaxrs;

import java.util.regex.Pattern;

import javax.ws.rs.BadRequestException;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;
import javax.xml.bind.JAXBException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

/**
 * @see org.apache.cxf.jaxrs.impl.WebApplicationExceptionMapper WebApplicationExceptionMapper
 */
@Provider
public class BadRequestExceptionMapper implements ExceptionMapper<BadRequestException>
{
	private static final Logger LOGGER = LoggerFactory.getLogger(BadRequestExceptionMapper.class);
	private static final Pattern JAXBEXCEPTION_MSG_START_PATTERN = Pattern.compile("^JAXBException occurred :", Pattern.CASE_INSENSITIVE);
	private static final String INVALID_PARAM_MSG_PREFIX = "Invalid parameters: ";

	@Override
	public Response toResponse(BadRequestException exception)
	{
		LOGGER.warn("Bad request", exception);
		final Response oldResp = exception.getResponse();
		final String errMsg;
		final Throwable cause = exception.getCause();
		if (cause != null)
		{
			// JAXB schema validation error
			if (cause instanceof SAXException)
			{
				final Throwable internalCause = cause.getCause();
				if (internalCause instanceof JAXBException)
				{
					final Throwable linkedEx = ((JAXBException) internalCause).getLinkedException();
					errMsg = INVALID_PARAM_MSG_PREFIX + linkedEx.getMessage();
				} else
				{
					errMsg = INVALID_PARAM_MSG_PREFIX + cause.getMessage();
				}
			} else if (cause instanceof JAXBException)
			{
				final Throwable linkedEx = ((JAXBException) cause).getLinkedException();
				errMsg = INVALID_PARAM_MSG_PREFIX + linkedEx.getMessage();
			} else if (cause instanceof IllegalArgumentException)
			{
				final Throwable internalCause = cause.getCause();
				errMsg = cause.getMessage() + (internalCause == null ? "" : ": " + internalCause.getMessage());
			} else
			{
				errMsg = cause.getMessage();
			}
		} else
		{
			// handle case where cause message is only in the response message (no exception object
			// in stacktrace), e.g. JAXBException
			final Object oldEntity = oldResp.getEntity();
			if (oldEntity instanceof String)
			{
				// hide "JAXBException..." when it occurs and only keep the JAXBException message
				errMsg = JAXBEXCEPTION_MSG_START_PATTERN.matcher((String) oldEntity).replaceFirst(INVALID_PARAM_MSG_PREFIX);
			} else
			{
				errMsg = null;
			}
		}

		if (errMsg != null)
		{
			final org.ow2.authzforce.rest.api.xmlns.Error errorEntity = new org.ow2.authzforce.rest.api.xmlns.Error(errMsg);
			return Response.status(Response.Status.BAD_REQUEST).entity(errorEntity).build();
		}

		return oldResp;
	}
}
