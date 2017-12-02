/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.ow2.authzforce.webapp.org.apache.cxf.jaxrs.provider.json.utils;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.xml.namespace.NamespaceContext;
import javax.xml.namespace.QName;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;

import org.apache.cxf.common.util.StringUtils;
import org.apache.cxf.staxutils.DelegatingXMLStreamWriter;
import org.apache.cxf.staxutils.DepthXMLStreamReader;
import org.apache.cxf.staxutils.DocumentDepthProperties;
import org.apache.cxf.staxutils.transform.IgnoreNamespacesWriter;
import org.codehaus.jettison.AbstractXMLStreamWriter;
import org.codehaus.jettison.badgerfish.BadgerFishXMLInputFactory;
import org.codehaus.jettison.badgerfish.BadgerFishXMLOutputFactory;
import org.codehaus.jettison.json.JSONTokener;
import org.codehaus.jettison.mapped.TypeConverter;
import org.ow2.authzforce.webapp.org.codehaus.jettison.mapped.Configuration;
import org.ow2.authzforce.webapp.org.codehaus.jettison.mapped.MappedNamespaceConvention;
import org.ow2.authzforce.webapp.org.codehaus.jettison.mapped.MappedXMLInputFactory;
import org.ow2.authzforce.webapp.org.codehaus.jettison.mapped.MappedXMLStreamWriter;

public final class JSONUtils
{
	public static final String XSI_PREFIX = "xsi";
	public static final String XSI_URI = "http://www.w3.org/2001/XMLSchema-instance";

	/*
	 * BEGIN CHANGE to FIX ISSUE "Undeclared prefix 'tns'", when validating XML schema on @xsi:type="tns:MyType". Constant used in fix of MappedNamespaceConvention
	 */
	public static final QName XSI_TYPE_QNAME = new QName(XSI_URI, "type");

	/*
	 * END CHANGE
	 */

	private JSONUtils()
	{
	}

	public static XMLStreamWriter createBadgerFishWriter(final OutputStream os, final String enc) throws XMLStreamException
	{
		final XMLOutputFactory factory = new BadgerFishXMLOutputFactory();
		return factory.createXMLStreamWriter(os, enc);
	}

	public static XMLStreamReader createBadgerFishReader(final InputStream is, final String enc) throws XMLStreamException
	{
		final XMLInputFactory factory = new BadgerFishXMLInputFactory();
		return factory.createXMLStreamReader(is, enc);
	}

	// CHECKSTYLE:OFF
	public static XMLStreamWriter createStreamWriter(final OutputStream os, final QName qname, final boolean writeXsiType, final Configuration config, final boolean serializeAsArray,
			final List<String> arrayKeys, final boolean dropRootElement, final String enc) throws Exception
	{
		// CHECKSTYLE:ON
		final MappedNamespaceConvention convention = new MappedNamespaceConvention(config);
		final AbstractXMLStreamWriter xsw = new MappedXMLStreamWriter(convention, new OutputStreamWriter(os, enc));
		if (serializeAsArray)
		{
			if (arrayKeys != null)
			{
				for (final String key : arrayKeys)
				{
					xsw.serializeAsArray(key);
				}
			}
			else if (qname != null)
			{
				final String key = getKey(convention, qname);
				xsw.serializeAsArray(key);
			}
		}
		final XMLStreamWriter writer = !writeXsiType || dropRootElement ? new IgnoreContentJettisonWriter(xsw, writeXsiType, dropRootElement) : xsw;

		return writer;
	}

	public static Configuration createConfiguration(final ConcurrentHashMap<String, String> namespaceMap, final boolean writeXsiType, final boolean attributesAsElements, final TypeConverter converter)
	{
		if (writeXsiType)
		{
			namespaceMap.putIfAbsent(XSI_URI, XSI_PREFIX);
		}
		final Configuration c = new Configuration(namespaceMap);
		c.setSupressAtAttributes(attributesAsElements);
		if (converter != null)
		{
			c.setTypeConverter(converter);
		}
		return c;
	}

