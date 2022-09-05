package com.geeksville.mesh.repository.datastore

import androidx.datastore.core.DataStore
import com.geeksville.mesh.android.Logging
import com.geeksville.mesh.ConfigProtos
import com.geeksville.mesh.LocalOnlyProtos.LocalConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.asSharedFlow
import java.io.IOException
import javax.inject.Inject

/**
 * Class that handles saving and retrieving config settings
 */
class LocalConfigRepository @Inject constructor(
    private val localConfigStore: DataStore<LocalConfig>
) : Logging {
    val localConfigFlow: Flow<LocalConfig> = localConfigStore.data
        .catch { exception ->
            // dataStore.data throws an IOException when an error is encountered when reading data
            if (exception is IOException) {
                errormsg("Error reading LocalConfig settings: ${exception.message}")
                emit(LocalConfig.getDefaultInstance())
            } else {
                throw exception
            }
        }

    private val _sendDeviceConfigFlow = MutableSharedFlow<ConfigProtos.Config>()
    val sendDeviceConfigFlow = _sendDeviceConfigFlow.asSharedFlow()

    private suspend fun sendDeviceConfig(config: ConfigProtos.Config) {
        debug("Sending device config!")
        _sendDeviceConfigFlow.emit(config)
    }

    /**
     * Update LocalConfig and send ConfigProtos.Config Oneof to the radio
     */
    suspend fun setRadioConfig(config: ConfigProtos.Config) {
        setLocalConfig(config)
        sendDeviceConfig(config)
    }

    suspend fun clearLocalConfig() {
        localConfigStore.updateData { preference ->
            preference.toBuilder().clear().build()
        }
    }

    /**
     * Update LocalConfig from each ConfigProtos.Config Oneof
     */
    suspend fun setLocalConfig(config: ConfigProtos.Config) {
        if (config.hasDevice()) setDeviceConfig(config.device)
        if (config.hasPosition()) setPositionConfig(config.position)
        if (config.hasPower()) setPowerConfig(config.power)
        if (config.hasWifi()) setWifiConfig(config.wifi)
        if (config.hasDisplay()) setDisplayConfig(config.display)
        if (config.hasLora()) setLoraConfig(config.lora)
        if (config.hasBluetooth()) setBluetoothConfig(config.bluetooth)
    }

    private suspend fun setDeviceConfig(config: ConfigProtos.Config.DeviceConfig) {
        localConfigStore.updateData { preference ->
            preference.toBuilder().setDevice(config).build()
        }
    }

    private suspend fun setPositionConfig(config: ConfigProtos.Config.PositionConfig) {
        localConfigStore.updateData { preference ->
            preference.toBuilder().setPosition(config).build()
        }
    }

    private suspend fun setPowerConfig(config: ConfigProtos.Config.PowerConfig) {
        localConfigStore.updateData { preference ->
            preference.toBuilder().setPower(config).build()
        }
    }

    private suspend fun setWifiConfig(config: ConfigProtos.Config.WiFiConfig) {
        localConfigStore.updateData { preference ->
            preference.toBuilder().setWifi(config).build()
        }
    }

    private suspend fun setDisplayConfig(config: ConfigProtos.Config.DisplayConfig) {
        localConfigStore.updateData { preference ->
            preference.toBuilder().setDisplay(config).build()
        }
    }

    private suspend fun setLoraConfig(config: ConfigProtos.Config.LoRaConfig) {
        localConfigStore.updateData { preference ->
            preference.toBuilder().setLora(config).build()
        }
    }

    private suspend fun setBluetoothConfig(config: ConfigProtos.Config.BluetoothConfig) {
        localConfigStore.updateData { preference ->
            preference.toBuilder().setBluetooth(config).build()
        }
    }

    suspend fun fetchInitialLocalConfig() = localConfigStore.data.first()

}
