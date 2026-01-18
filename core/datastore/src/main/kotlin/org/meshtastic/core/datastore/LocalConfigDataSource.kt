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
import org.meshtastic.proto.Config
import org.meshtastic.proto.LocalConfig
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** Class that handles saving and retrieving [LocalConfig] data. */
@Singleton
class LocalConfigDataSource @Inject constructor(private val localConfigStore: DataStore<LocalConfig>) {
    val localConfigFlow: Flow<LocalConfig> =
        localConfigStore.data.catch { exception ->
            // dataStore.data throws an IOException when an error is encountered when reading data
            if (exception is IOException) {
                Logger.e { "Error reading LocalConfig settings: ${exception.message}" }
                emit(LocalConfig())
            } else {
                throw exception
            }
        }

    suspend fun clearLocalConfig() {
        localConfigStore.updateData { _ -> LocalConfig() }
    }

    /** Updates [LocalConfig] from each [Config] oneOf. */
    suspend fun setLocalConfig(config: Config) = localConfigStore.updateData { local ->
        local.copy(
            device = config.device ?: local.device,
            position = config.position ?: local.position,
            power = config.power ?: local.power,
            network = config.network ?: local.network,
            display = config.display ?: local.display,
            lora = config.lora ?: local.lora,
            bluetooth = config.bluetooth ?: local.bluetooth,
            security = config.security ?: local.security,
        )
    }
}
