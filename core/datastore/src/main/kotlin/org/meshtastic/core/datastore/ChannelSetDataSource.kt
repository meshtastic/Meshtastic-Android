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

package org.meshtastic.core.datastore

import androidx.datastore.core.DataStore
import co.touchlab.kermit.Logger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import org.meshtastic.proto.AppOnlyProtos.ChannelSet
import org.meshtastic.proto.ChannelProtos.Channel
import org.meshtastic.proto.ChannelProtos.ChannelSettings
import org.meshtastic.proto.ConfigProtos
import java.io.IOException
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
                emit(ChannelSet.getDefaultInstance())
            } else {
                throw exception
            }
        }

    suspend fun clearChannelSet() {
        channelSetStore.updateData { preference -> preference.toBuilder().clear().build() }
    }

    suspend fun clearSettings() {
        channelSetStore.updateData { preference -> preference.toBuilder().clearSettings().build() }
    }

    suspend fun addAllSettings(settingsList: List<ChannelSettings>) {
        channelSetStore.updateData { preference -> preference.toBuilder().addAllSettings(settingsList).build() }
    }

    /** Updates the [ChannelSettings] list with the provided channel. */
    suspend fun updateChannelSettings(channel: Channel) {
        if (channel.role == Channel.Role.DISABLED) return
        channelSetStore.updateData { preference ->
            val builder = preference.toBuilder()
            // Resize to fit channel
            while (builder.settingsCount <= channel.index) {
                builder.addSettings(ChannelSettings.getDefaultInstance())
            }
            // use setSettings() to ensure settingsList and channel indexes match
            builder.setSettings(channel.index, channel.settings).build()
        }
    }

    suspend fun setLoraConfig(config: ConfigProtos.Config.LoRaConfig) {
        channelSetStore.updateData { preference -> preference.toBuilder().setLoraConfig(config).build() }
    }
}
