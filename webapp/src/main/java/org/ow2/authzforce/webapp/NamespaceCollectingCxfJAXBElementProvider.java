/*
 * Copyright (C) 2012-2022 THALES.
 *
 * This file is part of AuthzForce CE.
 *
 * AuthzForce CE is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * AuthzForce CE is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with AuthzForce CE.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.ow2.authzforce.webapp;

import com.google.common.collect.ImmutableMap;
import org.apache.cxf.jaxrs.ext.MessageContext;
import org.apache.cxf.jaxrs.provider.JAXBElementProvider;
import org.apache.cxf.jaxrs.utils.ExceptionUtils;
import org.apache.cxf.staxutils.StaxUtils;
import org.ow2.authzforce.pap.dao.flatfile.XmlnsAppendingDelegatingXMLStreamWriter;
import org.ow2.authzforce.rest.service.jaxrs.PolicyVersionResourceImpl;

import javax.ws.rs.core.MediaType;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.stream.StreamFilter;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.lang.annotation.Annotation;
import java.util.HashMap;
import java.util.Map;

/**
 * {@link org.apache.cxf.jaxrs.provider.JAXBElementProvider} that collects all namespace declarations (prefix-URI mappings) on parsing input XML in order to pass it Saxon XPath evaluator when XPath is enabled and XPath expressions are used in XACML
 */
public final class NamespaceCollectingCxfJAXBElementProvider<T> extends JAXBElementProvider<T>
{
    private static final class XmlnsStreamFilter implements StreamFilter
    {

        private final Map<String, String> xmlnsPrefixToUriMap = new HashMap<>();

        @Override
        public boolean accept(XMLStreamReader reader)
        {
            if (reader.isStartElement())
            {
                for (int i = 0; i < reader.getNamespaceCount(); i++)
                {
                    xmlnsPrefixToUriMap.put(reader.getNamespacePrefix(i), reader.getNamespaceURI(i));
                }
            }

            return true;
        }
    }

    @Override
    protected Object unmarshalFromReader(Unmarshaller unmarshaller, XMLStreamReader reader, Annotation[] anns, MediaType mt) throws JAXBException
    {
        final XmlnsStreamFilter xmlStreamFilter = new XmlnsStreamFilter();
        XMLStreamReader filteringReader = null;
        try
        {
            filteringReader = StaxUtils.createFilteredReader(reader, xmlStreamFilter);
            final Object result = unmarshaller.unmarshal(filteringReader);
            final MessageContext msgCtx = getContext();
            msgCtx.put(PolicyVersionResourceImpl.XML_NS_CONTEXTS_CXF_MESSAGE_CONTEXT_PROPERTY_NAME, xmlStreamFilter.xmlnsPrefixToUriMap);
            return result;
        } finally {
            try
            {
                StaxUtils.close(filteringReader);
            } catch (XMLStreamException e)
            {
                LOG.severe("Error closing input XMLStreamReader" + e.getMessage());
            }
        }
    }

    @Override
    protected Object unmarshalFromInputStream(Unmarshaller unmarshaller, InputStream is, Annotation[] anns, MediaType mt) throws JAXBException
    {
        // Try to create the read before unmarshalling the stream
        XMLStreamReader xmlReader = null;
        final XmlnsStreamFilter xmlStreamFilter;
        try
        {
            if (is == null)
            {
                Reader reader = getStreamHandlerFromCurrentMessage(Reader.class);
                if (reader == null)
                {
                    LOG.severe("No InputStream, Reader, or XMLStreamReader is available");
                    throw ExceptionUtils.toInternalServerErrorException(null, null);
                }
                xmlReader = StaxUtils.createXMLStreamReader(reader);
                xmlStreamFilter = null;
            } else
            {
                final XMLStreamReader filteredXmlReader = StaxUtils.createXMLStreamReader(is);
                xmlStreamFilter = new XmlnsStreamFilter();
                xmlReader = StaxUtils.createFilteredReader(filteredXmlReader, xmlStreamFilter);
            }
            configureReaderRestrictions(xmlReader);
            final Object result = unmarshaller.unmarshal(xmlReader);
            if (xmlStreamFilter != null)
            {
                final MessageContext msgCtx = getContext();
                msgCtx.put(PolicyVersionResourceImpl.XML_NS_CONTEXTS_CXF_MESSAGE_CONTEXT_PROPERTY_NAME, xmlStreamFilter.xmlnsPrefixToUriMap);
            }

            return result;
        } finally
        {
            try
            {
                StaxUtils.close(xmlReader);
            } catch (XMLStreamException e)
            {
                // Ignore
                LOG.severe("Error closing input XMLStreamReader" + e.getMessage());
            }
        }
    }

    @Override
    protected void marshalToWriter(Marshaller ms, Object obj, XMLStreamWriter writer, Annotation[] anns, MediaType mt) throws Exception
    {
        org.apache.cxf.common.jaxb.JAXBUtils.setNoEscapeHandler(ms);
        /*
				Add back the XPath namespace contexts if any as namespace declaration (xmlns:prefix="uri")
			*/
        final Map<String, String> xpathNamespaceContexts = PolicyVersionResourceImpl.OUTPUT_POLICY_XPATH_NAMESPACE_CONTEXT.get();
        final XMLStreamWriter finalWriter = xpathNamespaceContexts == null || xpathNamespaceContexts.isEmpty()? writer: new XmlnsAppendingDelegatingXMLStreamWriter(writer, ImmutableMap.copyOf(xpathNamespaceContexts));
        PolicyVersionResourceImpl.OUTPUT_POLICY_XPATH_NAMESPACE_CONTEXT.remove();
        ms.marshal(obj, finalWriter);
    }

    @Override
    protected void marshalToOutputStream(Marshaller ms, Object obj, OutputStream os, Annotation[] anns, MediaType mt) throws Exception
    {
        org.apache.cxf.common.jaxb.JAXBUtils.setMinimumEscapeHandler(ms);
        if (os == null)
        {
            Writer writer = getStreamHandlerFromCurrentMessage(Writer.class);
            if (writer == null)
            {
                LOG.severe("No OutputStream, Writer, or XMLStreamWriter is available");
                throw ExceptionUtils.toInternalServerErrorException(null, null);
            }
            ms.marshal(obj, writer);
            writer.flush();
        } else
        {
            /*
				Add back the XPath namespace contexts if any as namespace declaration (xmlns:prefix="uri")
			*/
            final XMLStreamWriter xmlStreamWriter = StaxUtils.createXMLStreamWriter(os);
            final Map<String, String> xpathNamespaceContexts = PolicyVersionResourceImpl.OUTPUT_POLICY_XPATH_NAMESPACE_CONTEXT.get();
            final XMLStreamWriter finalWriter = xpathNamespaceContexts == null || xpathNamespaceContexts.isEmpty()? xmlStreamWriter: new XmlnsAppendingDelegatingXMLStreamWriter(xmlStreamWriter, ImmutableMap.copyOf(xpathNamespaceContexts));
            PolicyVersionResourceImpl.OUTPUT_POLICY_XPATH_NAMESPACE_CONTEXT.remove();
            try
            {
                ms.marshal(obj, finalWriter);
            } finally
            {
                StaxUtils.close(xmlStreamWriter);
            }
        }
    }
}
