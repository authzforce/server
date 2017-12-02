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
package org.ow2.authzforce.rest.service.jaxrs;

import java.io.IOException;
import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.util.Properties;

import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;

import org.ow2.authzforce.rest.api.jaxrs.ProductMetadataResource;
import org.ow2.authzforce.rest.api.xmlns.ProductMetadata;

import com.google.common.base.Preconditions;

/**
 * Implementation of {@link ProductMetadataResource}
 *
 */
public final class ProductMetadataResourceImpl implements ProductMetadataResource
{
	private static final DatatypeFactory XML_DATATYPE_FACTORY;
	private static final String PRODUCT_PROPERTIES_CLASSPATH_URL = "/org.ow2.authzforce.server.product.properties";
	private static final String PRODUCT_NAME;
	private static final String PRODUCT_VERSION;
	private static final XMLGregorianCalendar PRODUCT_RELEASE_DATE;
	private static final String PRODUCT_API_DOC_URL;

	static
	{
		try
		{
			XML_DATATYPE_FACTORY = DatatypeFactory.newInstance();
		}
		catch (final DatatypeConfigurationException e)
		{
			throw new RuntimeException(e);
		}

		final Properties prodProps = new Properties();
		try (final InputStream propFileIn = ProductMetadataResourceImpl.class.getResourceAsStream(PRODUCT_PROPERTIES_CLASSPATH_URL))
		{
			if (propFileIn == null)
			{
				throw new RuntimeException("Missing product properties resource on the classpath: " + PRODUCT_PROPERTIES_CLASSPATH_URL);
			}
			prodProps.load(propFileIn);
		}
		catch (final IOException e)
		{
			throw new RuntimeException("Error reading product properties file on the classpath", e);
		}

		PRODUCT_NAME = prodProps.getProperty("name");
		Preconditions.checkNotNull(PRODUCT_NAME, "Property 'name' undefined in file '%s'", PRODUCT_PROPERTIES_CLASSPATH_URL);

		PRODUCT_VERSION = prodProps.getProperty("version");
		Preconditions.checkNotNull(PRODUCT_VERSION, "Property 'version' undefined in file '%s'", PRODUCT_PROPERTIES_CLASSPATH_URL);
		final String buildDate = prodProps.getProperty("build.date");
		Preconditions.checkNotNull(buildDate, "Property 'build.date' undefined in file '%s'", PRODUCT_PROPERTIES_CLASSPATH_URL);

		PRODUCT_RELEASE_DATE = XML_DATATYPE_FACTORY.newXMLGregorianCalendar(buildDate);
		PRODUCT_API_DOC_URL = prodProps.getProperty("api.doc.url");
		Preconditions.checkNotNull(PRODUCT_API_DOC_URL, "Property 'api.doc.ur' undefined in file '%s'", PRODUCT_PROPERTIES_CLASSPATH_URL);
	}

	@Override
	public ProductMetadata getProductMetadata()
	{
		final RuntimeMXBean rb = ManagementFactory.getRuntimeMXBean();
		return new ProductMetadata(PRODUCT_NAME, PRODUCT_VERSION, PRODUCT_RELEASE_DATE, XML_DATATYPE_FACTORY.newDuration(rb.getUptime()), PRODUCT_API_DOC_URL);
	}

}
