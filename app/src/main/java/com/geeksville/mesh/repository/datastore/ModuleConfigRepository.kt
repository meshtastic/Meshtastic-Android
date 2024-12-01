/*
 * Copyright (c) 2024 Meshtastic LLC
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

package com.geeksville.mesh.repository.datastore

import androidx.datastore.core.DataStore
import com.geeksville.mesh.android.Logging
import com.geeksville.mesh.ModuleConfigProtos.ModuleConfig
import com.geeksville.mesh.LocalOnlyProtos.LocalModuleConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import java.io.IOException
import javax.inject.Inject

/**
 * Class that handles saving and retrieving [LocalModuleConfig] data.
 */
class ModuleConfigRepository @Inject constructor(
    private val moduleConfigStore: DataStore<LocalModuleConfig>,
) : Logging {
    val moduleConfigFlow: Flow<LocalModuleConfig> = moduleConfigStore.data
        .catch { exception ->
            // dataStore.data throws an IOException when an error is encountered when reading data
            if (exception is IOException) {
                errormsg("Error reading LocalModuleConfig settings: ${exception.message}")
                emit(LocalModuleConfig.getDefaultInstance())
            } else {
                throw exception
            }
        }

    suspend fun clearLocalModuleConfig() {
        moduleConfigStore.updateData { preference ->
            preference.toBuilder().clear().build()
        }
    }

    /**
     * Updates [LocalModuleConfig] from each [ModuleConfig] oneOf.
     */
    suspend fun setLocalModuleConfig(config: ModuleConfig) = moduleConfigStore.updateData {
        val builder = it.toBuilder()
        config.allFields.forEach { (field, value) ->
            val localField = it.descriptorForType.findFieldByName(field.name)
            if (localField != null) {
                builder.setField(localField, value)
            } else {
                errormsg("Error writing LocalModuleConfig settings: ${config.payloadVariantCase}")
            }
        }
        builder.build()
    }
}
