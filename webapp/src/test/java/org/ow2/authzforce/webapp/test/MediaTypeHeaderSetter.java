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
package org.ow2.authzforce.webapp.test;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.ws.rs.core.MediaType;

import org.apache.cxf.interceptor.AbstractOutDatabindingInterceptor;
import org.apache.cxf.interceptor.Fault;
import org.apache.cxf.message.Message;
import org.apache.cxf.phase.Phase;

/**
 * CXF interceptor that set client's Content-Type/Accept headers for XML/FastInfoset properly
 *
 */
public final class MediaTypeHeaderSetter extends AbstractOutDatabindingInterceptor
{
	private final String contentType;
	private final List<String> acceptTypes;

	public MediaTypeHeaderSetter(final MediaType mediaType)
	{
		super(Phase.PRE_LOGICAL);
		switch (mediaType.getSubtype())
		{
			case "fastinfoset":
				contentType = mediaType.toString();
				/*
				 * Remove any occurrence of 'application/xml' from Accept headers, then the FIStaxOutInterceptor will add 'application/fastinfoset' if fastInfoset was enabled on the JAXRS client.
				 */
				acceptTypes = Collections.emptyList();
				break;

			case "xml":
			case "json":
				contentType = mediaType.toString();
				acceptTypes = Collections.singletonList(contentType);
				break;

			default:
				throw new UnsupportedOperationException("Unsupported mime type: '" + mediaType + "'");
		}
	}

	@Override
	public void handleMessage(final Message outMessage) throws Fault
	{
		final Map<String, List<String>> headers = (Map<String, List<String>>) outMessage.get(Message.PROTOCOL_HEADERS);
		final List<String> contentTypeHeaders = headers.get(Message.CONTENT_TYPE);
		if (contentTypeHeaders != null)
		{
			contentTypeHeaders.clear();
			contentTypeHeaders.add(contentType);
		}

		/*
		 * There is also a Content-Type property in Message
		 */
		final String contentTypePropVal = (String) outMessage.get(Message.CONTENT_TYPE);
		if (contentTypePropVal != null)
		{
			outMessage.put(Message.CONTENT_TYPE, contentType);
		}

		final List<String> acceptHeaders = headers.get(Message.ACCEPT_CONTENT_TYPE);
		if (acceptHeaders == null)
		{
			headers.put(Message.ACCEPT_CONTENT_TYPE, acceptTypes);
		}
		else
		{
			acceptHeaders.clear();
			acceptHeaders.addAll(acceptTypes);
		}

	}
}
