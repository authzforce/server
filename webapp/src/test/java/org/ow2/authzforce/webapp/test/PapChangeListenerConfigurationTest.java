/*
 * Copyright (C) 2012-2026 THALES.
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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import javax.xml.parsers.DocumentBuilderFactory;
import org.ow2.authzforce.rest.service.PapChangeEvent;
import org.ow2.authzforce.rest.service.PapChangeListener;
import org.ow2.authzforce.rest.service.jaxrs.DomainsResourceImpl;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.beans.factory.xml.XmlBeanDefinitionReader;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.jndi.JndiTemplate;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

/** Exercises the actual listener/JNDI/resource bean declarations from the production XML. */
public class PapChangeListenerConfigurationTest
{
    /** Example separately loadable extension. */
    public static final class RecordingListener implements PapChangeListener, AutoCloseable
    {
        static final AtomicInteger CLOSED = new AtomicInteger();
        @Override public void papChanged(PapChangeEvent event) { }
        @Override public void close() { CLOSED.incrementAndGet(); }
    }

    /** Constructor failure must prevent startup. */
    public static final class BrokenListener implements PapChangeListener
    {
        public BrokenListener() { throw new IllegalStateException("Cannot initialize extension"); }
        @Override public void papChanged(PapChangeEvent event) { }
    }

    private static GenericApplicationContext context(String configuredClass) throws Exception
    {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        Document doc = factory.newDocumentBuilder().parse("src/main/webapp/WEB-INF/beans.xml");
        Element root = doc.getDocumentElement();
        List<Node> remove = new ArrayList<>();
        for (Node node = root.getFirstChild(); node != null; node = node.getNextSibling())
        {
            if (!(node instanceof Element element) ||
                !List.of("jndi_papChangeListenerClass", "papChangeListener", "domainsResourceBean").contains(element.getAttribute("id")))
                remove.add(node);
        }
        remove.forEach(root::removeChild);
        GenericApplicationContext context = new GenericApplicationContext();
        // Keep the real JNDI lookup and Spring wiring, substituting only its external naming service.
        if (configuredClass != null)
        {
            context.getBeanFactory().registerSingleton("testJndiTemplate", new JndiTemplate() {
                @Override public Object lookup(String name) {
                    assertTrue(name.endsWith("org.ow2.authzforce.rest.service.PapChangeListener.class"));
                    return configuredClass;
                }
            });
        }
        // No storage is needed to verify constructor injection. Supply an explicit null bean instead.
        RootBeanDefinition nullDao = new RootBeanDefinition(NullDaoFactory.class);
        context.registerBeanDefinition("domainsDaoBean", nullDao);
        new XmlBeanDefinitionReader(context).registerBeanDefinitions(doc, new org.springframework.core.io.DescriptiveResource("Production PAP listener wiring"));
        if (configuredClass != null)
            context.getBeanDefinition("jndi_papChangeListenerClass").getPropertyValues()
                    .add("jndiTemplate", context.getBeanFactory().getSingleton("testJndiTemplate"));
        return context;
    }

    /** Null DAO stand-in: tests startup wiring without initializing persistent domains. */
    public static final class NullDaoFactory implements org.springframework.beans.factory.FactoryBean<Object>
    {
        @Override public Object getObject() { return null; }
        @Override public Class<?> getObjectType() { return org.ow2.authzforce.core.pap.api.dao.DomainsDao.class; }
        @Override public boolean isSingleton() { return true; }
    }

    @Test
    public void missingAndEmptyJndiConfigurationAllowStartup() throws Exception
    {
        for (String value : new String[]{null, ""})
        {
            try (GenericApplicationContext context = context(value)) {
                context.refresh();
                assertNotNull(context.getBean("domainsResourceBean", DomainsResourceImpl.class));
                assertNull(((org.ow2.authzforce.webapp.PapChangeListenerFactoryBean)context.getBean("&papChangeListener")).getObject());
            }
        }
    }

    @Test
    public void configuredListenerIsLoadedAndClosed() throws Exception
    {
        int before = RecordingListener.CLOSED.get();
        try (GenericApplicationContext context = context(RecordingListener.class.getName())) {
            context.refresh();
            assertTrue(context.getBean("papChangeListener") instanceof RecordingListener);
            assertNotNull(context.getBean("domainsResourceBean", DomainsResourceImpl.class));
            assertSame(context.getBean("papChangeListener"), context.getBean("papChangeListener"));
        }
        assertEquals(RecordingListener.CLOSED.get(), before + 1);
    }

    @Test
    public void invalidListenerConfigurationFailsStartup() throws Exception
    {
        for (String name : List.of("missing.Listener", String.class.getName(), BrokenListener.class.getName())) {
            try (GenericApplicationContext context = context(name)) {
                expectThrows(org.springframework.beans.BeansException.class, context::refresh);
            }
        }
    }
}

