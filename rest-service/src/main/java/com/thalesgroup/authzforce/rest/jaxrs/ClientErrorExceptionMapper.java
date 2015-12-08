/**
 * Copyright (C) 2012-2015 Thales Services SAS.
 *
 * This file is part of AuthZForce.
 *
 * AuthZForce is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * AuthZForce is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with AuthZForce.  If not, see <http://www.gnu.org/licenses/>.
 */
/**
 * 
 */
package com.thalesgroup.authzforce.rest.jaxrs;

import javax.ws.rs.ClientErrorException;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;


/**
 * @see org.apache.cxf.jaxrs.impl.WebApplicationExceptionMapper WebApplicationExceptionMapper
 */
@Provider
public class ClientErrorExceptionMapper implements ExceptionMapper<ClientErrorException>
{

	@Override
	public Response toResponse(ClientErrorException exception)
	{

		// if NotFoundException has root cause, we expect the root cause message to be more specific
		// on what resource could not be found, so return this message to the client
		if (exception.getCause() != null)
		{
			final com.thalesgroup.authz.model._3.Error errorEntity = new com.thalesgroup.authz.model._3.Error();
			errorEntity.setMessage(exception.getCause().getMessage());
			return Response.status(Response.Status.BAD_REQUEST).entity(errorEntity).build();
		}

		// if not, return response as is (no change)
		return exception.getResponse();
	}
}
