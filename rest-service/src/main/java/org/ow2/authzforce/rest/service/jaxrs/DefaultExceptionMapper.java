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
/**
 * 
 */
package org.ow2.authzforce.rest.service.jaxrs;

import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default JAX-RS {@link ExceptionMapper} for all exceptions not supported by other {@link ExceptionMapper}
 */
@Provider
public class DefaultExceptionMapper implements ExceptionMapper<Throwable>
{
	private final static Logger LOGGER = LoggerFactory.getLogger(DefaultExceptionMapper.class);
	private final static String INTERNAL_ERR_MSG = "Internal server error:";
	private final static org.ow2.authzforce.rest.api.xmlns.Error ERROR = new org.ow2.authzforce.rest.api.xmlns.Error("Internal server error. Retry later or contact the administrator.");

	@Override
	public Response toResponse(final Throwable exception)
	{
		/*
		 * Hide any internal server error info to clients
		 */
		LOGGER.error(INTERNAL_ERR_MSG, exception);
		return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(ERROR).build();
	}
}
