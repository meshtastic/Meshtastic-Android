/*
 * Copyright (c) 2026 Meshtastic LLC
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.meshtastic.core.network.transport

import kotlinx.io.IOException

/**
 * Whether this throwable is an ordinary, expected connection failure rather than an application defect.
 *
 * A radio that is powered off, a hostname that no longer resolves, a broker that closes the socket, or Wi-Fi dropping
 * mid-read are all routine for a mesh client: the transport simply retries. Reporting them to crash/error tracking
 * buries real defects — an unreachable host accounted for hundreds of "errors" per day with nothing to fix.
 *
 * Callers should still log these (at warn) so a support session can see them; they just should not be raised as errors.
 */
internal fun Throwable.isExpectedConnectionFailure(): Boolean = this is IOException || isPlatformConnectionFailure()

/**
 * Platform-specific expected connection failures that do not extend [IOException].
 *
 * Kept separate so the common predicate above covers the (large) [IOException] family once, on every target.
 */
internal expect fun Throwable.isPlatformConnectionFailure(): Boolean
