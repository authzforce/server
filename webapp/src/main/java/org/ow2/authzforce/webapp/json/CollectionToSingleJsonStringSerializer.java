package org.ow2.authzforce.webapp.json;

import java.io.IOException;
import java.util.Collection;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StringSerializer;

/**
 * FIXME: merge into the only mix-in class using it
 * 
 * @author cdangerv
 *
 */
public class CollectionToSingleJsonStringSerializer extends JsonSerializer<Collection<?>>
{

	private static final JsonSerializer<Object> STRING_SERIALIZER = new StringSerializer();

	@Override
	public void serialize(final Collection<?> value, final JsonGenerator gen, final SerializerProvider serializers) throws IOException, JsonProcessingException
	{
		if (value.isEmpty())
		{
			gen.writeString("");
		}

		STRING_SERIALIZER.serialize(value.iterator().next(), gen, serializers);
	}

}
