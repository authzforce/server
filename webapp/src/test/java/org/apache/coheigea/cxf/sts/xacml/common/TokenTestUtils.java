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
package org.apache.coheigea.cxf.sts.xacml.common;

import javax.xml.ws.BindingProvider;

import org.apache.cxf.ws.security.SecurityConstants;
import org.apache.cxf.ws.security.trust.STSClient;

public final class TokenTestUtils {
    
    private TokenTestUtils() {
        // complete
    }
    
    public static void updateSTSPort(BindingProvider p, String port) {
        STSClient stsClient = (STSClient)p.getRequestContext().get(SecurityConstants.STS_CLIENT);
        if (stsClient != null) {
            String location = stsClient.getWsdlLocation();
            if (location != null && location.contains("8080")) {
                stsClient.setWsdlLocation(location.replace("8080", port));
            } else if (location != null && location.contains("8443")) {
                stsClient.setWsdlLocation(location.replace("8443", port));
            }
        }
        stsClient = (STSClient)p.getRequestContext().get(SecurityConstants.STS_CLIENT + ".sct");
        if (stsClient != null) {
            String location = stsClient.getWsdlLocation();
            if (location.contains("8080")) {
                stsClient.setWsdlLocation(location.replace("8080", port));
            } else if (location.contains("8443")) {
                stsClient.setWsdlLocation(location.replace("8443", port));
            }
        }
    }

}
