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

import javax.ws.rs.InternalServerErrorException;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @see org.apache.cxf.jaxrs.impl.WebApplicationExceptionMapper WebApplicationExceptionMapper
 */
@Provider
public class ServerErrorExceptionMapper implements ExceptionMapper<InternalServerErrorException>
{
	private final static Logger LOGGER = LoggerFactory.getLogger(ServerErrorExceptionMapper.class);
	private final static String INTERNAL_ERR_MSG = "Internal server error";
	private final static org.ow2.authzforce.rest.api.xmlns.Error ERROR = new org.ow2.authzforce.rest.api.xmlns.Error(INTERNAL_ERR_MSG
			+ ". Retry later or contact the administrator.");

	@Override
	public Response toResponse(InternalServerErrorException exception)
	{
		LOGGER.error(INTERNAL_ERR_MSG, exception);
		return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(ERROR).build();
	}
}