	public static XMLStreamWriter createIgnoreMixedContentWriterIfNeeded(final XMLStreamWriter writer, final boolean ignoreMixedContent)
	{
		return ignoreMixedContent ? new IgnoreMixedContentWriter(writer) : writer;
	}

	public static XMLStreamWriter createIgnoreNsWriterIfNeeded(final XMLStreamWriter writer, final boolean ignoreNamespaces, final boolean ignoreXsiAttributes)
	{
		return ignoreNamespaces ? new IgnoreNamespacesWriter(writer, ignoreXsiAttributes) : writer;
	}

	private static String getKey(final MappedNamespaceConvention convention, final QName qname) throws Exception
	{
		return convention.createKey(qname.getPrefix(), qname.getNamespaceURI(), qname.getLocalPart());

	}

	public static XMLStreamReader createStreamReader(final InputStream is, final boolean readXsiType, final ConcurrentHashMap<String, String> namespaceMap) throws Exception
	{
		return createStreamReader(is, readXsiType, namespaceMap, null, null, null, StandardCharsets.UTF_8.name());
	}

	public static XMLStreamReader createStreamReader(final InputStream is, final boolean readXsiType, final ConcurrentHashMap<String, String> namespaceMap, final String namespaceSeparator,
			final List<String> primitiveArrayKeys, final DocumentDepthProperties depthProps, final String enc) throws Exception
	{
		if (readXsiType)
		{
			namespaceMap.putIfAbsent(XSI_URI, XSI_PREFIX);
		}
		final Configuration conf = new Configuration(namespaceMap);
		if (namespaceSeparator != null)
		{
			conf.setJsonNamespaceSeparator(namespaceSeparator);
		}
		if (primitiveArrayKeys != null)
		{
			conf.setPrimitiveArrayKeys(new HashSet<String>(primitiveArrayKeys));
		}

		final XMLInputFactory factory = depthProps != null ? new JettisonMappedReaderFactory(conf, depthProps) : new MappedXMLInputFactory(conf);
		return new JettisonReader(namespaceMap, factory.createXMLStreamReader(is, enc));
	}

	private static class JettisonMappedReaderFactory extends MappedXMLInputFactory
	{
		private final DocumentDepthProperties depthProps;

		JettisonMappedReaderFactory(final Configuration conf, final DocumentDepthProperties depthProps)
		{
			super(conf);
			this.depthProps = depthProps;
		}

		@Override
		protected JSONTokener createNewJSONTokener(final String doc)
		{
			return new JSONTokener(doc, depthProps.getInnerElementCountThreshold());
		}
	}

	/*
	 * BEGIN CHANGE to CXF 3.1.8: changed the visibility of this class and constructor to allow usage from org.ow2.authzforce.webapp.org.apache.cxf.jaxrs.provider.json.JSONProvider
	 */
	public static class JettisonReader extends DepthXMLStreamReader
	{
		private final Map<String, String> namespaceMap;

		public JettisonReader(final Map<String, String> nsMap, final XMLStreamReader reader)
		{
			/*
			 * END CHANGE
			 */
			super(reader);
			this.namespaceMap = nsMap;
		}

		@Override
		public String getNamespaceURI(final String arg0)
		{
			String uri = super.getNamespaceURI(arg0);
			if (uri == null)
			{
				uri = getNamespaceContext().getNamespaceURI(arg0);
			}
			return uri;
		}

		@Override
		public String getAttributePrefix(final int n)
		{
			final QName name = getAttributeName(n);
			if (name != null && XSI_URI.equals(name.getNamespaceURI()))
			{
				return XSI_PREFIX;
			}

			return super.getAttributePrefix(n);
		}

		@Override
		public NamespaceContext getNamespaceContext()
		{
			return new NamespaceContext()
			{

				@Override
				public String getNamespaceURI(final String prefix)
				{
					for (final Map.Entry<String, String> entry : namespaceMap.entrySet())
					{
						if (entry.getValue().equals(prefix))
						{
							return entry.getKey();
						}
					}
					return null;
				}

				@Override
				public String getPrefix(final String ns)
				{
					return namespaceMap.get(ns);
				}

				@Override
				public Iterator<?> getPrefixes(final String ns)
				{
					final String prefix = getPrefix(ns);
					return prefix == null ? null : Collections.singletonList(prefix).iterator();
				}

			};
		}

