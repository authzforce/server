/**
 * Copyright (C) 2012-2017 Thales Services SAS.
 *
 * This file is part of AuthZForce CE.
 *
 * AuthZForce CE is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * AuthZForce CE is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with AuthZForce CE.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.ow2.authzforce.webapp;

import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.ws.rs.ClientErrorException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response.Status;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamReader;

import org.apache.cxf.jaxrs.provider.json.JSONProvider;
import org.apache.cxf.jaxrs.provider.json.utils.JSONUtils;
import org.apache.cxf.jaxrs.utils.JAXRSUtils;
import org.apache.cxf.staxutils.DepthRestrictingStreamReader;
import org.apache.cxf.staxutils.DocumentDepthProperties;
import org.apache.cxf.staxutils.transform.TransformUtils;
import org.codehaus.jettison.AbstractXMLInputFactory;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONTokener;
import org.codehaus.jettison.mapped.Configuration;
import org.codehaus.jettison.mapped.MappedXMLInputFactory;
import org.ow2.authzforce.webapp.org.apache.cxf.jaxrs.provider.json.utils.JettisonReader;

import net.sf.saxon.lib.StandardURIChecker;
import net.sf.saxon.om.NameChecker;

/**
 * Fix for CXF issue: JSONProvider does not enforce JAX-RS (JSON in this case)
 * depth control properties org.apache.cxf.stax.* (only
 * innerElementCountThreshold is enforced and only affects JSONObject key-value
 * pairs, not JSONArray elements!), as of CXF 3.1.8. This fix is adapted from
 * protected method {@code getStreamReader()} of
 * {@link org.apache.cxf.jaxrs.provider.JAXBElementProvider}
 * <p>
 * FIXME: report this issue
 * <p>
 * This implementation like parent {@link JSONProvider} is not as efficient as
 * it should be, because JSON parsing is done after the whole JSON payload has
 * been parsed into a String by {@link AbstractXMLInputFactory}#readAll()
 * methods
 */
public final class JsonJaxrsProvider<T> extends JSONProvider<T> {

	private static final class HardenedJSONTokener extends JSONTokener {

		private static final ClientErrorException JSON_STRING_TOO_BIG_EXCEPTION = new ClientErrorException(
				"Maximum string length has been exceeded by one of the names/values in the input JSON payload",
				JAXRSUtils.toResponseBuilder(Status.REQUEST_ENTITY_TOO_LARGE).type(MediaType.APPLICATION_JSON_TYPE)
						.build());

		private final int maxValueStringLength;

		private transient boolean nextStringInProgress = false;
		private transient int currentStringIndex = 0;

		public HardenedJSONTokener(String doc, int maxValueStringLength) {
			super(doc);
			assert maxValueStringLength > 0;
			this.maxValueStringLength = maxValueStringLength;
		}

		@Override
		public char next() {
			final char next = super.next();
			if (nextStringInProgress && next != 0) {
				currentStringIndex += 1;
				if (currentStringIndex >= maxValueStringLength) {
					throw JSON_STRING_TOO_BIG_EXCEPTION;
				}
			}

			return next;
		}

		@Override
		public String nextString(char quote) throws JSONException {
			nextStringInProgress = true;
			final String nextString = super.nextString(quote);
			nextStringInProgress = false;
			// reset for next call
			currentStringIndex = 0;
			return nextString;
		}

	}

	/**
	 * MappedXMLInputFactory extended to fix setProperty() required by XML
	 * External Entity attack mitigation required by Findbugs in
	 * createStreamReader()
	 */
	private static class MappedXMLInputFactoryOverride extends MappedXMLInputFactory {

		private MappedXMLInputFactoryOverride(Configuration config) {
			super(config);
		}

		@Override
		public void setProperty(String arg0, Object arg1) throws IllegalArgumentException {
			/*
			 * Required by fix to XML External Entity attacks in
			 * createStreamReader()
			 */
			// TODO: should gracefully handle standard properties
		}

	}

	/**
	 * Adapted from JSONUtils$JettisonMappedReaderFactory
	 */
	private static final class HardenedJettisonMappedReaderFactory extends MappedXMLInputFactoryOverride {

		private final int maxValueStringLength;

