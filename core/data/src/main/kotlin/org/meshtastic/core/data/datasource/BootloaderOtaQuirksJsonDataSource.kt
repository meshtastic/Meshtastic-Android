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
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import org.meshtastic.core.model.BootloaderOtaQuirk
import timber.log.Timber
import javax.inject.Inject

class BootloaderOtaQuirksJsonDataSource @Inject constructor(private val application: Application) {
    @OptIn(ExperimentalSerializationApi::class)
    fun loadBootloaderOtaQuirksFromJsonAsset(): List<BootloaderOtaQuirk> = runCatching {
        val inputStream = application.assets.open("device_bootloader_ota_quirks.json")
        inputStream.use { Json.decodeFromStream<ListWrapper>(it).devices }
    }
        .onFailure { e -> Timber.w(e, "Failed to load device_bootloader_ota_quirks.json") }
        .getOrDefault(emptyList())

    @Serializable private data class ListWrapper(val devices: List<BootloaderOtaQuirk> = emptyList())
}
