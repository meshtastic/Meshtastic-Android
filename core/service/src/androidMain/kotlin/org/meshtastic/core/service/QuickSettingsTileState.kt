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

import android.service.quicksettings.Tile
import org.meshtastic.core.model.ConnectionState

/** What a tap on the Quick Settings tile does. */
enum class QuickSettingsTileAction {
    /** Bring the selected radio's link up. */
    Connect,

    /** Deselect the radio, the same disconnect the Connect tab performs. */
    Disconnect,

    /** No radio is selected, so the tile can only send the user to the Connect tab. */
    OpenConnections,
}

/** The tile's secondary line. */
sealed interface QuickSettingsTileSubtitle {
    data class NodeName(val shortName: String) : QuickSettingsTileSubtitle

    data object Connected : QuickSettingsTileSubtitle

    data object Connecting : QuickSettingsTileSubtitle

    data object DeviceSleeping : QuickSettingsTileSubtitle

    data object Disconnected : QuickSettingsTileSubtitle

    data object NoDeviceSelected : QuickSettingsTileSubtitle
}

/** Everything the tile renders, with [state] one of `Tile.STATE_ACTIVE` or `Tile.STATE_INACTIVE`. */
data class QuickSettingsTileState(
    val state: Int,
    val subtitle: QuickSettingsTileSubtitle,
    val action: QuickSettingsTileAction,
)

/**
 * Maps the radio link onto the tile.
 *
 * A selected radio that is still connecting reads as active, matching the Connect tab, which treats any selected device
 * as in progress and offers disconnect; a tap then cancels the attempt. [serviceRunning] guards against a stale
 * [ConnectionState.Connected] after the service has stopped.
 */
fun quickSettingsTileState(
    connectionState: ConnectionState,
    hasSelectedDevice: Boolean,
    serviceRunning: Boolean,
    ourShortName: String?,
): QuickSettingsTileState = when {
    !hasSelectedDevice ->
        QuickSettingsTileState(
            Tile.STATE_INACTIVE,
            QuickSettingsTileSubtitle.NoDeviceSelected,
            QuickSettingsTileAction.OpenConnections,
        )

    !serviceRunning ->
        QuickSettingsTileState(
            Tile.STATE_INACTIVE,
            QuickSettingsTileSubtitle.Disconnected,
            QuickSettingsTileAction.Connect,
        )

    else ->
        when (connectionState) {
            ConnectionState.Connected ->
                QuickSettingsTileState(
                    Tile.STATE_ACTIVE,
                    ourShortName?.takeIf { it.isNotBlank() }?.let(QuickSettingsTileSubtitle::NodeName)
                        ?: QuickSettingsTileSubtitle.Connected,
                    QuickSettingsTileAction.Disconnect,
                )

            ConnectionState.Connecting ->
                QuickSettingsTileState(
                    Tile.STATE_ACTIVE,
                    QuickSettingsTileSubtitle.Connecting,
                    QuickSettingsTileAction.Disconnect,
                )

            ConnectionState.DeviceSleep ->
                QuickSettingsTileState(
                    Tile.STATE_INACTIVE,
                    QuickSettingsTileSubtitle.DeviceSleeping,
                    QuickSettingsTileAction.Connect,
                )

            ConnectionState.Disconnected ->
                QuickSettingsTileState(
                    Tile.STATE_INACTIVE,
                    QuickSettingsTileSubtitle.Disconnected,
                    QuickSettingsTileAction.Connect,
                )
        }
}