		private HardenedJettisonMappedReaderFactory(Configuration config, int maxStringLength) {
			super(config);
			assert maxStringLength > 0;
			this.maxValueStringLength = maxStringLength;
		}

		protected JSONTokener createNewJSONTokener(String doc) {
			return new HardenedJSONTokener(doc, maxValueStringLength);
		}
	}

	private static final UnsupportedOperationException UNSUPPORTED_DEPTH_PROPERTIES_WITH_BADGERFISH_EXCEPTION = new UnsupportedOperationException(
			"'depthProperties' property not supported if convention = 'badgerfish'");

	private int maxStringLength = -1;

	/**
	 * Set maximum string length in JSON payload;
	 * 
	 * @param value
	 *            maximum, ignored iff negative (or zero)
	 */
	public void setMaxStringLength(int value) {
		this.maxStringLength = value;
	}

	/*
	 * Class members already defined in JSONProvider but not visible yet
	 * required by overridden method createReader() NB: this is far from optimal
	 * since it duplicates data. FIXME: get CXF JSONProvider patched directly
	 */
	private static final String MAPPED_CONVENTION = "mapped";
	private static final String BADGER_FISH_CONVENTION = "badgerfish";

	private String convention = MAPPED_CONVENTION;

	private Map<String, String> namespaceMap = null;

	private String namespaceSeparator;

	private List<String> primitiveArrayKeys;

	/*
	 * Method overrides required to access fields (not visible via inheritance
	 * from JSONProvider) used by overridden method createReader(). We also
	 * improve things, e.g. validation of namespaceMap
	 */
	@Override
	public void setConvention(String value) {
		super.setConvention(value);
		this.convention = value;
	}

	@Override
	public void setNamespaceMap(Map<String, String> namespaceMap) {
		super.setNamespaceMap(namespaceMap);

		if (namespaceMap != null) {
			for (final Entry<String, String> nsToPrefix : namespaceMap.entrySet()) {
				final String nsURI = nsToPrefix.getKey();
				if (!StandardURIChecker.getInstance().isValidURI(nsURI)) {
					throw new IllegalArgumentException("Invalid namespace URI in one of namespaceMap keys: " + nsURI);
				}

				final String nsPrefix = nsToPrefix.getValue();
				if (!NameChecker.isValidNCName(nsPrefix)) {
					throw new IllegalArgumentException(
							"Invalid namespace prefix in one of namespaceMap values: " + nsPrefix);
				}
			}

			final Map<String, String> mutableMap = new HashMap<>(namespaceMap);
			mutableMap.putIfAbsent(JSONUtils.XSI_URI, JSONUtils.XSI_PREFIX);
			this.namespaceMap = Collections.unmodifiableMap(mutableMap);
		}
	}

	@Override
	public void setPrimitiveArrayKeys(List<String> primitiveArrayKeys) {
		super.setPrimitiveArrayKeys(primitiveArrayKeys);
		this.primitiveArrayKeys = primitiveArrayKeys;
	}

	@Override
	public void setNamespaceSeparator(String namespaceSeparator) {
		super.setNamespaceSeparator(namespaceSeparator);
		this.namespaceSeparator = namespaceSeparator;
	}

	/**
	 * Reason for override: super.createDepthReaderIfNeeded() fails if
	 * getDepthProperties() != null because the reader (XMLStreamReader) is not
	 * compatible with internal call to StaxUtils.configureReader(reader,
	 * message): it is not expected type org.codehaus.stax2.XMLStreamReader2
	 * (therefore ClassCastException);
	 */
	@Override
	protected XMLStreamReader createDepthReaderIfNeeded(XMLStreamReader reader, InputStream is) {
		DocumentDepthProperties props = getDepthProperties();
		if (props != null && props.isEffective()) {
			reader = TransformUtils.createNewReaderIfNeeded(reader, is);
			reader = new DepthRestrictingStreamReader(reader, props);
		}
		// BEGIN CHANGE to AbstractJAXBProvider (CXF 3.1.8)
		/*
		 * WARNING: configureReaderRestrictions() fails if getDepthProperties()
		 * != null because the reader (XMLStreamReader) is not compatible with
		 * internal StaxUtils.configureReader(reader, message), it is not
		 * expected type org.codehaus.stax2.XMLStreamReader2 (therefore
		 * ClassCastException);
		 */
		// else if (reader != null) {
		// reader = configureReaderRestrictions(reader);
		// }
		// END CHANGE
		return reader;
	}

