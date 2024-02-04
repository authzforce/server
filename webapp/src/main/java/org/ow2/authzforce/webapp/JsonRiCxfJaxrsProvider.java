/*
 * Copyright (C) 2012-2024 THALES.
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

import org.apache.cxf.jaxrs.provider.AbstractConfigurableProvider;
import org.everit.json.schema.Schema;
import org.everit.json.schema.ValidationException;
import org.everit.json.schema.loader.SchemaLoader;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.ow2.authzforce.jaxrs.util.JaxbErrorMessage;
import org.ow2.authzforce.jaxrs.util.JsonRiJaxrsProvider;
import org.ow2.authzforce.xacml.json.model.LimitsCheckingJSONObject;
import org.ow2.authzforce.xacml.json.model.SpringBasedJsonSchemaClient;
import org.springframework.util.ResourceUtils;

import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ClientErrorException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response.Status;
import jakarta.ws.rs.ext.MessageBodyReader;
import jakarta.ws.rs.ext.MessageBodyWriter;
import jakarta.ws.rs.ext.Provider;
import java.beans.ConstructorProperties;
import java.io.*;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;

/**
 * JAX-RS entity provider for {@link JSONObject} input/output with configurable Consume/Produce media types and optional buffering
 * <p>
 * TODO: this is copy-paste from {@link org.ow2.authzforce.jaxrs.util.JsonRiJaxrsProvider} class (authzforce-ce-jaxrs-utils project), except this one handles {@link JaxbErrorMessage},
 * and extends CXF-specific {@link AbstractConfigurableProvider} to allow configuration of Consume/Produce media types and use of this info at runtime. See how we can reuse in one way or the other.
 */
@Provider
public final class JsonRiCxfJaxrsProvider<T> extends AbstractConfigurableProvider implements MessageBodyReader<JSONObject>, MessageBodyWriter<T>
{
	private static final BadRequestException EMPTY_JSON_OBJECT_BAD_REQUEST_EXCEPTION = new BadRequestException("object cannot be empty");

	private interface JsonObjectFactory
	{
		JSONObject getInstance(final InputStream entityStream);
	}

	private static class BaseJsonObjectFactory implements JsonObjectFactory
	{
		protected JSONObject parse(final InputStream entityStream)
		{
			return new JSONObject(new JSONTokener(entityStream));
		}

		protected void schemaValidate(final JSONObject jsonObj)
		{
			// no validation
		}

		@Override
		public final JSONObject getInstance(final InputStream entityStream) throws ValidationException
		{
			final JSONObject jsonObj = parse(entityStream);
			schemaValidate(jsonObj);
			return jsonObj;
		}

	}

	private static final JsonObjectFactory DEFAULT_JSON_TOKENER_FACTORY = entityStream -> new JSONObject(new JSONTokener(entityStream));

	/**
	 * Utility function to be used in Spring config to load JSON schema from file
	 * @param schemaLocation schema location
	 * @return JSON Schema
	 * @throws IOException error accessing schema file
	 */
	public static Schema loadSchema(final String schemaLocation) throws IOException
	{
		final File schemaFile = ResourceUtils.getFile(schemaLocation);
		try (BufferedReader reader = Files.newBufferedReader(schemaFile.toPath(), StandardCharsets.UTF_8))
		{
			final JSONObject rawSchema = new JSONObject(new JSONTokener(reader));
			return SchemaLoader.builder().schemaJson(rawSchema).resolutionScope("file://"+schemaFile.getParent()+ File.separator).schemaClient(new SpringBasedJsonSchemaClient()).build().load().build();
		}
	}

	private final JsonObjectFactory jsonObjectFactory;

	/**
	 * Constructs JSON provider using default insecure {@link JSONTokener}. Only for trusted environments or protected by JSON-threat-mitigating proxy (e.g. WAF as in Web Application Firewall)
	 */
	public JsonRiCxfJaxrsProvider()
	{
		jsonObjectFactory = DEFAULT_JSON_TOKENER_FACTORY;
	}

	/**
	 * Constructs JSON provider using default insecure {@link JSONTokener} with single JSON schema validation. Only for trusted environments or protected by JSON-threat-mitigating proxy (e.g. WAF as
	 * in Web Application Firewall)
	 *
	 * @param schema
	 *            JSON schema, null iff no schema validation shall occur
	 */
	public JsonRiCxfJaxrsProvider(final Schema schema)
	{
		jsonObjectFactory = schema == null ? DEFAULT_JSON_TOKENER_FACTORY : new BaseJsonObjectFactory()
		{

			@Override
			protected void schemaValidate(final JSONObject jsonObj) throws ValidationException
			{
				schema.validate(jsonObj);
			}

		};
	}

