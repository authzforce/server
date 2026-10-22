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

/**
 * Optional notification of successful PAP API operations.
 * Implementations must be thread-safe and return promptly: callbacks run synchronously
 * on request threads and may run concurrently or arrive out of mutation order.
 * Runtime exceptions are logged without changing the successful PAP response.
 * Delivery is best effort, not transactional with policy persistence.
 */
@FunctionalInterface
public interface PapChangeListener
{
    /**
     * Handle a successful PAP operation.
     * @param event immutable operation metadata (no policy or provider configuration payload)
     */
    void papChanged(PapChangeEvent event);
}

