/**
 * Copyright (C) 2012-2016 Thales Services SAS.
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
package org.ow2.authzforce.rest.service.jaxrs;

import java.util.List;

import javax.ws.rs.NotAcceptableException;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.core.MediaType;

/**
 * FastInfoset request bloquer
 *
 */
public class XmlOnlyMediaTypeRequestFilter implements ContainerRequestFilter
{
	private static final NotAcceptableException NOT_ACCEPTABLE_EXCEPTION = new NotAcceptableException();

	@Override
	public void filter(ContainerRequestContext context)
	{
		final List<MediaType> acceptMediaTypes = context.getAcceptableMediaTypes();
		if (acceptMediaTypes.isEmpty())
		{
			return;
		}

		final MediaType mediaType0 = acceptMediaTypes.get(0);
		if (mediaType0.equals(MediaType.WILDCARD_TYPE))
		{
			return;
		}

		if (mediaType0.getType().equalsIgnoreCase("application") && mediaType0.getSubtype().equalsIgnoreCase("xml"))
		{
			return;
		}

		throw NOT_ACCEPTABLE_EXCEPTION;
	}

}