	/**
	 * Adapted from
	 * {@link JSONUtils#createStreamReader(InputStream, boolean, java.util.concurrent.ConcurrentHashMap, String, List, DocumentDepthProperties, String)}
	 * to support extra property maxStringLength
	 */
	private XMLStreamReader createStreamReader(InputStream is,
			/* DocumentDepthProperties depthProps, */ String enc) throws Exception {
		// BEGIN CHANGE to JSONUtils#createStreamReader() (CXF 3.1.8)
		/*
		 * XSI namespace added to namespaceMap in setNamespaceMap()
		 */
		// if (readXsiType) {
		// namespaceMap.putIfAbsent(XSI_URI, XSI_PREFIX);
		// }
		// END CHANGE
		Configuration conf = new Configuration(namespaceMap);
		if (namespaceSeparator != null) {
			conf.setJsonNamespaceSeparator(namespaceSeparator);
		}
		if (primitiveArrayKeys != null) {
			conf.setPrimitiveArrayKeys(new HashSet<String>(primitiveArrayKeys));
		}

		// BEGIN CHANGE to JSONUtils#createStreamReader() (CXF 3.1.8)
		// XMLInputFactory factory = depthProps != null
		// ? new JettisonMappedReaderFactory(conf, depthProps)
		// : new MappedXMLInputFactory(conf);
		// depthProps already handled by createDepthReaderIfNeeded()
		final XMLInputFactory factory = maxStringLength > 0
				? new HardenedJettisonMappedReaderFactory(conf, /* depthProps, */ maxStringLength)
				: new MappedXMLInputFactoryOverride(conf);
		/*
		 * Mitigation of XML External Entity attacks: (More info:
		 * https://find-sec-bugs.github.io/bugs.htm)
		 */
		factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
		factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
		// END CHANGE
		return new JettisonReader(namespaceMap, factory.createXMLStreamReader(is, enc));
	}

	/**
	 * Reason for override:
	 * <ol>
	 * <li>prevent use of depthProperties with badgerfish convention (not
	 * supported). More info:
	 * http://cxf.547215.n5.nabble.com/No-JSON-depth-control-with-Badgerfish
	 * -td5776211.html</li>
	 * <li>Support innerElementLevelThreshold (only innerElementCount is
	 * supported by CXF JSONProvider as shown by method
	 * JSONUtils$JettisonMappedReaderFactory#createNewJSONTokener() ) and max
	 * string length (similar to
	 * org.apache.cxf.stax.maxAttributeSize/maxTextLength for XML).</li>
	 * </ol>
	 */
	@Override
	protected XMLStreamReader createReader(Class<?> type, InputStream is, String enc) throws Exception {
		XMLStreamReader reader = null;
		// BEGIN CHANGE to JSONProvider (CXF 3.1.8)
		final DocumentDepthProperties depthProps = getDepthProperties();
		// END CHANGE
		if (BADGER_FISH_CONVENTION.equals(convention)) {
			// BEGIN CHANGE to JSONProvider (CXF 3.1.8)
			if (depthProps != null) {
				throw UNSUPPORTED_DEPTH_PROPERTIES_WITH_BADGERFISH_EXCEPTION;
			}
			// END CHANGE
			reader = JSONUtils.createBadgerFishReader(is, enc);
		} else {
			// BEGIN CHANGE to JSONProvider (CXF 3.1.8)
			// reader = JSONUtils.createStreamReader(is, readXsiType,
			// namespaceMap, namespaceSeparator, primitiveArrayKeys,
			// depthProps, enc);
			reader = createStreamReader(is, /* depthProps, */ enc);
			// END CHANGE
		}

		reader = createTransformReaderIfNeeded(reader, is);
		// BEGIN CHANGE to JSONProvider (same as JAXBElementProvider) (CXF
		// 3.1.8)
		/*
		 * This enforces innerElementLevelThreshold and innerElementCount (and
		 * total elementCount but we don't use it). Else only innerElementCount
		 * is supported.
		 */
		reader = createDepthReaderIfNeeded(reader, is);
		// END CHANGE

		return reader;
	}

}
