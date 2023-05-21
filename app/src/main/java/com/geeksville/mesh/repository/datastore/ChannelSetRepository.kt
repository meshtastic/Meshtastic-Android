package com.geeksville.mesh.repository.datastore

import androidx.datastore.core.DataStore
import com.geeksville.mesh.android.Logging
import com.geeksville.mesh.AppOnlyProtos.ChannelSet
import com.geeksville.mesh.ChannelProtos.Channel
import com.geeksville.mesh.ChannelProtos.ChannelSettings
import com.geeksville.mesh.ConfigProtos
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.firstOrNull
import java.io.IOException
import javax.inject.Inject

/**
 * Class that handles saving and retrieving [ChannelSet] data.
 */
class ChannelSetRepository @Inject constructor(
    private val channelSetStore: DataStore<ChannelSet>
) : Logging {
    val channelSetFlow: Flow<ChannelSet> = channelSetStore.data
        .catch { exception ->
            // dataStore.data throws an IOException when an error is encountered when reading data
            if (exception is IOException) {
                errormsg("Error reading DeviceConfig settings: ${exception.message}")
                emit(ChannelSet.getDefaultInstance())
            } else {
                throw exception
            }
        }

    suspend fun clearChannelSet() {
        channelSetStore.updateData { preference ->
            preference.toBuilder().clear().build()
        }
    }

    suspend fun clearSettings() {
        channelSetStore.updateData { preference ->
            preference.toBuilder().clearSettings().build()
        }
    }

    suspend fun addAllSettings(settingsList: List<ChannelSettings>) {
        channelSetStore.updateData { preference ->
            preference.toBuilder().addAllSettings(settingsList).build()
        }
    }

    /**
     * Updates the [ChannelSettings] list with the provided channel.
     */
    suspend fun updateChannelSettings(channel: Channel) {
        channelSetStore.updateData { preference ->
            if (preference.settingsCount > channel.index) {
                preference.toBuilder().setSettings(channel.index, channel.settings).build()
            } else {
                preference.toBuilder().addSettings(channel.settings).build()
            }
        }
    }

    suspend fun setLoraConfig(config: ConfigProtos.Config.LoRaConfig) {
        channelSetStore.updateData { preference ->
            preference.toBuilder().setLoraConfig(config).build()
        }
    }

    suspend fun fetchInitialChannelSet() = channelSetStore.data.firstOrNull()

}
