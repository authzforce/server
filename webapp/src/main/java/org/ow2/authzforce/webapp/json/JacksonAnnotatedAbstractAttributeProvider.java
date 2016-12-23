package org.ow2.authzforce.webapp.json;

import org.ow2.authzforce.xmlns.pdp.ext.AbstractAttributeProvider;

import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * 
 * Jackson Mix-in for adding Jackson annotations to XSD-generated class {@link AbstractAttributeProvider}
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, include = JsonTypeInfo.As.PROPERTY, property = "@class")
interface JacksonAnnotatedAbstractAttributeProvider
{
	/*
	 * AbstractAttributeProvider is a marker interface, no setter/getter
	 */
}
