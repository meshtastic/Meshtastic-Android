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
import org.meshtastic.proto.LocalModuleConfig
import org.meshtastic.proto.ModuleConfig
import java.io.IOException
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
        moduleConfigStore.updateData { _ -> LocalModuleConfig() }
    }

    /** Updates [LocalModuleConfig] from each [ModuleConfig] oneOf. */
    suspend fun setLocalModuleConfig(config: ModuleConfig) = moduleConfigStore.updateData { local ->
        local.copy(
            mqtt = config.mqtt ?: local.mqtt,
            serial = config.serial ?: local.serial,
            external_notification = config.external_notification ?: local.external_notification,
            store_forward = config.store_forward ?: local.store_forward,
            range_test = config.range_test ?: local.range_test,
            telemetry = config.telemetry ?: local.telemetry,
            canned_message = config.canned_message ?: local.canned_message,
            audio = config.audio ?: local.audio,
            remote_hardware = config.remote_hardware ?: local.remote_hardware,
            neighbor_info = config.neighbor_info ?: local.neighbor_info,
            ambient_lighting = config.ambient_lighting ?: local.ambient_lighting,
            detection_sensor = config.detection_sensor ?: local.detection_sensor,
            paxcounter = config.paxcounter ?: local.paxcounter,
        )
    }
}