	/**
	 * Constructs JSON provider using default insecure {@link JSONTokener} with validation against a given schema depending on the input JSON root property. Only for trusted environments or protected
	 * by JSON-threat-mitigating proxy (e.g. WAF as in Web Application Firewall).
	 *
	 * @param schemasByPropertyName
	 *            mappings of JSON property names to schemas, defining which schema to apply according to which (root) property the input JSON object has; if {@code schemasByPropertyName} is empty, or
	 *            {@code schemasByPropertyName} does not contain any schema for the input JSON (root) property, no schema validation shall occur. Any input JSON without any root property is considered
	 *            invalid.
	 */
	public JsonRiCxfJaxrsProvider(final Map<String, Schema> schemasByPropertyName)
	{
		jsonObjectFactory = schemasByPropertyName == null || schemasByPropertyName.isEmpty() ? DEFAULT_JSON_TOKENER_FACTORY : new BaseJsonObjectFactory()
		{

			@Override
			protected void schemaValidate(final JSONObject jsonObj) throws ValidationException
			{
				final Iterator<String> keysIt = jsonObj.keys();
				if (!keysIt.hasNext())
				{
					/*
					 * JSONException extends RuntimeException so it is not caught as IllegalArgumentException
					 */
					throw EMPTY_JSON_OBJECT_BAD_REQUEST_EXCEPTION;
				}

				final Schema schema = schemasByPropertyName.get(keysIt.next());
				if (schema != null)
				{
					schema.validate(jsonObj);
				}
			}

		};
	}

	private static class LimitsCheckingJsonObjectFactory extends BaseJsonObjectFactory
	{
		private final int maxJsonStringSize;
		private final int maxNumOfImmediateChildren;
		private final int maxDepth;

		private LimitsCheckingJsonObjectFactory(final int maxJsonStringSize, final int maxNumOfImmediateChildren, final int maxDepth)
		{
			this.maxJsonStringSize = maxJsonStringSize;
			this.maxNumOfImmediateChildren = maxNumOfImmediateChildren;
			this.maxDepth = maxDepth;
		}

		@Override
		protected final JSONObject parse(final InputStream entityStream)
		{
			return new LimitsCheckingJSONObject(new InputStreamReader(entityStream, StandardCharsets.UTF_8), maxJsonStringSize, maxNumOfImmediateChildren, maxDepth);
		}

	}


	/**
	 * Constructs JSON provider using hardened {@link JSONTokener} that checks limits on JSON structures, such as arrays and strings, in order to mitigate content-level attacks. Downside: it is slower
	 * at parsing than for {@link JsonRiJaxrsProvider#JsonRiJaxrsProvider()}.
	 *
	 * @param schema
	 *            JSON schema, null means no schema validation
	 *
	 * @param maxJsonStringSize
	 *            allowed maximum size of JSON keys and string values. Negative or zero values disable limit checking altogether (the other max*** arguments have no effect).
	 * @param maxNumOfImmediateChildren
	 *            allowed maximum number of keys (therefore key-value pairs) in JSON object, or items in JSON array. egative or zero values disable limit checking altogether (the other max*** arguments have no effect).
	 * @param maxDepth
	 *            allowed maximum depth of JSON object. egative or zero values disable limit checking altogether (the other max*** arguments have no effect).
	 */
	@ConstructorProperties({"schema", "maxJsonStringSize", "maxNumOfImmediateChildren", "maxDepth" })
	public JsonRiCxfJaxrsProvider(final Schema schema, final int maxJsonStringSize, final int maxNumOfImmediateChildren, final int maxDepth)
	{
		if (maxJsonStringSize <= 0 || maxNumOfImmediateChildren <= 0 || maxDepth <= 0)
		{
			// limit checking disabled
			jsonObjectFactory = new BaseJsonObjectFactory() {
				@Override
				protected void schemaValidate(final JSONObject jsonObj) throws ValidationException
				{
					schema.validate(jsonObj);
				}
			};
		} else
		{
			jsonObjectFactory = schema == null ? new LimitsCheckingJsonObjectFactory(maxJsonStringSize, maxNumOfImmediateChildren, maxDepth)
					: new LimitsCheckingJsonObjectFactory(maxJsonStringSize, maxNumOfImmediateChildren, maxDepth)
			{
				@Override
				protected void schemaValidate(final JSONObject jsonObj) throws ValidationException
				{
					schema.validate(jsonObj);
				}

			};
		}
	}

