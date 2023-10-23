package com.geeksville.mesh.repository.datastore

import androidx.datastore.core.DataStore
import com.geeksville.mesh.android.Logging
import com.geeksville.mesh.ConfigProtos.Config
import com.geeksville.mesh.LocalOnlyProtos.LocalConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import java.io.IOException
import javax.inject.Inject

/**
 * Class that handles saving and retrieving [LocalConfig] data.
 */
class LocalConfigRepository @Inject constructor(
    private val localConfigStore: DataStore<LocalConfig>,
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

    suspend fun clearLocalConfig() {
        localConfigStore.updateData { preference ->
            preference.toBuilder().clear().build()
        }
    }

    /**
     * Updates [LocalConfig] from each [Config] oneOf.
     */
    suspend fun setLocalConfig(config: Config) {
        if (config.hasDevice()) setDeviceConfig(config.device)
        if (config.hasPosition()) setPositionConfig(config.position)
        if (config.hasPower()) setPowerConfig(config.power)
        if (config.hasNetwork()) setWifiConfig(config.network)
        if (config.hasDisplay()) setDisplayConfig(config.display)
        if (config.hasLora()) setLoraConfig(config.lora)
        if (config.hasBluetooth()) setBluetoothConfig(config.bluetooth)
    }

    private suspend fun setDeviceConfig(config: Config.DeviceConfig) {
        localConfigStore.updateData { preference ->
            preference.toBuilder().setDevice(config).build()
        }
    }

    private suspend fun setPositionConfig(config: Config.PositionConfig) {
        localConfigStore.updateData { preference ->
            preference.toBuilder().setPosition(config).build()
        }
    }

    private suspend fun setPowerConfig(config: Config.PowerConfig) {
        localConfigStore.updateData { preference ->
            preference.toBuilder().setPower(config).build()
        }
    }

    private suspend fun setWifiConfig(config: Config.NetworkConfig) {
        localConfigStore.updateData { preference ->
            preference.toBuilder().setNetwork(config).build()
        }
    }

    private suspend fun setDisplayConfig(config: Config.DisplayConfig) {
        localConfigStore.updateData { preference ->
            preference.toBuilder().setDisplay(config).build()
        }
    }

    private suspend fun setLoraConfig(config: Config.LoRaConfig) {
        localConfigStore.updateData { preference ->
            preference.toBuilder().setLora(config).build()
        }
    }

    private suspend fun setBluetoothConfig(config: Config.BluetoothConfig) {
        localConfigStore.updateData { preference ->
            preference.toBuilder().setBluetooth(config).build()
        }
    }

    suspend fun fetchInitialLocalConfig() = localConfigStore.data.first()

}
