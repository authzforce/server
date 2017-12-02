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
package org.ow2.authzforce.webapp;

import org.ow2.authzforce.webapp.org.apache.cxf.jaxrs.provider.json.JSONProvider;
import org.ow2.authzforce.webapp.org.codehaus.jettison.mapped.Configuration;
import org.ow2.authzforce.webapp.org.codehaus.jettison.mapped.MappedXMLInputFactory;

/**
 * {@link MappedXMLInputFactory} allowing setProperty(arg0, arg1) doing nothing, extended to fix setProperty() required by XML External Entity attack mitigation required by Findbugs in
 * createStreamReader(), and required for compatibility with {@link JSONProvider}. {@link MappedXMLInputFactory#setProperty(String, Object)} would return IllegalArgumentException.
 */
public class SetPropertyAllowingMappedXMLInputFactory extends MappedXMLInputFactory
{

	/**
	 * Constructor
	 * 
	 * @param config
	 *            configuration
	 */
	public SetPropertyAllowingMappedXMLInputFactory(final Configuration config)
	{
		super(config);
	}

	@Override
	public void setProperty(final String arg0, final Object arg1)
	{
		/*
		 * Void: do nothing. super.setProperty(arg0, arg1) would return IllegalArgumentException
		 */
		/*
		 * Required by fix to XML External Entity attacks in createStreamReader()
		 */
		// TODO: should gracefully handle standard properties

	}

}
