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

package org.meshtastic.core.data.datasource

import android.app.Application
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import org.meshtastic.core.model.NetworkDeviceHardware
import javax.inject.Inject

class DeviceHardwareJsonDataSource @Inject constructor(private val application: Application) {

    // Use a tolerant JSON parser so that additional fields in the bundled asset
    // (e.g., "key") do not break deserialization on older app versions.
    @OptIn(ExperimentalSerializationApi::class)
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    @OptIn(ExperimentalSerializationApi::class)
    fun loadDeviceHardwareFromJsonAsset(): List<NetworkDeviceHardware> =
        application.assets.open("device_hardware.json").use { inputStream ->
            json.decodeFromStream<List<NetworkDeviceHardware>>(inputStream)
        }
}
