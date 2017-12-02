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
package org.ow2.authzforce.webapp.test.pep.cxf;

import java.io.IOException;

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.core.Response;

import org.apache.cxf.configuration.security.AuthorizationPolicy;
import org.apache.cxf.jaxrs.utils.ExceptionUtils;
import org.apache.cxf.jaxrs.utils.JAXRSUtils;
import org.apache.cxf.message.Message;
import org.apache.cxf.rt.security.saml.interceptor.WSS4JBasicAuthValidator;

/**
 * Extends the WSS4J validator as a JAX-RS request filter
 */
public class WSS4JBasicAuthFilter extends WSS4JBasicAuthValidator implements ContainerRequestFilter {

    public void filter(ContainerRequestContext requestContext) throws IOException {
        Message message = JAXRSUtils.getCurrentMessage();
        AuthorizationPolicy policy = message.get(AuthorizationPolicy.class);
        
        if (policy == null || policy.getUserName() == null || policy.getPassword() == null) {
            requestContext.abortWith(
                Response.status(401).header("WWW-Authenticate", "Basic realm=\"IdP\"").build());
            return;
        }

        try {
            super.validate(message);
        } catch (Exception ex) {
            throw ExceptionUtils.toInternalServerErrorException(ex, null);
        }
    }

}
