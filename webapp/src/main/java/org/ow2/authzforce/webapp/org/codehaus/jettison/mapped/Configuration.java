/**
 * Copyright 2006 Envoi Solutions LLC
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.ow2.authzforce.webapp.org.codehaus.jettison.mapped;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.codehaus.jettison.mapped.DefaultConverter;
import org.codehaus.jettison.mapped.TypeConverter;

/**
 * Required by {@link MappedNamespaceConvention} in the same package
 */
public class Configuration
{
	/* Were there a constants class, this key would live there. */
	private static final String JETTISON_TYPE_CONVERTER_CLASS_KEY = "jettison.mapped.typeconverter.class";

	/* Mostly exists to wrap exception handling for reflective invocations. */
	private static class ConverterFactory
	{
		TypeConverter newDefaultConverterInstance()
		{
			return new DefaultConverter();
		}
	}

	private static final ConverterFactory converterFactory;

	static
	{
		ConverterFactory cf = null;
		final String userSpecifiedClass = System.getProperty(JETTISON_TYPE_CONVERTER_CLASS_KEY);
		if (userSpecifiedClass != null && userSpecifiedClass.length() > 0)
		{
			try
			{
				final Class<? extends TypeConverter> tc = Class.forName(userSpecifiedClass).asSubclass(TypeConverter.class);
				tc.newInstance(); /* Blow up as soon as possible. */
				cf = new ConverterFactory()
				{
					@Override
					public TypeConverter newDefaultConverterInstance()
					{
						try
						{
							return tc.newInstance();
						}
						catch (final Exception e)
						{
							// Implementer of custom class would have to try pretty hard to make this happen,
							// since we already created one instance.
							throw new ExceptionInInitializerError(e);
						}
					}
				};
			}
			catch (final Exception e)
			{
				throw new ExceptionInInitializerError(e);
			}
		}
		if (cf == null)
		{
			cf = new ConverterFactory();
		}
		converterFactory = cf;
	}

	private Map xmlToJsonNamespaces;
	private List attributesAsElements;
	/*
	 * BEGIN CHANGE to transform specific JSON keys - not prefixed with attributeKey - to XML attributes (opposite of attributesAsElements)
	 */
	private Set<String> elementsAsAttributes = Collections.EMPTY_SET;
	/*
	 * END
	 */
	private List ignoredElements;
	private boolean supressAtAttributes;
	private String attributeKey = "@";
	private boolean ignoreNamespaces;
	private boolean dropRootElement;
	private Set primitiveArrayKeys = Collections.EMPTY_SET;
	private boolean writeNullAsString = true;
	private boolean readNullAsString;
	private boolean ignoreEmptyArrayValues;
	private boolean escapeForwardSlashAlways;
	private String jsonNamespaceSeparator;

	private TypeConverter typeConverter = converterFactory.newDefaultConverterInstance();

	public Configuration()
	{
		super();
		this.xmlToJsonNamespaces = new HashMap();
	}

	public Configuration(final Map xmlToJsonNamespaces)
	{
		super();
		this.xmlToJsonNamespaces = xmlToJsonNamespaces;
	}

	public Configuration(final Map xmlToJsonNamespaces, final List attributesAsElements, final List ignoredElements)
	{
		super();
		this.xmlToJsonNamespaces = xmlToJsonNamespaces;
		this.attributesAsElements = attributesAsElements;
		this.ignoredElements = ignoredElements;
	}

	public boolean isIgnoreNamespaces()
	{
		return ignoreNamespaces;
	}

	public void setIgnoreNamespaces(final boolean ignoreNamespaces)
	{
		this.ignoreNamespaces = ignoreNamespaces;
	}

	public List getAttributesAsElements()
	{
		return attributesAsElements;
	}

	public void setAttributesAsElements(final List attributesAsElements)
	{
		this.attributesAsElements = attributesAsElements;
	}

	public List getIgnoredElements()
	{
		return ignoredElements;
	}

	public void setIgnoredElements(final List ignoredElements)
	{
		this.ignoredElements = ignoredElements;
	}

	public Map getXmlToJsonNamespaces()
	{
		return xmlToJsonNamespaces;
	}

	public void setXmlToJsonNamespaces(final Map xmlToJsonNamespaces)
	{
		this.xmlToJsonNamespaces = xmlToJsonNamespaces;
	}

	public TypeConverter getTypeConverter()
	{
		return typeConverter;
	}

	public void setTypeConverter(final TypeConverter typeConverter)
	{
		this.typeConverter = typeConverter;
	}

	public boolean isSupressAtAttributes()
	{
		return this.supressAtAttributes;
	}

	public void setSupressAtAttributes(final boolean supressAtAttributes)
	{
		this.supressAtAttributes = supressAtAttributes;
	}

	public String getAttributeKey()
	{
		return this.attributeKey;
	}

	public void setAttributeKey(final String attributeKey)
	{
		this.attributeKey = attributeKey;
	}

	static TypeConverter newDefaultConverterInstance()
	{
		return converterFactory.newDefaultConverterInstance();
	}

	public Set getPrimitiveArrayKeys()
	{
		return primitiveArrayKeys;
	}

	public void setPrimitiveArrayKeys(final Set primitiveArrayKeys)
	{
		this.primitiveArrayKeys = primitiveArrayKeys;
	}

	public boolean isDropRootElement()
	{
		return dropRootElement;
	}

	public void setDropRootElement(final boolean dropRootElement)
	{
		this.dropRootElement = dropRootElement;
	}

	public boolean isWriteNullAsString()
	{
		return writeNullAsString;
	}

	public void setWriteNullAsString(final boolean writeNullAsString)
	{
		this.writeNullAsString = writeNullAsString;
	}

	public boolean isReadNullAsString()
	{
		return readNullAsString;
	}

	public void setReadNullAsString(final boolean readNullString)
	{
		this.readNullAsString = readNullString;
	}

	public boolean isIgnoreEmptyArrayValues()
	{
		return ignoreEmptyArrayValues;
	}

	public void setIgnoreEmptyArrayValues(final boolean ignoreEmptyArrayValues)
	{
		this.ignoreEmptyArrayValues = ignoreEmptyArrayValues;
	}

	@Deprecated
	public void setReadNullAsEmptyString(final boolean read)
	{
	}

	public boolean isEscapeForwardSlashAlways()
	{
		return escapeForwardSlashAlways;
	}

	public void setEscapeForwardSlashAlways(final boolean escapeForwardSlash)
	{
		this.escapeForwardSlashAlways = escapeForwardSlash;
	}

	public String getJsonNamespaceSeparator()
	{
		return jsonNamespaceSeparator;
	}

	public void setJsonNamespaceSeparator(final String jsonNamespaceSeparator)
	{
		this.jsonNamespaceSeparator = jsonNamespaceSeparator;
	}

	/*
	 * BEGIN CHANGE to transform specific JSON keys - not starting with $attributeKey - to attributes (opposite of attributesAsElements)
	 */
	public Set<String> getElementsAsAttributes()
	{
		return this.elementsAsAttributes;
	}

	public void setElementsAsAttributes(final Set<String> elementsAsAttributes)
	{
		this.elementsAsAttributes = elementsAsAttributes;
	}
	/*
	 * END
	 */
}
