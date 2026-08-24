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
package org.meshtastic.core.service

/**
 * Whether the platform currently permits opening a socket to a local-network address.
 *
 * Android 17 (API 37) gates local-network traffic behind `ACCESS_LOCAL_NETWORK`, and the gate is on the socket rather
 * than on discovery: a blocked TCP connect does not fail fast, it *times out*. The service reconnects to a persisted
 * device address at startup with no UI in sight, so without this check a TCP user who has not granted the permission
 * simply waits out a socket timeout with nothing to explain it.
 *
 * A service has no `Activity`, so it cannot request the permission — it can only decline to try and say why. The
 * request itself belongs to `ConnectionsScreen`.
 *
 * Implementations are per-platform and granted-by-construction everywhere the concept does not apply.
 */
fun interface LocalNetworkAccess {
    fun isGranted(): Boolean
}
