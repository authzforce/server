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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import javax.xml.XMLConstants;
import javax.xml.namespace.NamespaceContext;
import javax.xml.namespace.QName;

import org.codehaus.jettison.Convention;
import org.codehaus.jettison.Node;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.codehaus.jettison.mapped.TypeConverter;
import org.ow2.authzforce.webapp.org.apache.cxf.jaxrs.provider.json.utils.JSONUtils;

/**
 * From Jettison's {@link org.codehaus.jettison.mapped.MappedNamespaceConvention}, modified to fix issue with XML schema validation failing when something like xsi:type="tns:MyType" is used in the
 * JSON input ("Undeclared prefix 'tns'").
 * <p>
 * TODO: report this issue to Jettison project
 *
 */
public class MappedNamespaceConvention implements Convention, NamespaceContext
{
	private static final String DOT_NAMESPACE_SEP = ".";
	private Map<Object, Object> xnsToJns = new HashMap<>();
	private final Map<Object, Object> jnsToXns = new HashMap<>();
	private List<?> attributesAsElements;
	private List<?> ignoredElements;
	private List<String> jsonAttributesAsElements;
	private boolean supressAtAttributes;
	private boolean ignoreNamespaces;
	private String attributeKey = "@";
	private TypeConverter typeConverter;
	private Set<?> primitiveArrayKeys;
	private boolean dropRootElement;
	private boolean writeNullAsString = true;
	private boolean ignoreEmptyArrayValues;
	private boolean readNullAsString;
	private boolean escapeForwardSlashAlways;
	private String jsonNamespaceSeparator;
	private Set<String> elementsAsAttributes;

	public MappedNamespaceConvention()
	{
		super();
		typeConverter = Configuration.newDefaultConverterInstance();
	}

