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

import java.security.Principal;

import javax.annotation.Resource;
import javax.jws.WebService;
import javax.xml.ws.WebServiceContext;

import org.apache.cxf.feature.Features;
import org.example.contract.doubleit.DoubleItPortType;
import org.junit.Assert;

@WebService(targetNamespace = "http://www.example.org/contract/DoubleIt", 
            serviceName = "DoubleItService", 
            endpointInterface = "org.example.contract.doubleit.DoubleItPortType")
@Features(features = "org.apache.cxf.feature.LoggingFeature")              
public class DoubleItPortTypeImpl implements DoubleItPortType {
    
    @Resource
    WebServiceContext wsContext;

    public int doubleIt(int numberToDouble) {
        Principal pr = wsContext.getUserPrincipal();
        
        Assert.assertNotNull("Principal must not be null", pr);
        Assert.assertNotNull("Principal.getName() must not return null", pr.getName());
        
        return numberToDouble * 2;
    }
    
}
