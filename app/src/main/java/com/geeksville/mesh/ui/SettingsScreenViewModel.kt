package com.geeksville.mesh.ui

import android.app.Application
import android.os.RemoteException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.geeksville.mesh.ConfigProtos.Config
import com.geeksville.mesh.LocalOnlyProtos.LocalConfig
import com.geeksville.mesh.R
import com.geeksville.mesh.config
import com.geeksville.mesh.database.NodeRepository
import com.geeksville.mesh.model.RegionInfo
import com.geeksville.mesh.repository.datastore.RadioConfigRepository
import com.geeksville.mesh.repository.location.LocationRepository
import com.geeksville.mesh.service.MeshService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class Effect {
    object CheckForBluetoothPermission : Effect()
}

data class UIState(
    val userName: String,
    val regionDropDownExpanded: Boolean,
    val selectedRegion: RegionInfo,
    val localConfig: LocalConfig,
    val enableUsernameEdit: Boolean,
    val enableProvideLocation: Boolean,
    val errorText: String?,
    val isConnected: Boolean,
    val nodeFirmwareVersion: String?,
    val ipAddress: String,
)

@HiltViewModel
class SettingsScreenViewModel @Inject constructor(
    private val application: Application,
    private val radioConfigRepository: RadioConfigRepository,
    private val locationRepository: LocationRepository,
    private val nodeRepository: NodeRepository,
) : ViewModel() {

    private val _effectFlow = MutableSharedFlow<Effect>(replay = 0)
    val effectFlow: SharedFlow<Effect> = _effectFlow.asSharedFlow()

    private val _uiState = MutableStateFlow(
        UIState(
            userName = "",
            regionDropDownExpanded = false,
            selectedRegion = RegionInfo.UNSET,
            localConfig = LocalConfig.getDefaultInstance(),
            enableUsernameEdit = false,
            enableProvideLocation = false,
            errorText = null,
            isConnected = false,
            nodeFirmwareVersion = null,
            ipAddress = "",
            )
    )
    val uiState: StateFlow<UIState> = _uiState

    fun onUserNameChange(newName: String) {
        _uiState.value = _uiState.value.copy(userName = newName)
    }


    fun onToggleRegionDropDown() {
        viewModelScope.launch {
            _uiState.emit(
                _uiState.value.copy(regionDropDownExpanded = !_uiState.value.regionDropDownExpanded)
            )
        }
    }


    fun onRegionSelected(newRegion: RegionInfo) {
        viewModelScope.launch {
            _uiState.emit(_uiState.value.copy(selectedRegion = newRegion))
        }
        onToggleRegionDropDown()
        updateLoraConfig { it.toBuilder().setRegion(newRegion.regionCode).build() }
    }

    init {
        viewModelScope.launch {
            radioConfigRepository.connectionState.collect { connectionState ->
                // managed mode disables all access to configuration
                val isManaged =
                    _uiState.value.localConfig.let { it.device.isManaged || it.security.isManaged }
                val isConnected = connectionState.isConnected()
                _uiState.emit(
                    _uiState.value.copy(
                        isConnected = isConnected,
                        enableUsernameEdit = isConnected && !isManaged,
                    )
                )
                // Reset node values if not connected
                if (!isConnected) {
                    _uiState.emit(
                        _uiState.value.copy(
                            nodeFirmwareVersion = null,
                        )
                    )
                }
            }
        }

        viewModelScope.launch {
            nodeRepository.ourNodeInfo.collect { node ->
                _uiState.emit(
                    _uiState.value.copy(
                        userName = node?.user?.longName ?: _uiState.value.userName
                    )
                )
            }
        }

        viewModelScope.launch {
            nodeRepository.myNodeInfo.collect { node ->
                _uiState.emit(uiState.value.copy(nodeFirmwareVersion = node?.firmwareString))
            }
        }

        viewModelScope.launch {
            radioConfigRepository.localConfigFlow.collect {
                _uiState.emit(uiState.value.copy(
                    localConfig = it,
                    selectedRegion = uiState.value.localConfig.lora.region.let { region ->
                        RegionInfo.entries.firstOrNull { it.regionCode == region }
                            ?: RegionInfo.UNSET
                    }
                ))
            }
        }
    }

    /**
     * Pull the latest device info from the model and into the GUI
     */
    suspend fun updateNodeInfo() {
        // update the region selection from the device
        val region = uiState.value.localConfig.lora.region

        _uiState.emit(_uiState.value.copy(
            enableProvideLocation = locationRepository.hasGps(),
            selectedRegion =
            RegionInfo.entries.firstOrNull { it.regionCode == region } ?: RegionInfo.UNSET
        ))

        // Update the status string (highest priority messages first)
        val regionUnset = region == Config.LoRaConfig.RegionCode.UNSET
        val info = nodeRepository.myNodeInfo.value
        when (radioConfigRepository.connectionState.value) {
            MeshService.ConnectionState.CONNECTED ->
                if (regionUnset) R.string.must_set_region else R.string.connected_to

            MeshService.ConnectionState.DISCONNECTED -> R.string.not_connected
            MeshService.ConnectionState.DEVICE_SLEEP -> R.string.connected_sleeping
            else -> null
        }?.let {
            val errorText =
                info?.firmwareString ?: application.resources.getString(R.string.unknown)
            _uiState.emit(_uiState.value.copy(errorText = errorText))
        }

    }

    fun onIpAddressChange(newAddress: String) {
        _uiState.value = _uiState.value.copy(ipAddress = newAddress)
    }


    private inline fun updateLoraConfig(crossinline body: (Config.LoRaConfig) -> Config.LoRaConfig) {
        val data = body(uiState.value.localConfig.lora)
        setConfig(config { lora = data })
    }

    // Set the radio config (also updates our saved copy in preferences)
    private fun setConfig(config: Config) {
        try {
            radioConfigRepository.meshService?.setConfig(config.toByteArray())
        } catch (ex: RemoteException) {
            // TODO: Show error message to ui
        }
    }

    fun onAddRadioButtonClicked() {
        viewModelScope.launch {
            _effectFlow.emit(Effect.CheckForBluetoothPermission)
        }
    }

}


