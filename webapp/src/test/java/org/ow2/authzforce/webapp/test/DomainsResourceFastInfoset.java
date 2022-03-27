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
package org.ow2.authzforce.webapp.test;

import org.apache.cxf.annotations.FastInfoset;
import org.ow2.authzforce.rest.api.jaxrs.DomainsResource;

/**
 * FastInfoset-aware API client
 *
 */
@FastInfoset(force = true)
public interface DomainsResourceFastInfoset extends DomainsResource
{
	// just to add FastInfoset annotation to enable FastInfoset on the JAXRS client
}
