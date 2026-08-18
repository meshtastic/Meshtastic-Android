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
 * Covers the `ACCESS_LOCAL_NETWORK` gate applied to the Connections paths that open a socket without a scan having run
 * — manual address entry and the persisted recent-address list. On Android 17 an ungated connect times out rather than
 * failing fast, so a wrong branch here is a silent hang, not an error the user can act on.
 */
class LocalNetworkGateTest {

    @Test
    fun `granted proceeds`() {
        assertEquals(LocalNetworkGateAction.PROCEED, localNetworkGateAction(PermissionStatus.GRANTED))
    }

    @Test
    fun `never requested prompts in-context rather than opening settings`() {
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
    fun `permanent denial routes to app settings because the system will not prompt again`() {
        assertEquals(
            LocalNetworkGateAction.OPEN_APP_SETTINGS,
            localNetworkGateAction(PermissionStatus.PERMANENTLY_DENIED),
        )
    }
}
