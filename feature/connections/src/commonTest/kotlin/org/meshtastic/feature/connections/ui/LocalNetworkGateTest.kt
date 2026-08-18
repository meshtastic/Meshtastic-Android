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
package org.meshtastic.feature.connections.ui

import org.meshtastic.core.ui.util.PermissionStatus
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Covers the `ACCESS_LOCAL_NETWORK` policy applied to the Connections TCP-connect paths: prompt when possible, warn
 * when not, never block. A TCP address says nothing about locality — public-IP/port-forward/VPN radios need no
 * permission — so no branch may refuse the connect outright; on Android 17 the OS enforces the permission at the socket
 * regardless.
 */
class LocalNetworkGateTest {

    @Test
    fun `granted proceeds silently`() {
        assertEquals(LocalNetworkGateAction.PROCEED, localNetworkGateAction(PermissionStatus.GRANTED))
    }

    @Test
    fun `never requested prompts in-context`() {
        assertEquals(LocalNetworkGateAction.REQUEST_PERMISSION, localNetworkGateAction(PermissionStatus.NOT_REQUESTED))
    }

    @Test
    fun `retryable denial prompts again`() {
        assertEquals(
            LocalNetworkGateAction.REQUEST_PERMISSION,
            localNetworkGateAction(PermissionStatus.DENIED_CAN_RETRY),
        )
    }

    @Test
    fun `permanent denial warns but still proceeds because the system will not prompt again`() {
        assertEquals(
            LocalNetworkGateAction.PROCEED_WITH_WARNING,
            localNetworkGateAction(PermissionStatus.PERMANENTLY_DENIED),
        )
    }

    // ── Resolution of a connect stashed behind an in-flight permission request ──

    @Test
    fun `a grant runs the stashed connect`() {
        assertEquals(
            PendingTcpConnectResolution.CONNECT,
            resolvePendingTcpConnect(
                stashedStatus = PermissionStatus.NOT_REQUESTED,
                currentStatus = PermissionStatus.GRANTED,
            ),
        )
    }

    @Test
    fun `a first denial warns and runs the stashed connect anyway`() {
        assertEquals(
            PendingTcpConnectResolution.CONNECT_WITH_WARNING,
            resolvePendingTcpConnect(
                stashedStatus = PermissionStatus.NOT_REQUESTED,
                currentStatus = PermissionStatus.DENIED_CAN_RETRY,
            ),
        )
    }

    @Test
    fun `a denial that becomes permanent warns and runs the stashed connect anyway`() {
        assertEquals(
            PendingTcpConnectResolution.CONNECT_WITH_WARNING,
            resolvePendingTcpConnect(
                stashedStatus = PermissionStatus.DENIED_CAN_RETRY,
                currentStatus = PermissionStatus.PERMANENTLY_DENIED,
            ),
        )
    }

    @Test
    fun `an unchanged status keeps waiting for the request to resolve`() {
        assertEquals(
            PendingTcpConnectResolution.KEEP_WAITING,
            resolvePendingTcpConnect(
                stashedStatus = PermissionStatus.NOT_REQUESTED,
                currentStatus = PermissionStatus.NOT_REQUESTED,
            ),
        )
    }
}
