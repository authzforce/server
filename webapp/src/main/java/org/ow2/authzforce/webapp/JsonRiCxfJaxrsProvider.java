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

import java.beans.ConstructorProperties;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;

import javax.ws.rs.BadRequestException;
import javax.ws.rs.ClientErrorException;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.ext.MessageBodyReader;
import javax.ws.rs.ext.MessageBodyWriter;
import javax.ws.rs.ext.Provider;

import org.apache.cxf.jaxrs.provider.AbstractConfigurableProvider;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.ow2.authzforce.xacml.json.model.LimitsCheckingJSONObject;

/**
 * JAX-RS entity provider for {@link JSONObject} input/output with configurable Consume/Produce media types and optional buffering
 * <p>
 * TODO: this is copy-paste from org.ow2.authzforce.core.pdp.xacml.json.jaxrs.JsonRiJaxrsProvider class (authzforce-ce-jaxrs-pdp-xacml-json project), except this one extends CXF-specific
 * {@link AbstractConfigurableProvider} to allow configuration of Consume/Produce media types and use of this info at runtime. See how we can reuse in one way or the other.
 */
@Provider
public final class JsonRiCxfJaxrsProvider extends AbstractConfigurableProvider implements MessageBodyReader<JSONObject>, MessageBodyWriter<JSONObject>
{
	private interface JSONObjectFactory
	{
		JSONObject getInstance(final InputStream entityStream);
	}

	private static final JSONObjectFactory DEFAULT_JSON_TOKENER_FACTORY = new JSONObjectFactory()
	{
		@Override
		public JSONObject getInstance(final InputStream entityStream)
		{
			return new JSONObject(new JSONTokener(entityStream));
		}
	};

	private final JSONObjectFactory jsonObjectFactory;

	/**
	 * Constructs JSON provider using default insecure {@link JSONTokener}. Only for trusted environments or protected by JSON-threat-mitigating proxy (e.g. WAF as in Web Application Firewall)
	 */
	public JsonRiCxfJaxrsProvider()
	{
		jsonObjectFactory = DEFAULT_JSON_TOKENER_FACTORY;
	}

	/**
	 * Constructs JSON provider using hardened {@link JSONTokener} that checks limits on JSON structures, such as arrays and strings, in order to mitigate content-level attacks. Downside: it is slower
	 * at parsing than for {@link JsonRiCxfJaxrsProvider#JsonRiJaxrsProvider()}.
	 * 
	 * @param maxJsonStringSize
	 *            allowed maximum size of JSON keys and string values. If negative or zero, limits are ignored and this is equivalent to {@link JsonRiCxfJaxrsProvider#JsonRiJaxrsProvider()}.
	 * @param maxNumOfImmediateChildren
	 *            allowed maximum number of keys (therefore key-value pairs) in JSON object, or items in JSON array. If negative or zero, limits are ignored and this is equivalent to
	 *            {@link JsonRiCxfJaxrsProvider#JsonRiJaxrsProvider()}.
	 * @param maxDepth
	 *            allowed maximum depth of JSON object. If negative or zero, limits are ignored and this is equivalent to {@link JsonRiCxfJaxrsProvider#JsonRiJaxrsProvider()}.
	 */
	@ConstructorProperties({ "maxJsonStringSize", "maxNumOfImmediateChildren", "maxDepth" })
	public JsonRiCxfJaxrsProvider(final int maxJsonStringSize, final int maxNumOfImmediateChildren, final int maxDepth)
	{
		if (maxJsonStringSize <= 0 || maxNumOfImmediateChildren <= 0 || maxDepth <= 0)
		{
			jsonObjectFactory = DEFAULT_JSON_TOKENER_FACTORY;
		}
		else
		{
			jsonObjectFactory = new JSONObjectFactory()
			{

				@Override
				public JSONObject getInstance(final InputStream entityStream)
				{
					return new LimitsCheckingJSONObject(entityStream, maxJsonStringSize, maxNumOfImmediateChildren, maxDepth);
				}
			};
		}
	}

	@Override
	public boolean isWriteable(final Class<?> type, final Type genericType, final Annotation[] annotations, final MediaType mediaType)
	{
		return JSONObject.class.isAssignableFrom(type);
	}

	@Override
	public long getSize(final JSONObject o, final Class<?> type, final Type genericType, final Annotation[] annotations, final MediaType mediaType)
	{
		return -1;
	}

	@Override
	public void writeTo(final JSONObject o, final Class<?> type, final Type genericType, final Annotation[] annotations, final MediaType mediaType, final MultivaluedMap<String, Object> httpHeaders,
			final OutputStream entityStream) throws IOException, WebApplicationException
	{
		final OutputStreamWriter writer = new OutputStreamWriter(entityStream, StandardCharsets.UTF_8);
		o.write(writer);
		writer.close();
	}

	@Override
	public boolean isReadable(final Class<?> type, final Type genericType, final Annotation[] annotations, final MediaType mediaType)
	{
		return JSONObject.class.isAssignableFrom(type);
	}

	@Override
	public JSONObject readFrom(final Class<JSONObject> type, final Type genericType, final Annotation[] annotations, final MediaType mediaType, final MultivaluedMap<String, String> httpHeaders,
			final InputStream entityStream) throws IOException, WebApplicationException
	{
		try
		{
			return jsonObjectFactory.getInstance(entityStream);
		}
		catch (final JSONException e)
		{
			/*
			 * JSONException extends RuntimeException so it is not caught as IllegalArgumentException
			 */
			throw new BadRequestException(e);
		}
		catch (final IllegalArgumentException e)
		{
			// exception related to limits checking
			throw new ClientErrorException(Status.REQUEST_ENTITY_TOO_LARGE, e);
		}
	}

}
