package org.ow2.authzforce.webapp.json;

import java.io.Serializable;
import java.util.List;

import org.ow2.authzforce.core.pdp.api.value.AttributeValue;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

/**
 * 
 * Jackson Mix-in for adding Jackson annotation to XSD-generated class {@link AttributeValue} FIXME: remove this if not used in client.xml/beans.xml
 */
interface JacksonAnnotatedXACMLAttributeValueType
{
	@JsonSerialize(using = CollectionToSingleJsonStringSerializer.class)
	@JsonDeserialize(using = ListFromSingleJsonStringDeserializer.class)
	List<Serializable> getContent();
}
