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
package org.meshtastic.core.ui.util

import org.jetbrains.compose.resources.StringResource
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.connected
import org.meshtastic.core.resources.connecting
import org.meshtastic.core.resources.connection_failed
import org.meshtastic.core.resources.device_sleeping
import org.meshtastic.core.resources.disconnected
import org.meshtastic.core.resources.disconnecting
import org.meshtastic.core.resources.reconnecting
import org.meshtastic.sdk.ConnectionState

/** The short name of this state, for a tooltip, badge or widget line. */
val ConnectionState.label: StringResource
    get() =
        when (this) {
            ConnectionState.Connected -> Res.string.connected

            is ConnectionState.Connecting,
            is ConnectionState.Configuring,
            -> Res.string.connecting

            is ConnectionState.Reconnecting -> Res.string.reconnecting

            is ConnectionState.DeviceSleeping -> Res.string.device_sleeping

            ConnectionState.Disconnecting -> Res.string.disconnecting

            is ConnectionState.Failed -> Res.string.connection_failed

            ConnectionState.Disconnected -> Res.string.disconnected
        }
