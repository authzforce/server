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
package org.ow2.authzforce.rest.service.jaxrs;

import java.util.List;

import javax.ws.rs.NotAcceptableException;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.core.MediaType;

/**
 * FastInfoset request blocker
 *
 */
public class XmlAndJsonOnlyMediaTypeRequestFilter implements ContainerRequestFilter
{
	private static final NotAcceptableException NOT_ACCEPTABLE_EXCEPTION = new NotAcceptableException();
	private static final String APPLICATION_MEDIA_TYPE = MediaType.APPLICATION_XML_TYPE.getType(); // "application"
	private static final String XML_SUB_MEDIA_TYPE = MediaType.APPLICATION_XML_TYPE.getSubtype(); // "xml"
	private static final String JSON_SUB_MEDIA_TYPE = MediaType.APPLICATION_JSON_TYPE.getSubtype(); // "json"

	@Override
	public void filter(final ContainerRequestContext context)
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

		if (mediaType0.getType().equalsIgnoreCase(APPLICATION_MEDIA_TYPE)
				&& (mediaType0.getSubtype().equalsIgnoreCase(XML_SUB_MEDIA_TYPE) || mediaType0.getSubtype().equalsIgnoreCase(JSON_SUB_MEDIA_TYPE)))
		{
			return;
		}

		throw NOT_ACCEPTABLE_EXCEPTION;
	}
}