	/**
	 * Constructs JSON provider using hardened {@link JSONTokener} that checks limits on JSON structures, such as arrays and strings, in order to mitigate content-level attacks. Downside: it is slower
	 * at parsing than for {@link JsonRiCxfJaxrsProvider#JsonRiCxfJaxrsProvider()}.  This provider also validates input JSON against a given schema depending on the input JSON root property.
	 *
	 * @param schemasByRootKey
	 * 	 *            mappings of JSON property names to schemas, defining which schema to apply according to which (root) property the input JSON object has; if {@code schemasByRootKey} is empty, or
	 * 	 *            {@code schemasByRootKey} does not contain any schema for the input JSON (root) property, no schema validation shall occur. Any input JSON without any root property is considered
	 * 	 *            invalid.
	 *
	 * @param maxJsonStringSize
	 *            allowed maximum size of JSON keys and string values. If negative or zero, limits are ignored and this is equivalent to {@link JsonRiCxfJaxrsProvider#JsonRiCxfJaxrsProvider()}.
	 * @param maxNumOfImmediateChildren
	 *            allowed maximum number of keys (therefore key-value pairs) in JSON object, or items in JSON array. If negative or zero, limits are ignored and this is equivalent to
	 *            {@link JsonRiCxfJaxrsProvider#JsonRiCxfJaxrsProvider()}.
	 * @param maxDepth
	 *            allowed maximum depth of JSON object. If negative or zero, limits are ignored and this is equivalent to {@link JsonRiCxfJaxrsProvider#JsonRiCxfJaxrsProvider()}.
	 */
	@ConstructorProperties({"schemasByRootKey", "maxJsonStringSize", "maxNumOfImmediateChildren", "maxDepth" })
	public JsonRiCxfJaxrsProvider(final Map<String, Schema> schemasByRootKey, final int maxJsonStringSize, final int maxNumOfImmediateChildren, final int maxDepth)
	{
		if (maxJsonStringSize <= 0 || maxNumOfImmediateChildren <= 0 || maxDepth <= 0)
		{
			if(schemasByRootKey == null || schemasByRootKey.isEmpty() ) {
				jsonObjectFactory = DEFAULT_JSON_TOKENER_FACTORY;
			} else {
				jsonObjectFactory = new BaseJsonObjectFactory() {
					@Override
					protected void schemaValidate(final JSONObject jsonObj) throws ValidationException
					{
						final Iterator<String> keysIt = jsonObj.keys();
						if (!keysIt.hasNext())
						{
							/*
							 * JSONException extends RuntimeException so it is not caught as IllegalArgumentException
							 */
							throw EMPTY_JSON_OBJECT_BAD_REQUEST_EXCEPTION;
						}

						final Schema schema = schemasByRootKey.get(keysIt.next());
						if (schema != null)
						{
							schema.validate(jsonObj);
						}
					}
				};
			}
		}
		else
		{
			jsonObjectFactory = schemasByRootKey == null || schemasByRootKey.isEmpty() ? new LimitsCheckingJsonObjectFactory(maxJsonStringSize, maxNumOfImmediateChildren, maxDepth)
					: new LimitsCheckingJsonObjectFactory(maxJsonStringSize, maxNumOfImmediateChildren, maxDepth)
			{
				@Override
				protected void schemaValidate(final JSONObject jsonObj) throws ValidationException
				{
					final Iterator<String> keysIt = jsonObj.keys();
					if (!keysIt.hasNext())
					{
						/*
						 * JSONException extends RuntimeException so it is not caught as IllegalArgumentException
						 */
						throw EMPTY_JSON_OBJECT_BAD_REQUEST_EXCEPTION;
					}

					final Schema schema = schemasByRootKey.get(keysIt.next());
					if (schema != null)
					{
						schema.validate(jsonObj);
					}
				}

			};
		}
	}

	@Override
	public boolean isWriteable(final Class<?> type, final Type genericType, final Annotation[] annotations, final MediaType mediaType)
	{
		return JSONObject.class.isAssignableFrom(type) || type == JaxbErrorMessage.class;
	}

	@Override
	public long getSize(final T o, final Class<?> type, final Type genericType, final Annotation[] annotations, final MediaType mediaType)
	{
		return -1;
	}

	@Override
	public void writeTo(final T o, final Class<?> type, final Type genericType, final Annotation[] annotations, final MediaType mediaType, final MultivaluedMap<String, Object> httpHeaders,
			final OutputStream entityStream) throws IOException, WebApplicationException
	{
		final JSONObject json;
		if (o instanceof JSONObject)
		{
			json = (JSONObject) o;
		}
		else if (o instanceof JaxbErrorMessage)
		{
			final JaxbErrorMessage errMsg = (JaxbErrorMessage) o;
			json = new JSONObject(Collections.singletonMap("error", errMsg.getMessage()));
		}
		else
		{
			throw new RuntimeException("Unexpected input object class to MessageBodyWriter '" + this.getClass() + "': " + o.getClass());
		}
		try (OutputStreamWriter writer = new OutputStreamWriter(entityStream, StandardCharsets.UTF_8))
		{
			json.write(writer);
		}
	}

	@Override
	public boolean isReadable(final Class<?> type, final Type genericType, final Annotation[] annotations, final MediaType mediaType)
	{
		return JSONObject.class.isAssignableFrom(type);
	}

	@Override
	public JSONObject readFrom(final Class<JSONObject> type, final Type genericType, final Annotation[] annotations, final MediaType mediaType, final MultivaluedMap<String, String> httpHeaders,
			final InputStream entityStream) throws WebApplicationException
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
