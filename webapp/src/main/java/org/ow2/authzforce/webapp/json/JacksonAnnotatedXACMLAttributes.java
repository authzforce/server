package org.ow2.authzforce.webapp.json;

import oasis.names.tc.xacml._3_0.core.schema.wd_17.Content;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * 
 * Jackson Mix-in for adding Jackson annotation to XSD-generated class {@link Attributes} FIXME: remove this if not used in client.xml/beans.xml
 */
interface JacksonAnnotatedXACMLAttributes
{
	@JsonIgnore
	Content getContent();
}
