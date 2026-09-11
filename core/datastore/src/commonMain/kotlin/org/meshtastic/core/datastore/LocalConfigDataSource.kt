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
import org.meshtastic.core.datastore.di.CoreLocalConfigDataStore
import org.meshtastic.proto.Config
import org.meshtastic.proto.LocalConfig

/** Class that handles saving and retrieving [LocalConfig] data. */
@Single
class LocalConfigDataSource(private val localConfigStore: CoreLocalConfigDataStore) {
    val localConfigFlow: Flow<LocalConfig> =
        localConfigStore.data.catch { exception ->
            // dataStore.data throws an IOException when an error is encountered when reading data
            if (exception is IOException) {
                Logger.e { "Error reading LocalConfig settings: ${exception.message}" }
                emit(LocalConfig.Builder().build())
            } else {
                throw exception
            }
        }

    suspend fun clearLocalConfig() {
        localConfigStore.updateData { LocalConfig.Builder().build() }
    }

    /** Updates [LocalConfig] from each [Config] oneOf. */
    suspend fun setLocalConfig(config: Config) = localConfigStore.updateData { current ->
        when {
            config.device != null -> current.newBuilder().also { wb -> wb.device = config.device }.build()
            config.position != null -> current.newBuilder().also { wb -> wb.position = config.position }.build()
            config.power != null -> current.newBuilder().also { wb -> wb.power = config.power }.build()
            config.network != null -> current.newBuilder().also { wb -> wb.network = config.network }.build()
            config.display != null -> current.newBuilder().also { wb -> wb.display = config.display }.build()
            config.lora != null -> current.newBuilder().also { wb -> wb.lora = config.lora }.build()
            config.bluetooth != null -> current.newBuilder().also { wb -> wb.bluetooth = config.bluetooth }.build()
            config.security != null -> current.newBuilder().also { wb -> wb.security = config.security }.build()
            else -> current
        }
    }
}
