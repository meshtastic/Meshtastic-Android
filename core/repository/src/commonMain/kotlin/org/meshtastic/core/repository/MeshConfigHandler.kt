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
package org.meshtastic.core.repository

import kotlinx.coroutines.flow.StateFlow
import org.meshtastic.proto.Channel
import org.meshtastic.proto.Config
import org.meshtastic.proto.DeviceUIConfig
import org.meshtastic.proto.LoRaRegionPresetMap
import org.meshtastic.proto.LocalConfig
import org.meshtastic.proto.LocalModuleConfig
import org.meshtastic.proto.ModuleConfig

/** Interface for handling device and module configuration updates. */
interface MeshConfigHandler {
    /** Reactive local configuration. */
    val localConfig: StateFlow<LocalConfig>

    /** Reactive local module configuration. */
    val moduleConfig: StateFlow<LocalModuleConfig>

    /**
     * Handles a received device configuration.
     *
     * @param session The transport session that admitted [config].
     * @return `true` when the update is admitted for the current session; `false` when [session] is stale or revoked.
     */
    fun handleDeviceConfig(config: Config, session: RadioSessionContext): Boolean

    /**
     * Handles a received module configuration.
     *
     * @param session The transport session that admitted [config].
     * @return `true` when the update is admitted for the current session; `false` when [session] is stale or revoked.
     */
    fun handleModuleConfig(config: ModuleConfig, session: RadioSessionContext): Boolean

    /**
     * Handles a received channel configuration.
     *
     * @param session The transport session that admitted [channel].
     * @return `true` when the update is admitted for the current session; `false` when [session] is stale or revoked.
     */
    fun handleChannel(channel: Channel, session: RadioSessionContext): Boolean

    /**
     * Handles the [DeviceUIConfig] received during the config handshake (STATE_SEND_UIDATA). This arrives as the 2nd
     * packet in every handshake, immediately after my_info.
     *
     * @param session The transport session that admitted [config].
     * @return `true` when the update is admitted for the current session; `false` when [session] is stale or revoked.
     */
    fun handleDeviceUIConfig(config: DeviceUIConfig, session: RadioSessionContext): Boolean

    /**
     * Handles the [LoRaRegionPresetMap] received during the config handshake (after metadata, before channels). It
     * describes which modem presets are legal in each LoRa region. Absent on firmware older than 2.8.
     *
     * @param session The transport session that admitted [map].
     * @return `true` when the update is admitted for the current session; `false` when [session] is stale or revoked.
     */
    fun handleRegionPresets(map: LoRaRegionPresetMap, session: RadioSessionContext): Boolean
}
