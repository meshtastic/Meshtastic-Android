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
import org.meshtastic.proto.Channel
import org.meshtastic.proto.ChannelSet
import org.meshtastic.proto.ChannelSettings
import org.meshtastic.proto.Config
import javax.inject.Inject
import javax.inject.Singleton

/** Class that handles saving and retrieving [ChannelSet] data. */
@Singleton
class ChannelSetDataSource @Inject constructor(private val channelSetStore: DataStore<ChannelSet>) {
    val channelSetFlow: Flow<ChannelSet> =
        channelSetStore.data.catch { exception ->
            // dataStore.data throws an IOException when an error is encountered when reading data
            if (exception is IOException) {
                Logger.e { "Error reading DeviceConfig settings: ${exception.message}" }
                emit(ChannelSet())
            } else {
                throw exception
            }
        }

    suspend fun clearChannelSet() {
        channelSetStore.updateData { ChannelSet() }
    }

    /** Replaces all [ChannelSettings] in a single atomic operation. */
    suspend fun replaceAllSettings(settingsList: List<ChannelSettings>) {
        channelSetStore.updateData { it.copy(settings = settingsList) }
    }

    /** Updates the [ChannelSettings] list with the provided channel. */
    suspend fun updateChannelSettings(channel: Channel) {
        if (channel.role == Channel.Role.DISABLED) return
        channelSetStore.updateData { preference ->
            val settings = preference.settings.toMutableList()
            // Resize to fit channel
            while (settings.size <= channel.index) {
                settings.add(ChannelSettings())
            }
            // use setSettings() to ensure settingsList and channel indexes match
            settings[channel.index] = channel.settings ?: ChannelSettings()
            preference.copy(settings = settings)
        }
    }

    suspend fun setLoraConfig(config: Config.LoRaConfig) {
        channelSetStore.updateData { it.copy(lora_config = config) }
    }
}
