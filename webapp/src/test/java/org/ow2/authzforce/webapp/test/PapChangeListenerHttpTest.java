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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with AuthzForce CE. If not, see <http://www.gnu.org/licenses/>.
 */
package org.ow2.authzforce.webapp.test;

import java.util.List;
import java.util.Collections;
import java.util.concurrent.CopyOnWriteArrayList;
import jakarta.ws.rs.ClientErrorException;
import org.ow2.authzforce.rest.api.jaxrs.DomainResource;
import org.ow2.authzforce.rest.api.xmlns.DomainProperties;
import org.ow2.authzforce.rest.api.xmlns.PdpPropertiesUpdate;
import org.ow2.authzforce.rest.service.PapChangeEvent;
import org.ow2.authzforce.rest.service.PapChangeListener;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.AfterClass;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

/** Real Tomcat/JNDI/CXF/flat-file integration with an enabled listener. */
public class PapChangeListenerHttpTest extends RestServiceTest
{
    public static final class Listener implements PapChangeListener
    {
        static final List<PapChangeEvent> EVENTS = new CopyOnWriteArrayList<>();
        static volatile boolean fail;
        @Override public void papChanged(PapChangeEvent event)
        {
            EVENTS.add(event);
            if (fail) throw new IllegalStateException("Simulated extension failure");
        }
    }

    @Override protected String getPapChangeListenerClassName() { return Listener.class.getName(); }

    @BeforeClass
    public void start() throws Exception
    {
        Listener.EVENTS.clear();
        Listener.fail = false;
        startServerAndInitCLient("", ClientType.XML, "", false, 0, false);
    }

    @AfterClass(alwaysRun = true)
    public void stop() throws Exception
    {
        Listener.fail = false;
        shutdownServer();
    }

    @Test
    public void notifiesSuccessfulHttpMutationsAndPreservesResponses()
    {
        String id = domainsAPIProxyClient.addDomain(new DomainProperties("Listener test", null)).getHref();
        DomainResource domain = domainsAPIProxyClient.getDomainResource(id);
        var pap = domain.getPapResource();
        var policies = pap.getPoliciesResource();
        var policy = createDumbXacmlPolicySet("listener-policy", "1.0");
        policies.addPolicy(policy);
        int beforeConflict = Listener.EVENTS.size();
        expectThrows(ClientErrorException.class, () -> policies.addPolicy(policy));
        assertEquals(Listener.EVENTS.size(), beforeConflict);
        policies.getPolicyResource("listener-policy").getPolicyVersionResource("1.0").deletePolicyVersion();
        policies.addPolicy(createDumbXacmlPolicySet("listener-policy", "2.0"));
        policies.getPolicyResource("listener-policy").deletePolicy();

        // Reapply the existing provider configuration and root selection.
        var providers = pap.getAttributeProvidersResource();
        providers.updateAttributeProviderList(providers.getAttributeProviderList());
        var pdp = pap.getPdpPropertiesResource();
        var current = pdp.getOtherPdpProperties();
        pdp.updateOtherPdpProperties(new PdpPropertiesUpdate(Collections.emptyList(), current.getRootPolicyRefExpression()));
        Listener.fail = true;
        domain.deleteDomain();
        Listener.fail = false;

        assertEquals(Listener.EVENTS.stream().map(PapChangeEvent::operation).toList(), List.of(
                PapChangeEvent.Operation.POLICY_VERSION_ADDED,
                PapChangeEvent.Operation.POLICY_VERSION_DELETED,
                PapChangeEvent.Operation.POLICY_VERSION_ADDED,
                PapChangeEvent.Operation.POLICY_DELETED,
                PapChangeEvent.Operation.ATTRIBUTE_PROVIDERS_CHANGED,
                PapChangeEvent.Operation.PDP_CONFIGURATION_CHANGED,
                PapChangeEvent.Operation.DOMAIN_DELETED));
        assertTrue(Listener.EVENTS.stream().allMatch(event -> id.equals(event.domainId())));
    }
}

