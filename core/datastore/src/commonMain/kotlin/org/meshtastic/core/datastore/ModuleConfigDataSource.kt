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
package org.meshtastic.core.datastore

import co.touchlab.kermit.Logger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import okio.IOException
import org.koin.core.annotation.Single
import org.meshtastic.core.datastore.di.CoreModuleConfigDataStore
import org.meshtastic.proto.LocalModuleConfig
import org.meshtastic.proto.ModuleConfig

/** Class that handles saving and retrieving [LocalModuleConfig] data. */
@Single
class ModuleConfigDataSource(private val moduleConfigStore: CoreModuleConfigDataStore) {
    val moduleConfigFlow: Flow<LocalModuleConfig> =
        moduleConfigStore.data.catch { exception ->
            // dataStore.data throws an IOException when an error is encountered when reading data
            if (exception is IOException) {
                Logger.e { "Error reading LocalModuleConfig settings: ${exception.message}" }
                emit(LocalModuleConfig.Builder().build())
            } else {
                throw exception
            }
        }

    suspend fun clearLocalModuleConfig() {
        moduleConfigStore.updateData { LocalModuleConfig.Builder().build() }
    }

    /** Updates [LocalModuleConfig] from each [ModuleConfig] oneOf. */
    @Suppress("CyclomaticComplexMethod")
    suspend fun setLocalModuleConfig(config: ModuleConfig) = moduleConfigStore.updateData { current ->
        when {
            config.mqtt != null -> current.newBuilder().also { wb -> wb.mqtt = config.mqtt }.build()

            config.serial != null -> current.newBuilder().also { wb -> wb.serial = config.serial }.build()

            config.external_notification != null ->
                current.newBuilder().also { wb -> wb.external_notification = config.external_notification }.build()

            config.store_forward != null -> current.newBuilder().also { wb -> wb.store_forward = config.store_forward }.build()

            config.range_test != null -> current.newBuilder().also { wb -> wb.range_test = config.range_test }.build()

            config.telemetry != null -> current.newBuilder().also { wb -> wb.telemetry = config.telemetry }.build()

            config.canned_message != null -> current.newBuilder().also { wb -> wb.canned_message = config.canned_message }.build()

            config.audio != null -> current.newBuilder().also { wb -> wb.audio = config.audio }.build()

            config.remote_hardware != null -> current.newBuilder().also { wb -> wb.remote_hardware = config.remote_hardware }.build()

            config.neighbor_info != null -> current.newBuilder().also { wb -> wb.neighbor_info = config.neighbor_info }.build()

            config.ambient_lighting != null -> current.newBuilder().also { wb -> wb.ambient_lighting = config.ambient_lighting }.build()

            config.detection_sensor != null -> current.newBuilder().also { wb -> wb.detection_sensor = config.detection_sensor }.build()

            config.paxcounter != null -> current.newBuilder().also { wb -> wb.paxcounter = config.paxcounter }.build()

            config.statusmessage != null -> current.newBuilder().also { wb -> wb.statusmessage = config.statusmessage }.build()

            config.tak != null -> current.newBuilder().also { wb -> wb.tak = config.tak }.build()

            config.mesh_beacon != null -> current.newBuilder().also { wb -> wb.mesh_beacon = config.mesh_beacon }.build()

            else -> current
        }
    }
}
