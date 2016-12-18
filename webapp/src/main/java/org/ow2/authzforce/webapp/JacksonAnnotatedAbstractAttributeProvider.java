package org.ow2.authzforce.webapp;

import org.ow2.authzforce.xmlns.pdp.ext.AbstractAttributeProvider;

import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * 
 * Jackson Mix-in for adding Jackson annotations to XSD-generated class {@link AbstractAttributeProvider} JsonSubtypes annotation needed for deserializing instances of PDP extensions (JAXB @XmlSeeAlso
 * does not help since not available during pdp-ext schema compilation)
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, include = JsonTypeInfo.As.PROPERTY, property = "@class")
// @JsonSubTypes({ @Type(value = TestAttributeProvider.class) })
interface JacksonAnnotatedAbstractAttributeProvider
{
	/*
	 * AbstractAttributeProvider is a marker interface, no setter/getter
	 */
}
