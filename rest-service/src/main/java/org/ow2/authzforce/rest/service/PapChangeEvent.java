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
package org.ow2.authzforce.rest.service;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Successful PAP API operation; does not imply activation of an added policy version.
 * Identifiers and timestamps are not a domain revision or an ordering guarantee.
 * @param eventId unique notification identifier
 * @param timestamp notification creation time
 * @param domainId affected domain
 * @param operation successful operation
 * @param policyId affected policy, or null for domain/configuration operations
 * @param policyVersion affected version, or null for whole-policy/domain/configuration operations
 */
public record PapChangeEvent(UUID eventId, Instant timestamp, String domainId,
                             Operation operation, String policyId, String policyVersion)
{
    /** Supported PAP operations. Automatic rolling and direct file edits are not separate events. */
    public enum Operation
    {
        /** A policy version was added. */
        POLICY_VERSION_ADDED,
        /** A specific policy version was removed. */
        POLICY_VERSION_DELETED,
        /** A policy and its versions were removed. */
        POLICY_DELETED,
        /** PDP configuration, including root policy selection, was updated. */
        PDP_CONFIGURATION_CHANGED,
        /** Attribute-provider configuration was updated, not its external source data. */
        ATTRIBUTE_PROVIDERS_CHANGED,
        /** A domain was removed. */
        DOMAIN_DELETED
    }

    /** Validate mandatory metadata and the operation-specific policy identifiers. */
    public PapChangeEvent
    {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(timestamp, "timestamp");
        Objects.requireNonNull(domainId, "domainId");
        Objects.requireNonNull(operation, "operation");
        switch (operation)
        {
            case POLICY_VERSION_ADDED, POLICY_VERSION_DELETED -> {
                Objects.requireNonNull(policyId, "policyId");
                Objects.requireNonNull(policyVersion, "policyVersion");
            }
            case POLICY_DELETED -> {
                Objects.requireNonNull(policyId, "policyId");
                if (policyVersion != null) throw new IllegalArgumentException("Whole-policy event cannot specify a version");
            }
            default -> {
                if (policyId != null || policyVersion != null)
                    throw new IllegalArgumentException("Configuration/domain event cannot specify a policy");
            }
        }
    }
}