	public MappedNamespaceConvention(final Configuration config)
	{
		super();
		this.xnsToJns = config.getXmlToJsonNamespaces();
		this.attributesAsElements = config.getAttributesAsElements();
		this.elementsAsAttributes = config.getElementsAsAttributes();
		this.supressAtAttributes = config.isSupressAtAttributes();
		this.ignoreNamespaces = config.isIgnoreNamespaces();
		this.dropRootElement = config.isDropRootElement();
		this.attributeKey = config.getAttributeKey();
		this.primitiveArrayKeys = config.getPrimitiveArrayKeys();
		this.ignoredElements = config.getIgnoredElements();
		this.ignoreEmptyArrayValues = config.isIgnoreEmptyArrayValues();
		this.escapeForwardSlashAlways = config.isEscapeForwardSlashAlways();
		this.jsonNamespaceSeparator = config.getJsonNamespaceSeparator();
		for (final Entry<Object, Object> entry2 : xnsToJns.entrySet())
		{
			final Map.Entry<?, ?> entry = entry2;
			jnsToXns.put(entry.getValue(), entry.getKey());
		}

		jsonAttributesAsElements = new ArrayList<String>();
		if (attributesAsElements != null)
		{
			for (final Object name : attributesAsElements)
			{
				final QName q = (QName) name;
				jsonAttributesAsElements.add(createAttributeKey(q.getPrefix(), q.getNamespaceURI(), q.getLocalPart()));
			}
		}
		this.readNullAsString = config.isReadNullAsString();
		this.writeNullAsString = config.isWriteNullAsString();
		typeConverter = config.getTypeConverter();
		if (!writeNullAsString && typeConverter != null)
		{
			typeConverter = new NullStringConverter(typeConverter);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.codehaus.xson.mapped.Convention#processNamespaces(org.codehaus.xson.Node, org.json.JSONObject)
	 */
	@Override
	public void processAttributesAndNamespaces(final Node n, final JSONObject object) throws JSONException
	{

		// Read in the attributes, and stop when there are no more
		for (final Iterator<?> itr = object.keys(); itr.hasNext();)
		{
			String k = (String) itr.next();

			if (this.supressAtAttributes)
			{
				if (k.startsWith(attributeKey))
				{
					k = k.substring(1);
				}
				if (null == this.jsonAttributesAsElements)
				{
					this.jsonAttributesAsElements = new ArrayList<>();
				}
				if (!this.jsonAttributesAsElements.contains(k))
				{
					this.jsonAttributesAsElements.add(k);
				}
			}

			/*
			 * BEGIN CHANGE to handle new property 'elementsAsAttributes'
			 */
			/*
			 * value for current JSON key, not null iff it is to be handled as XML attribute value, i.e. either key starts with special attribute prefix, e.g. '@'; or the key is in
			 * elementsAsAttributes
			 */
			final Object o;
			/*
			 * 
			 */
			if (k.startsWith(attributeKey))
			{
				o = object.opt(k);
				// String value = object.optString( k );
				k = k.substring(1);
			}
			else if (elementsAsAttributes.contains(k))
			{
				o = object.opt(k);
			}
			else
			{
				o = null;
			}

			if (o != null)
			{
				if (k.equals("xmlns"))
				{
					// if its a string its a default namespace
					if (o instanceof JSONObject)
					{
						final JSONObject jo = (JSONObject) o;
						for (final Iterator<?> pitr = jo.keys(); pitr.hasNext();)
						{
							// set namespace if one is specified on this attribute.
							final String prefix = (String) pitr.next();
							final String uri = jo.getString(prefix);

							// if ( prefix.equals( "$" ) ) {
							// prefix = "";
							// }

							n.setNamespace(prefix, uri);
						}
					}
				}
				else
				{
					final String strValue = o == null ? null : o.toString();
					QName name = null;
					// note that a non-prefixed attribute name implies NO namespace,
					// i.e. as opposed to the in-scope default namespace
					if (k.contains(getNamespaceSeparator()))
					{
						name = createQName(k, n);
						/*
						 * BEGIN CHANGE to FIX ISSUE "Undeclared prefix 'tns'", when validating XML schema on @xsi:type="tns:MyType",
						 */
						if (strValue != null && name.equals(JSONUtils.XSI_TYPE_QNAME))
						{
							// final QName xsiTypeQName = createQName(strValue);
							final int nsSepPosition = strValue.lastIndexOf(getNamespaceSeparator());
							final String jns = nsSepPosition == -1 ? "" : strValue.substring(0, nsSepPosition);
							final String xns = getNamespaceURI(jns);
							if (xns == null)
							{
								throw new JSONException("Unknown XML namespace for prefix used in @xsi:type value: '" + jns + "'");
							}

							n.setNamespace(jns, xns);
						}
						/*
						 * END CHANGE
						 */
					}
					else
					{
						name = new QName(XMLConstants.DEFAULT_NS_PREFIX, k);
					}
					n.setAttribute(name, strValue);
				}
				itr.remove();
			}
			else
			{
				// set namespace if one is specified on this attribute.
				final int dot = k.lastIndexOf(getNamespaceSeparator());
				if (dot != -1)
				{
					final String jns = k.substring(0, dot);
					final String xns = getNamespaceURI(jns);
					n.setNamespace("", xns);
				}
			}
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see javax.xml.namespace.NamespaceContext#getNamespaceURI(java.lang.String)
	 */
	@Override
	public String getNamespaceURI(final String prefix)
	{

		if (ignoreNamespaces)
		{
			return "";
		}

		return (String) jnsToXns.get(prefix);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see javax.xml.namespace.NamespaceContext#getPrefix(java.lang.String)
	 */
	@Override
	public String getPrefix(final String namespaceURI)
	{

		if (ignoreNamespaces)
		{
			return "";
		}

		return (String) xnsToJns.get(namespaceURI);
	}

	@Override
	public Iterator<Object> getPrefixes(final String arg0)
	{

		if (ignoreNamespaces)
		{
			return Collections.emptySet().iterator();
		}

		return jnsToXns.keySet().iterator();
	}

	@Override
	public QName createQName(final String rootName, final Node node)
	{

		return createQName(rootName);
	}

	@SuppressWarnings("unused")
	private void readAttribute(final Node n, final String k, final JSONArray array) throws JSONException
	{

		for (int i = 0; i < array.length(); i++)
		{
			readAttribute(n, k, array.getString(i));
		}
	}

	private void readAttribute(final Node n, final String name, final String value) throws JSONException
	{

		final QName qname = createQName(name);
		n.getAttributes().put(qname, value);
	}

	private QName createQName(final String name)
	{

		final String nsSeparator = getNamespaceSeparator();
		int dot = name.lastIndexOf(nsSeparator);
		QName qname = null;
		String local = name;

		if (dot == -1)
		{
			dot = 0;
		}
		else
		{
			local = local.substring(dot + nsSeparator.length());
		}

		final String jns = name.substring(0, dot);
		final String xns = getNamespaceURI(jns);

		if (xns == null)
		{
			qname = new QName(name);
		}
		else
		{
			qname = new QName(xns, local);
		}

		return qname;
	}

	public String createAttributeKey(final String p, final String ns, final String local)
	{

		final StringBuilder builder = new StringBuilder();
		if (!this.supressAtAttributes)
		{
			builder.append(attributeKey);
		}
		final String jns = getJSONNamespace(p, ns);
		// String jns = getPrefix(ns);
		if (jns != null && jns.length() != 0)
		{
			builder.append(jns).append(getNamespaceSeparator());
		}
		return builder.append(local).toString();
	}

	private String getJSONNamespace(final String providedPrefix, final String ns)
	{

		if (ns == null || ns.length() == 0 || ignoreNamespaces)
		{
			return "";
		}

		String jns = (String) xnsToJns.get(ns);
		if (jns == null && providedPrefix != null && providedPrefix.length() > 0)
		{
			jns = providedPrefix;
		}
		if (jns == null)
		{
			throw new IllegalStateException("Invalid JSON namespace: " + ns);
		}
		return jns;
	}

	public String createKey(final String p, final String ns, final String local)
	{

		final StringBuilder builder = new StringBuilder();
		final String jns = getJSONNamespace(p, ns);
		// String jns = getPrefix(ns);
		if (jns != null && jns.length() != 0)
		{
			builder.append(jns).append(getNamespaceSeparator());
		}
		return builder.append(local).toString();
	}

	public boolean isElement(final String p, final String ns, final String local)
	{

		if (attributesAsElements == null)
		{
			return false;
		}

		for (final Object name : attributesAsElements)
		{
			final QName q = (QName) name;

			if (q.getNamespaceURI().equals(ns) && q.getLocalPart().equals(local))
			{
				return true;
			}
		}
		return false;
	}

	public Object convertToJSONPrimitive(final String text)
	{

		return typeConverter.convertToJSONPrimitive(text);
	}

	public Set<?> getPrimitiveArrayKeys()
	{
		return primitiveArrayKeys;
	}

	public boolean isDropRootElement()
	{
		return dropRootElement;
	}

	public List<?> getIgnoredElements()
	{
		return ignoredElements;
	}

	public boolean isWriteNullAsString()
	{
		return writeNullAsString;
	}

	public boolean isReadNullAsString()
	{
		return readNullAsString;
	}

	public boolean isIgnoreEmptyArrayValues()
	{
		return ignoreEmptyArrayValues;
	}

	public boolean isEscapeForwardSlashAlways()
	{
		return escapeForwardSlashAlways;
	}

	public void setEscapeForwardSlashAlways(final boolean escapeForwardSlash)
	{
		this.escapeForwardSlashAlways = escapeForwardSlash;
	}

	public String getNamespaceSeparator()
	{
		return jsonNamespaceSeparator == null ? DOT_NAMESPACE_SEP : jsonNamespaceSeparator;
	}

	private static class NullStringConverter implements TypeConverter
	{
		private static final String NULL_STRING = "null";
		private final TypeConverter converter;

		public NullStringConverter(final TypeConverter converter)
		{
			this.converter = converter;
		}

		@Override
		public Object convertToJSONPrimitive(final String text)
		{
			if (NULL_STRING.equals(text))
			{
				return null;
			}
			return converter.convertToJSONPrimitive(text);
		}

	}
}
