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
package org.ow2.authzforce.rest.service.jaxrs;

import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.*;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ClientErrorException;
import jakarta.ws.rs.InternalServerErrorException;
import jakarta.ws.rs.NotFoundException;
import oasis.names.tc.xacml._3_0.core.schema.wd_17.PolicySet;
import oasis.names.tc.xacml._3_0.core.schema.wd_17.Target;
import org.apache.cxf.message.MessageImpl;
import org.apache.cxf.phase.PhaseInterceptorChain;
import org.ow2.authzforce.core.pap.api.dao.*;
import org.ow2.authzforce.core.pdp.api.policy.PolicyVersion;
import org.ow2.authzforce.rest.api.jaxrs.DomainResource;
import org.ow2.authzforce.rest.api.xmlns.AttributeProviders;
import org.ow2.authzforce.rest.api.xmlns.PdpPropertiesUpdate;
import org.ow2.authzforce.rest.service.*;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

/** Tests API hooks independently of filesystem storage using the existing DAO contracts. */
public class PapChangeListenerTest
{
    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, InvocationHandler handler)
    {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler);
    }

    private static final class Fixture
    {
        final PolicySet policy = new PolicySet(null, null, null, new Target(null), null, null, null,
                "policy", "1.0", "urn:oasis:names:tc:xacml:3.0:policy-combining-algorithm:deny-unless-permit", null);
        final AuthzPolicy authzPolicy = new JaxbXacmlAuthzPolicy(policy, com.google.common.collect.ImmutableMap.of());
        boolean missing;
        boolean conflict;
        boolean invalid;
        boolean ioFailure;
        final java.util.concurrent.atomic.AtomicInteger completedMutations = new java.util.concurrent.atomic.AtomicInteger();
        final DomainDao<PolicyVersionResourceImpl, PolicyResourceImpl> dao;
        final DomainResourceImpl<?> cached;
        final DomainsDao<DomainResourceImpl<?>> repository;

        @SuppressWarnings("unchecked")
        Fixture() throws IOException
        {
            dao = proxy(DomainDao.class, (instance, method, args) -> {
                String name = method.getName();
                if (name.equals("getPolicyDaoClient"))
                    return PolicyResourceImpl.FACTORY.getInstance((String) args[0], dao());
                if (name.equals("getVersionDaoClient"))
                    return PolicyVersionResourceImpl.FACTORY.getInstance((String) args[0], (PolicyVersion) args[1], dao());
                if (name.equals("getLatestPolicyVersionId")) return new PolicyVersion("1.0");
                if (invalid) throw new IllegalArgumentException("Rejected mutation");
                if (ioFailure) throw new IOException("Storage failure");
                if (name.equals("addPolicy")) {
                    if (conflict) return authzPolicy;
                    completedMutations.incrementAndGet();
                    return null;
                }
                if (name.equals("removePolicy")) {
                    if (missing) return new TreeSet<PolicyVersion>();
                    completedMutations.incrementAndGet();
                    return new TreeSet<>(List.of(new PolicyVersion("1.0")));
                }
                if (name.equals("removePolicyVersion")) {
                    if (missing) return null;
                    completedMutations.incrementAndGet();
                    return authzPolicy;
                }
                if (name.equals("removeDomain")) {
                    if (missing) return null;
                    completedMutations.incrementAndGet();
                    return proxy(ReadableDomainProperties.class, (p, m, a) -> null);
                }
                if (name.equals("setAttributeProviders")) {
                    completedMutations.incrementAndGet();
                    return args[0];
                }
                if (name.equals("setOtherPdpProperties")) {
                    completedMutations.incrementAndGet();
                    return proxy(ReadablePdpProperties.class, (p, m, a) -> switch (m.getName()) {
                        case "getLastModified" -> 0L;
                        case "getFeatures", "getApplicableRefPolicyRefs" -> Collections.emptyList();
                        default -> null;
                    });
                }
                throw new UnsupportedOperationException(name);
            });
            cached = new DomainResourceImpl.Factory<DomainDao<PolicyVersionResourceImpl, PolicyResourceImpl>>()
                    .getInstance("domain", () -> dao);
            repository = proxy(DomainsDao.class, (p, m, a) -> {
                if (m.getName().equals("getDomainDaoClient")) return cached;
                throw new UnsupportedOperationException(m.getName());
            });
        }

        private DomainDao<PolicyVersionResourceImpl, PolicyResourceImpl> dao() { return dao; }

        DomainResource resource(String domain, PapChangeListener listener)
        {
            return new DomainsResourceImpl(repository, "authorizedResource", null, listener).getDomainResource(domain);
        }
    }

    private static void add(DomainResource resource, PolicySet policy)
    {
        PhaseInterceptorChain chain = new PhaseInterceptorChain(
                new TreeSet<>(List.of(new org.apache.cxf.phase.Phase("invoke", 1))));
        java.util.concurrent.atomic.AtomicReference<RuntimeException> failure = new java.util.concurrent.atomic.AtomicReference<>();
        chain.add(new org.apache.cxf.phase.AbstractPhaseInterceptor<org.apache.cxf.message.Message>("invoke") {
            @Override public void handleMessage(org.apache.cxf.message.Message message) {
                try { resource.getPapResource().getPoliciesResource().addPolicy(policy); }
                catch (RuntimeException e) { failure.set(e); }
            }
        });
        chain.doIntercept(new MessageImpl());
        if (failure.get() != null) throw failure.get();
    }

    @Test
    public void emitsAllSupportedOperationsAfterSuccess() throws Exception
    {
        Fixture f = new Fixture();
        List<PapChangeEvent> events = new ArrayList<>();
        DomainResource resource = f.resource("domain", event -> {
            assertEquals(f.completedMutations.get(), events.size() + 1);
            events.add(event);
        });
        add(resource, f.policy);
        resource.getPapResource().getPoliciesResource().getPolicyResource("policy").getPolicyVersionResource("latest").deletePolicyVersion();
        resource.getPapResource().getPoliciesResource().getPolicyResource("policy").deletePolicy();
        resource.getPapResource().getAttributeProvidersResource().updateAttributeProviderList(new AttributeProviders(Collections.emptyList()));
        resource.getPapResource().getPdpPropertiesResource().updateOtherPdpProperties(new PdpPropertiesUpdate(Collections.emptyList(), null));
        resource.deleteDomain();
        assertEquals(events.stream().map(PapChangeEvent::operation).toList(), List.of(
                PapChangeEvent.Operation.POLICY_VERSION_ADDED, PapChangeEvent.Operation.POLICY_VERSION_DELETED,
                PapChangeEvent.Operation.POLICY_DELETED, PapChangeEvent.Operation.ATTRIBUTE_PROVIDERS_CHANGED,
                PapChangeEvent.Operation.PDP_CONFIGURATION_CHANGED, PapChangeEvent.Operation.DOMAIN_DELETED));
        assertEquals(events.get(1).policyVersion(), "1.0");
        assertEquals(events.get(2).policyId(), "policy");
        assertNull(events.get(2).policyVersion());
        assertNull(events.get(3).policyId());
        assertEquals(events.stream().map(PapChangeEvent::eventId).distinct().count(), 6L);
        assertTrue(events.stream().allMatch(e -> e.domainId().equals("domain") && e.timestamp() != null));
    }

    @Test
    public void rejectedOperationsDoNotNotify() throws Exception
    {
        Fixture f = new Fixture();
        List<PapChangeEvent> events = new ArrayList<>();
        DomainResource r = f.resource("domain", events::add);
        f.conflict = true;
        expectThrows(ClientErrorException.class, () -> add(r, f.policy));
        f.missing = true;
        expectThrows(NotFoundException.class, () -> r.getPapResource().getPoliciesResource().getPolicyResource("policy").deletePolicy());
        expectThrows(NotFoundException.class, () -> r.getPapResource().getPoliciesResource().getPolicyResource("policy").getPolicyVersionResource("1.0").deletePolicyVersion());
        expectThrows(NotFoundException.class, r::deleteDomain);
        f.invalid = true;
        expectThrows(BadRequestException.class, () -> r.getPapResource().getAttributeProvidersResource().updateAttributeProviderList(new AttributeProviders(Collections.emptyList())));
        expectThrows(BadRequestException.class, () -> r.getPapResource().getPdpPropertiesResource().updateOtherPdpProperties(new PdpPropertiesUpdate(Collections.emptyList(), null)));
        f.invalid = false;
        f.ioFailure = true;
        expectThrows(InternalServerErrorException.class, () -> add(r, f.policy));
        assertTrue(events.isEmpty());
    }

    @Test
    public void listenerFailureDoesNotChangeSuccessAndCachedClientsStayUnchanged() throws Exception
    {
        Fixture f = new Fixture();
        DomainResource r = f.resource("domain", e -> { throw new IllegalStateException("Listener unavailable"); });
        r.getPapResource().getPoliciesResource().getPolicyResource("policy").deletePolicy();
        assertEquals(f.completedMutations.get(), 1);
        assertSame(new DomainsResourceImpl(f.repository, "authorizedResource", null).getDomainResource("domain"), f.cached);
        f.cached.getPolicyResource("policy").deletePolicy();
        assertEquals(f.completedMutations.get(), 2);
    }

    @Test
    public void concurrentViewsKeepTheirOwnListenerAndDomain() throws Exception
    {
        Fixture f = new Fixture();
        List<PapChangeEvent> first = new CopyOnWriteArrayList<>();
        List<PapChangeEvent> second = new CopyOnWriteArrayList<>();
        DomainResource a = f.resource("a", first::add);
        DomainResource b = f.resource("b", second::add);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Callable<Void>> tasks = List.of(
                () -> { for (int i = 0; i < 30; i++) a.getPapResource().getPoliciesResource().getPolicyResource("policy").deletePolicy(); return null; },
                () -> { for (int i = 0; i < 30; i++) b.getPapResource().getPoliciesResource().getPolicyResource("policy").getPolicyVersionResource("1.0").deletePolicyVersion(); return null; });
            for (Future<Void> result : pool.invokeAll(tasks)) result.get();
        } finally { pool.shutdownNow(); }
        assertEquals(first.size(), 30);
        assertEquals(second.size(), 30);
        assertTrue(first.stream().allMatch(e -> e.domainId().equals("a")));
        assertTrue(second.stream().allMatch(e -> e.domainId().equals("b")));
    }
}

