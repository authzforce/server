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
package org.ow2.authzforce.webapp.test;

import java.io.File;
import java.io.IOException;
import java.util.Collections;

import javax.xml.stream.FactoryConfigurationError;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLEventWriter;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.transform.stax.StAXSource;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Validator;

import org.codehaus.jettison.mapped.MappedXMLOutputFactory;
import org.ow2.authzforce.xacml.Xacml3JaxbHelper;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

public class XacmlToJsonConversion
{

	public static void main(final String[] args) throws XMLStreamException, FactoryConfigurationError, SAXException, IOException
	{
		final String xmlDocFilepath = "src/test/resources/xacml.samples/policy.xml";

		/*
		 * replace new StreamSource(new File(xmlDocFilepath)) with new StringReader(xml) if input xml is XML string
		 */
		final XMLEventReader reader = XMLInputFactory.newInstance().createXMLEventReader(new StreamSource(new File(xmlDocFilepath)));
		final Validator validator = Xacml3JaxbHelper.XACML_3_0_SCHEMA.newValidator();
		validator.validate(new StAXSource(reader));
		validator.setErrorHandler(new ErrorHandler()
		{

			@Override
			public void warning(final SAXParseException exception) throws SAXException
			{
				System.out.println(exception);
			}

			@Override
			public void fatalError(final SAXParseException exception) throws SAXException
			{
				System.out.println(exception);

			}

			@Override
			public void error(final SAXParseException exception) throws SAXException
			{
				System.out.println(exception);

			}
		});

		final XMLEventWriter writer = new MappedXMLOutputFactory(Collections.emptyMap()).createXMLEventWriter(System.out);
		writer.add(reader);
		writer.close();
		reader.close();

	}
}
