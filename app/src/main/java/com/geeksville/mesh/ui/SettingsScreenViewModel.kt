package com.geeksville.mesh.ui

import android.app.Application
import android.os.RemoteException
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsScreenViewModel @Inject constructor(
    private val application: Application,
    private val radioConfigRepository: RadioConfigRepository,
    private val locationRepository: LocationRepository,
    private val nodeRepository: NodeRepository,
) : ViewModel() {

    var userName by mutableStateOf("")
        private set

    fun onUserNameChange(newName: String) {
        userName = newName
    }

    var regionDropDownExpanded by mutableStateOf(false)
        private set

    fun onToggleRegionDropDown() {
        regionDropDownExpanded = !regionDropDownExpanded
    }

    var selectedRegion by mutableStateOf(RegionInfo.UNSET)
        private set

    fun onRegionSelected(newRegion: RegionInfo) {
        selectedRegion = newRegion
        onToggleRegionDropDown()
        updateLoraConfig { it.toBuilder().setRegion(newRegion.regionCode).build() }
    }


    var localConfig by mutableStateOf<LocalConfig>(LocalConfig.getDefaultInstance())
        private set
    var showNodeSettings by mutableStateOf(false)
        private set
    var showProvideLocation by mutableStateOf(false)
        private set
    var enableUsernameEdit by mutableStateOf(false)
        private set
    var enableProvideLocation by mutableStateOf(false)
        private set

    // managed mode disables all access to configuration
    val isManaged: Boolean get() = localConfig.device.isManaged || localConfig.security.isManaged

    var errorText by mutableStateOf(null as String?)
        private set

    var isConnected = false
        private set

    init {
        viewModelScope.launch {
            radioConfigRepository.connectionState.collect { connectionState ->
                isConnected = connectionState == MeshService.ConnectionState.CONNECTED
                showNodeSettings = isConnected
                showProvideLocation = isConnected
                enableUsernameEdit = isConnected && !isManaged
            }
        }

        viewModelScope.launch {
            nodeRepository.ourNodeInfo.collect { node ->
                userName = node?.user?.longName ?: userName
            }
        }

        viewModelScope.launch {
            radioConfigRepository.localConfigFlow.collect {
                localConfig = it
                selectedRegion = localConfig.lora.region.let { region ->
                    RegionInfo.entries.firstOrNull { it.regionCode == region } ?: RegionInfo.UNSET
                }
            }
        }
    }

    /**
     * Pull the latest device info from the model and into the GUI
     */
    suspend fun updateNodeInfo() {


        enableProvideLocation = locationRepository.hasGps()

        // update the region selection from the device
        val region = localConfig.lora.region

        selectedRegion =
            RegionInfo.entries.firstOrNull { it.regionCode == region } ?: RegionInfo.UNSET

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
            errorText = info?.firmwareString ?: application.resources.getString(R.string.unknown)
        }

    }

    var ipAddress by mutableStateOf("")
        private set

    fun onIpAddressChange(newAddress: String) {
        ipAddress = newAddress
    }


    private inline fun updateLoraConfig(crossinline body: (Config.LoRaConfig) -> Config.LoRaConfig) {
        val data = body(localConfig.lora)
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

}


