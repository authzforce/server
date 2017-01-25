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

import java.util.Collections;
import java.util.Iterator;
import java.util.Map;

import javax.xml.namespace.NamespaceContext;
import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamReader;

import org.apache.cxf.jaxrs.provider.json.utils.JSONUtils;
import org.apache.cxf.staxutils.DepthXMLStreamReader;
import org.ow2.authzforce.webapp.JsonJaxrsProvider;

/**
 * From CXF {@link JSONUtils} (not visible but required by
 * {@link JsonJaxrsProvider} therefore copy-pasted here)
 */
public class JettisonReader extends DepthXMLStreamReader {
	private Map<String, String> namespaceMap;

	public JettisonReader(Map<String, String> nsMap, XMLStreamReader reader) {
		super(reader);
		this.namespaceMap = nsMap;
	}

	public String getNamespaceURI(String arg0) {
		String uri = super.getNamespaceURI(arg0);
		if (uri == null) {
			uri = getNamespaceContext().getNamespaceURI(arg0);
		}
		return uri;
	}

	@Override
	public String getAttributePrefix(int n) {
		QName name = getAttributeName(n);
		if (name != null && JSONUtils.XSI_URI.equals(name.getNamespaceURI())) {
			return JSONUtils.XSI_PREFIX;
		} else {
			return super.getAttributePrefix(n);
		}
	}

	@Override
	public NamespaceContext getNamespaceContext() {
		return new NamespaceContext() {

			public String getNamespaceURI(String prefix) {
				for (Map.Entry<String, String> entry : namespaceMap.entrySet()) {
					if (entry.getValue().equals(prefix)) {
						return entry.getKey();
					}
				}
				return null;
			}

			public String getPrefix(String ns) {
				return namespaceMap.get(ns);
			}

			public Iterator<?> getPrefixes(String ns) {
				String prefix = getPrefix(ns);
				return prefix == null ? null : Collections.singletonList(prefix).iterator();
			}

		};
	}

	/*
	 * Change to CXF 3.1.8 - JettisonReader to fix issue reported by PMD: Rule:OverrideBothEqualsAndHashcode
	 */
	@Override
	public boolean equals(Object arg0) {
		return reader.equals(arg0);
	}

	/*
	 * Change to CXF 3.1.8 - JettisonReader to fix issue reported by PMD: Rule:OverrideBothEqualsAndHashcode
	 */
	@Override
	public int hashCode() {
		return reader.hashCode();
	}

}