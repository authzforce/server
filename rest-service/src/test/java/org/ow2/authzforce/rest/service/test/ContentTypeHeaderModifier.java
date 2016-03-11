package org.ow2.authzforce.rest.service.test;

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
