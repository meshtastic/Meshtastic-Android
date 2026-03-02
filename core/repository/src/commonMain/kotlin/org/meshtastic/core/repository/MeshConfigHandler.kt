/*
 * Copyright (c) 2025 Meshtastic LLC
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

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import org.meshtastic.proto.Channel
import org.meshtastic.proto.Config
import org.meshtastic.proto.LocalConfig
import org.meshtastic.proto.LocalModuleConfig
import org.meshtastic.proto.ModuleConfig

/**
 * Interface for handling device and module configuration updates.
 */
interface MeshConfigHandler {
    /** Starts the handler with the given coroutine scope. */
    fun start(scope: CoroutineScope)

    /** Reactive local configuration. */
    val localConfig: StateFlow<LocalConfig>

    /** Reactive local module configuration. */
    val moduleConfig: StateFlow<LocalModuleConfig>

    /** Handles a received device configuration. */
    fun handleDeviceConfig(config: Config)

    /** Handles a received module configuration. */
    fun handleModuleConfig(config: ModuleConfig)

    /** Handles a received channel configuration. */
    fun handleChannel(channel: Channel)
}
