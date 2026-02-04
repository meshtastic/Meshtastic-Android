/*
 * Copyright (c) 2025-2026 Meshtastic LLC
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
package org.meshtastic.core.datastore

import androidx.datastore.core.DataStore
import co.touchlab.kermit.Logger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import okio.IOException
import org.meshtastic.proto.LocalModuleConfig
import org.meshtastic.proto.ModuleConfig
import javax.inject.Inject
import javax.inject.Singleton

/** Class that handles saving and retrieving [LocalModuleConfig] data. */
@Singleton
class ModuleConfigDataSource @Inject constructor(private val moduleConfigStore: DataStore<LocalModuleConfig>) {
    val moduleConfigFlow: Flow<LocalModuleConfig> =
        moduleConfigStore.data.catch { exception ->
            // dataStore.data throws an IOException when an error is encountered when reading data
            if (exception is IOException) {
                Logger.e { "Error reading LocalModuleConfig settings: ${exception.message}" }
                emit(LocalModuleConfig())
            } else {
                throw exception
            }
        }

    suspend fun clearLocalModuleConfig() {
        moduleConfigStore.updateData { LocalModuleConfig() }
    }

    /** Updates [LocalModuleConfig] from each [ModuleConfig] oneOf. */
    suspend fun setLocalModuleConfig(config: ModuleConfig) = moduleConfigStore.updateData { current ->
        when {
            config.mqtt != null -> current.copy(mqtt = config.mqtt)
            config.serial != null -> current.copy(serial = config.serial)
            config.external_notification != null ->
                current.copy(external_notification = config.external_notification)
            config.store_forward != null -> current.copy(store_forward = config.store_forward)
            config.range_test != null -> current.copy(range_test = config.range_test)
            config.telemetry != null -> current.copy(telemetry = config.telemetry)
            config.canned_message != null -> current.copy(canned_message = config.canned_message)
            config.audio != null -> current.copy(audio = config.audio)
            config.remote_hardware != null -> current.copy(remote_hardware = config.remote_hardware)
            config.neighbor_info != null -> current.copy(neighbor_info = config.neighbor_info)
            config.ambient_lighting != null -> current.copy(ambient_lighting = config.ambient_lighting)
            config.detection_sensor != null -> current.copy(detection_sensor = config.detection_sensor)
            config.paxcounter != null -> current.copy(paxcounter = config.paxcounter)
            config.statusmessage != null -> current.copy(statusmessage = config.statusmessage)
            else -> current
        }
    }
}
