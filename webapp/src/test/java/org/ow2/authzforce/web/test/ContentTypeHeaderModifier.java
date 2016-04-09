/**
 * Copyright (C) 2012-2016 Thales Services SAS.
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
package org.ow2.authzforce.web.test;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import javax.ws.rs.core.MediaType;

import org.apache.cxf.interceptor.AbstractOutDatabindingInterceptor;
import org.apache.cxf.interceptor.Fault;
import org.apache.cxf.message.Message;
import org.apache.cxf.phase.Phase;

public class ContentTypeHeaderModifier extends AbstractOutDatabindingInterceptor
{
	public ContentTypeHeaderModifier() {
        super(Phase.PRE_LOGICAL);
    }
    
    @Override
	public void handleMessage(Message outMessage) throws Fault {
    	Map<String, List<String>> headers = (Map<String, List<String>>)outMessage.get(Message.PROTOCOL_HEADERS);
		Map<String, List<String>> modifiedHeaders;
		if (headers == null) {
			modifiedHeaders = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
			outMessage.put(Message.PROTOCOL_HEADERS, modifiedHeaders);
        } else {
        	modifiedHeaders = headers;
        }
		modifiedHeaders.put(Message.CONTENT_TYPE, Collections.singletonList(MediaType.APPLICATION_XML));
		modifiedHeaders.put(Message.ACCEPT_CONTENT_TYPE, Collections.singletonList(MediaType.APPLICATION_XML));
    }
}
