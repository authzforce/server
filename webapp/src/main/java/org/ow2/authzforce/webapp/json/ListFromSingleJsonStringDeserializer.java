package org.ow2.authzforce.webapp.json;

import java.io.IOException;
import java.io.Serializable;
import java.util.Collections;
import java.util.List;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.deser.std.StringDeserializer;

/**
 * FIXME: merge into the only mix-in class using it
 * 
 * @author cdangerv
 *
 */
public class ListFromSingleJsonStringDeserializer extends JsonDeserializer<List<Serializable>>
{
	@Override
	public List<Serializable> deserialize(final JsonParser p, final DeserializationContext ctxt) throws IOException, JsonProcessingException
	{
		final String str = StringDeserializer.instance.deserialize(p, ctxt);
		return Collections.singletonList(str);
	}

}
