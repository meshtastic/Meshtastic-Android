package com.geeksville.mesh.repository.datastore

import androidx.datastore.core.DataStore
import com.geeksville.mesh.android.Logging
import com.geeksville.mesh.ModuleConfigProtos.ModuleConfig
import com.geeksville.mesh.LocalOnlyProtos.LocalModuleConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import java.io.IOException
import javax.inject.Inject

/**
 * Class that handles saving and retrieving config settings
 */
class ModuleConfigRepository @Inject constructor(
    private val moduleConfigStore: DataStore<LocalModuleConfig>,
) : Logging {
    val moduleConfigFlow: Flow<LocalModuleConfig> = moduleConfigStore.data
        .catch { exception ->
            // dataStore.data throws an IOException when an error is encountered when reading data
            if (exception is IOException) {
                errormsg("Error reading LocalConfig settings: ${exception.message}")
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
     * Update LocalModuleConfig from each ModuleConfigProtos.ModuleConfig Oneof
     */
    suspend fun setLocalModuleConfig(config: ModuleConfig) {
        if (config.hasMqtt()) setMQTTConfig(config.mqtt)
        if (config.hasSerial()) setSerialConfig(config.serial)
        if (config.hasExternalNotification()) setExternalNotificationConfig(config.externalNotification)
        if (config.hasStoreForward()) setStoreForwardConfig(config.storeForward)
        if (config.hasRangeTest()) setRangeTestConfig(config.rangeTest)
        if (config.hasTelemetry()) setTelemetryConfig(config.telemetry)
        if (config.hasCannedMessage()) setCannedMessageConfig(config.cannedMessage)
        if (config.hasAudio()) setAudioConfig(config.audio)
    }

    private suspend fun setMQTTConfig(config: ModuleConfig.MQTTConfig) {
        moduleConfigStore.updateData { preference ->
            preference.toBuilder().setMqtt(config).build()
        }
    }

    private suspend fun setSerialConfig(config: ModuleConfig.SerialConfig) {
        moduleConfigStore.updateData { preference ->
            preference.toBuilder().setSerial(config).build()
        }
    }

    private suspend fun setExternalNotificationConfig(config: ModuleConfig.ExternalNotificationConfig) {
        moduleConfigStore.updateData { preference ->
            preference.toBuilder().setExternalNotification(config).build()
        }
    }

    private suspend fun setStoreForwardConfig(config: ModuleConfig.StoreForwardConfig) {
        moduleConfigStore.updateData { preference ->
            preference.toBuilder().setStoreForward(config).build()
        }
    }

    private suspend fun setRangeTestConfig(config: ModuleConfig.RangeTestConfig) {
        moduleConfigStore.updateData { preference ->
            preference.toBuilder().setRangeTest(config).build()
        }
    }

    private suspend fun setTelemetryConfig(config: ModuleConfig.TelemetryConfig) {
        moduleConfigStore.updateData { preference ->
            preference.toBuilder().setTelemetry(config).build()
        }
    }

    private suspend fun setCannedMessageConfig(config: ModuleConfig.CannedMessageConfig) {
        moduleConfigStore.updateData { preference ->
            preference.toBuilder().setCannedMessage(config).build()
        }
    }

    private suspend fun setAudioConfig(config: ModuleConfig.AudioConfig) {
        moduleConfigStore.updateData { preference ->
            preference.toBuilder().setAudio(config).build()
        }
    }

    suspend fun fetchInitialModuleConfig() = moduleConfigStore.data.first()

}
