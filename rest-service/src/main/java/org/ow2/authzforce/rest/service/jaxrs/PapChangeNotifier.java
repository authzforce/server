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

import java.time.Instant;
import java.util.UUID;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.ow2.authzforce.rest.service.PapChangeEvent;
import org.ow2.authzforce.rest.service.PapChangeListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Immutable domain-scoped notification context; never attached mutably to cached DAO clients. */
final class PapChangeNotifier
{
    static final PapChangeNotifier NONE = new PapChangeNotifier(null, null);
    private static final Logger LOGGER = LoggerFactory.getLogger(PapChangeNotifier.class);
    private final String domainId;
    private final PapChangeListener listener;

    PapChangeNotifier(String domainId, PapChangeListener listener)
    {
        this.domainId = domainId;
        this.listener = listener;
    }

    @SuppressFBWarnings(value = "CRLF_INJECTION_LOGS",
            justification = "The only formatted metadata is a server-generated UUID; the exception is logged as a Throwable.")
    void notifyChange(PapChangeEvent.Operation operation, String policyId, String version)
    {
        if (listener == null) return;
        final PapChangeEvent event = new PapChangeEvent(UUID.randomUUID(), Instant.now(), domainId, operation, policyId, version);
        try
        {
            listener.papChanged(event);
        }
        catch (RuntimeException e)
        {
            LOGGER.error("PAP change listener failed for event {}", event.eventId(), e);
        }
    }
}

