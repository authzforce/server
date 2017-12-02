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

import javax.ws.rs.ClientErrorException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response.Status;

import org.apache.cxf.jaxrs.utils.JAXRSUtils;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONTokener;
import org.ow2.authzforce.webapp.org.codehaus.jettison.mapped.Configuration;

/**
 * Hardened {@link SetPropertyAllowingMappedXMLInputFactory} (controlling max string length in JSON keys and values)
 */
public final class HardenedMappedXMLInputFactory extends SetPropertyAllowingMappedXMLInputFactory
{
	private static final class HardenedJSONTokener extends JSONTokener
	{

		private static final ClientErrorException JSON_STRING_TOO_BIG_EXCEPTION = new ClientErrorException(
				"Maximum string length has been exceeded by one of the names/values in the input JSON payload", JAXRSUtils.toResponseBuilder(Status.REQUEST_ENTITY_TOO_LARGE)
						.type(MediaType.APPLICATION_JSON_TYPE).build());

		private final int maxValueStringLength;

		private transient boolean nextStringInProgress = false;
		private transient int currentStringIndex = 0;

		public HardenedJSONTokener(final String doc, final int maxValueStringLength)
		{
			super(doc);
			assert maxValueStringLength > 0;
			this.maxValueStringLength = maxValueStringLength;
		}

		@Override
		public char next()
		{
			final char next = super.next();
			if (nextStringInProgress && next != 0)
			{
				currentStringIndex += 1;
				if (currentStringIndex >= maxValueStringLength)
				{
					throw JSON_STRING_TOO_BIG_EXCEPTION;
				}
			}

			return next;
		}

		@Override
		public String nextString(final char quote) throws JSONException
		{
			nextStringInProgress = true;
			final String nextString = super.nextString(quote);
			nextStringInProgress = false;
			// reset for next call
			currentStringIndex = 0;
			return nextString;
		}

	}

	private final int maxValueStringLength;

	/**
	 * Creates instance from given configuration and enforcing a maximum JSON string length
	 * 
	 * @param config
	 *            configuration
	 * @param maxStringLength
	 *            maximum string length allowed for JSON keys and string values
	 */
	public HardenedMappedXMLInputFactory(final Configuration config, final int maxStringLength)
	{
		super(config);
		assert maxStringLength > 0;
		this.maxValueStringLength = maxStringLength;
	}

	@Override
	protected JSONTokener createNewJSONTokener(final String doc)
	{
		return new HardenedJSONTokener(doc, maxValueStringLength);
	}
}