		/*
		 * Change to CXF 3.1.8 - JettisonReader to fix issue reported by PMD: Rule:OverrideBothEqualsAndHashcode
		 */
		@Override
		public boolean equals(final Object arg0)
		{
			return reader.equals(arg0);
		}

		/*
		 * Change to CXF 3.1.8 - JettisonReader to fix issue reported by PMD: Rule:OverrideBothEqualsAndHashcode
		 */
		@Override
		public int hashCode()
		{
			return reader.hashCode();
		}
	}

	private static class IgnoreContentJettisonWriter extends DelegatingXMLStreamWriter
	{

		private final boolean writeXsiType;
		private final boolean dropRootElement;
		private boolean rootDropped;
		private int index;

		IgnoreContentJettisonWriter(final XMLStreamWriter writer, final boolean writeXsiType, final boolean dropRootElement)
		{
			super(writer);
			this.writeXsiType = writeXsiType;
			this.dropRootElement = dropRootElement;
		}

		@Override
		public void writeAttribute(final String prefix, final String uri, final String local, final String value) throws XMLStreamException
		{
			if (!writeXsiType && XSI_PREFIX.equals(prefix) && ("type".equals(local) || "nil".equals(local)))
			{
				return;
			}
			super.writeAttribute(prefix, uri, local, value);

		}

		@Override
		public void writeStartElement(final String prefix, final String local, final String uri) throws XMLStreamException
		{
			index++;
			if (dropRootElement && index - 1 == 0)
			{
				rootDropped = true;
				return;
			}
			super.writeStartElement(prefix, local, uri);
		}

		@Override
		public void writeStartElement(final String local) throws XMLStreamException
		{
			this.writeStartElement("", local, "");
		}

		@Override
		public void writeEndElement() throws XMLStreamException
		{
			index--;
			if (rootDropped && index == 0)
			{
				return;
			}
			super.writeEndElement();
		}
	}

	private static class IgnoreMixedContentWriter extends DelegatingXMLStreamWriter
	{
		String lastText;
		boolean isMixed;
		List<Boolean> mixed = new LinkedList<Boolean>();

		IgnoreMixedContentWriter(final XMLStreamWriter writer)
		{
			super(writer);
		}

		@Override
		public void writeCharacters(final String text) throws XMLStreamException
		{
			if (StringUtils.isEmpty(text.trim()))
			{
				lastText = text;
			}
			else if (lastText != null)
			{
				lastText += text;
			}
			else if (!isMixed)
			{
				super.writeCharacters(text);
			}
			else
			{
				lastText = text;
			}
		}

		@Override
		public void writeStartElement(final String prefix, final String local, final String uri) throws XMLStreamException
		{
			if (lastText != null)
			{
				isMixed = true;
			}
			mixed.add(0, isMixed);
			lastText = null;
			isMixed = false;
			super.writeStartElement(prefix, local, uri);
		}

		@Override
		public void writeStartElement(final String uri, final String local) throws XMLStreamException
		{
			if (lastText != null)
			{
				isMixed = true;
			}
			mixed.add(0, isMixed);
			lastText = null;
			isMixed = false;
			super.writeStartElement(uri, local);
		}

		@Override
		public void writeStartElement(final String local) throws XMLStreamException
		{
			if (lastText != null)
			{
				isMixed = true;
			}
			mixed.add(0, isMixed);
			lastText = null;
			isMixed = false;
			super.writeStartElement(local);
		}

		@Override
		public void writeEndElement() throws XMLStreamException
		{
			if (lastText != null && (!isMixed || !StringUtils.isEmpty(lastText.trim())))
			{
				super.writeCharacters(lastText.trim());
			}
			super.writeEndElement();
			isMixed = mixed.get(0);
			mixed.remove(0);
		}

	}

}
