package org.ow2.authzforce.webapp.json;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import oasis.names.tc.xacml._3_0.core.schema.wd_17.Policy;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.deser.UnresolvedForwardReference;
import com.fasterxml.jackson.databind.deser.std.ContainerDeserializerBase;
import com.fasterxml.jackson.databind.deser.std.UntypedObjectDeserializer;
import com.fasterxml.jackson.databind.type.CollectionType;
import com.fasterxml.jackson.databind.type.TypeFactory;

public class ListFromJsonArrayDeserializer extends ContainerDeserializerBase<List<Serializable>>
{
	private static final CollectionType LIST_OF_SERIALIZABLE_TYPE = TypeFactory.defaultInstance().constructCollectionType(List.class, Serializable.class);

	private static final JavaType POLICY_SET_TYPE = TypeFactory.defaultInstance().constructSimpleType(Policy.class, null);

	protected ListFromJsonArrayDeserializer()
	{
		super(LIST_OF_SERIALIZABLE_TYPE);
	}

	/**
	 * Helper method called when current token is no START_ARRAY. Will either throw an exception, or try to handle value as if member of implicit array, depending on configuration.
	 * 
	 * Adapted from CollectionDeserializer class
	 * 
	 * @throws IOException
	 *
	 */
	@SuppressWarnings("unchecked")
	protected final List<Serializable> handleNonArray(final JsonParser p, final DeserializationContext ctxt, final List<Serializable> result) throws IOException
	{
		// Implicit arrays from single values?
		// boolean canWrap = (this._unwrapSingle == Boolean.TRUE) || ((_unwrapSingle == null) && ctxt.isEnabled(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY));
		// if (!canWrap)
		// {
		return (List<Serializable>) ctxt.handleUnexpectedToken(List.class, p);
		// }
		// JsonDeserializer<Object> valueDes = _valueDeserializer;
		// final TypeDeserializer typeDeser = _valueTypeDeserializer;
		// JsonToken t = p.getCurrentToken();
		//
		// Object value;
		//
		// try
		// {
		// if (t == JsonToken.VALUE_NULL)
		// {
		// value = valueDes.getNullValue(ctxt);
		// }
		// else if (typeDeser == null)
		// {
		// value = valueDes.deserialize(p, ctxt);
		// }
		// else
		// {
		// value = valueDes.deserializeWithType(p, ctxt, typeDeser);
		// }
		// }
		// catch (Exception e)
		// {
		// // note: pass Object.class, not Object[].class, as we need element type for error info
		// throw JsonMappingException.wrapWithPath(e, Object.class, result.size());
		// }
		// result.add(value);
		// return result;
	}

	@Override
	public List<Serializable> deserialize(final JsonParser p, final DeserializationContext ctxt) throws IOException
	{
		final List<Serializable> result = new ArrayList<>();
		/*
		 * Adapted from CollectionDeserializer class
		 */
		// Ok: must point to START_ARRAY (or equivalent)
		if (!p.isExpectedStartArrayToken())
		{
			return handleNonArray(p, ctxt, result);
		}
		// [databind#631]: Assign current value, to be accessible by custom serializers
		p.setCurrentValue(result);

		final JsonDeserializer<Object> valueDes = ctxt.findNonContextualValueDeserializer(POLICY_SET_TYPE) /* this.getContentDeserializer() */;
		// final TypeDeserializer typeDeser = _valueTypeDeserializer;
		// final CollectionReferringAccumulator referringAccumulator = (valueDes.getObjectIdReader() == null) ? null : new CollectionReferringAccumulator(LIST_OF_SERIALIZABLE_TYPE.getContentType()
		// .getRawClass(), (Collection<Object>) result);

		JsonToken t;
		while ((t = p.nextToken()) != JsonToken.END_ARRAY)
		{
			try
			{
				final Serializable value;
				if (t == JsonToken.VALUE_NULL)
				{
					value = (Serializable) valueDes.getNullValue(ctxt);
				}
				else
				/* if (typeDeser == null) */
				{
					value = (Serializable) valueDes.deserialize(p, ctxt);
				}
				// else
				// {
				// value = valueDes.deserializeWithType(p, ctxt, typeDeser);
				// }
				// if (referringAccumulator != null)
				// {
				// referringAccumulator.add(value);
				// }
				// else
				// {
				result.add(value);
				// }
			}
			catch (final UnresolvedForwardReference reference)
			{
				// if (referringAccumulator == null)
				// {
				throw JsonMappingException.from(p, "Unresolved forward reference but no identity info", reference);
				// }
				// final Referring ref = referringAccumulator.handleUnresolvedReference(reference);
				// reference.getRoid().appendReferring(ref);
			}
			catch (final Exception e)
			{
				final boolean wrap = (ctxt == null) || ctxt.isEnabled(DeserializationFeature.WRAP_EXCEPTIONS);
				if (!wrap && e instanceof RuntimeException)
				{
					throw (RuntimeException) e;
				}
				throw JsonMappingException.wrapWithPath(e, result, result.size());
			}
		}
		return result;
	}

	@Override
	public JavaType getContentType()
	{
		return LIST_OF_SERIALIZABLE_TYPE.getContentType();
	}

	@Override
	public JsonDeserializer<Object> getContentDeserializer()
	{
		return new UntypedObjectDeserializer(null, null);
	}
}
