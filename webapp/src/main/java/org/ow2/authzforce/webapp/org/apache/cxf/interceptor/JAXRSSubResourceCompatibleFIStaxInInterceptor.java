/**
 * Copyright (C) 2012-2017 Thales Services SAS.
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
package org.ow2.authzforce.webapp.org.apache.cxf.interceptor;

import java.io.InputStream;

import javax.xml.stream.XMLStreamReader;

import org.apache.cxf.endpoint.Endpoint;
import org.apache.cxf.interceptor.FIStaxInInterceptor;
import org.apache.cxf.interceptor.FIStaxOutInterceptor;
import org.apache.cxf.interceptor.StaxInEndingInterceptor;
import org.apache.cxf.jaxrs.provider.JAXBElementProvider;
import org.apache.cxf.message.Message;
import org.apache.cxf.phase.Phase;

import com.sun.xml.fastinfoset.stax.StAXDocumentParser;

/**
 * This modifies {@link FIStaxInInterceptor} to apply the {@link StaxInEndingInterceptor} to phase post-invoke instead of pre-invoke, in order to allow {@link JAXBElementProvider}
 * {@code doUnmarshal(...)} method, when called on a JAX-RS subresource operation invocation, to get the {@link StAXDocumentParser} created here. Indeed, the default {@link StaxInEndingInterceptor}
 * used by {@link FIStaxInInterceptor} applies to pre-invoke phase, and therefore removes the FastInfoset {@link StAXDocumentParser} before any operation on a JAX-RS subresource is called and actually
 * needs/uses it to unmarshal the XML inputstream in argument, resulting in error.
 * 
 * This class also makes some optimizations compared to {@link FIStaxInInterceptor}.
 * 
 * FIXME: report the issue to CXF team to get it fixed and remove this workaround
 *
 */
public final class JAXRSSubResourceCompatibleFIStaxInInterceptor extends FIStaxInInterceptor
{
	// private final static Logger LOGGER = LoggerFactory.getLogger(JAXRSSubResourceCompatibleFIStaxInInterceptor.class);
	private static final StaxInEndingInterceptor ENDING_INTERCEPTOR = new StaxInEndingInterceptor(Phase.POST_INVOKE);

	private static XMLStreamReader getParser(InputStream in)
	{
		final StAXDocumentParser parser = new StAXDocumentParser(in);
		parser.setStringInterning(true);
		parser.setForceStreamClose(true);
		parser.setInputStream(in);
		return parser;
	}

	/**
	 * Default constructor. Enables FastInfoset support.
	 */
	public JAXRSSubResourceCompatibleFIStaxInInterceptor()
	{
		super(Phase.POST_STREAM);
	}

	@Override
	public void handleMessage(Message message)
	{
		if (message.getContent(XMLStreamReader.class) != null || !isHttpVerbSupported(message))
		{
			return;
		}

		final String inContentType = (String) message.get(Message.CONTENT_TYPE);
		if (inContentType != null && inContentType.indexOf("fastinfoset") != -1 && message.getContent(InputStream.class) != null && message.getContent(XMLStreamReader.class) == null)
		{
			message.setContent(XMLStreamReader.class, getParser(message.getContent(InputStream.class)));
			// add the StaxInEndingInterceptor which will close the reader
			message.getInterceptorChain().add(ENDING_INTERCEPTOR);

			final String newContentType = inContentType.replace("application/fastinfoset", "text/xml");
			message.put(Message.CONTENT_TYPE, newContentType);

			message.getExchange().put(FIStaxOutInterceptor.FI_ENABLED, Boolean.TRUE);
			if (isRequestor(message))
			{
				// record the fact that is worked so future requests will
				// automatically be FI enabled
				final Endpoint ep = message.getExchange().getEndpoint();
				ep.put(FIStaxOutInterceptor.FI_ENABLED, Boolean.TRUE);
			}
		}
	}
}
