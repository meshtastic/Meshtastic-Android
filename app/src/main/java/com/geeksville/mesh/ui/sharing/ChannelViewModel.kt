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
package com.geeksville.mesh.ui.sharing

import android.net.Uri
import android.os.RemoteException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.meshtastic.core.analytics.DataPair
import org.meshtastic.core.analytics.platform.PlatformAnalytics
import org.meshtastic.core.data.repository.RadioConfigRepository
import org.meshtastic.core.model.util.toChannelSet
import org.meshtastic.core.service.ServiceRepository
import org.meshtastic.core.ui.util.getChannelList
import org.meshtastic.core.ui.viewmodel.stateInWhileSubscribed
import org.meshtastic.proto.Channel
import org.meshtastic.proto.ChannelSet
import org.meshtastic.proto.Config
import org.meshtastic.proto.LocalConfig
import javax.inject.Inject

@HiltViewModel
class ChannelViewModel
@Inject
constructor(
    private val serviceRepository: ServiceRepository,
    private val radioConfigRepository: RadioConfigRepository,
    private val analytics: PlatformAnalytics,
) : ViewModel() {

    val connectionState = serviceRepository.connectionState

    val localConfig = radioConfigRepository.localConfigFlow.stateInWhileSubscribed(initialValue = LocalConfig())

    val channels = radioConfigRepository.channelSetFlow.stateInWhileSubscribed(initialValue = ChannelSet())

    // managed mode disables all access to configuration
    val isManaged: Boolean
        get() = localConfig.value.security?.is_managed == true

    var txEnabled: Boolean
        get() = localConfig.value.lora?.tx_enabled == true
        set(value) {
            updateLoraConfig { it.copy(tx_enabled = value) }
        }

    var region: Config.LoRaConfig.RegionCode
        get() = localConfig.value.lora?.region ?: Config.LoRaConfig.RegionCode.UNSET
        set(value) {
            updateLoraConfig { it.copy(region = value) }
        }

    private val _requestChannelSet = MutableStateFlow<ChannelSet?>(null)
    val requestChannelSet: StateFlow<ChannelSet?>
        get() = _requestChannelSet

    fun requestChannelUrl(url: Uri, onError: () -> Unit) = runCatching { _requestChannelSet.value = url.toChannelSet() }
        .onFailure { ex ->
            Logger.e(ex) { "Channel url error" }
            onError()
        }

    fun clearRequestChannelUrl() {
        _requestChannelSet.value = null
    }

    /** Set the radio config (also updates our saved copy in preferences). */
    fun setChannels(channelSet: ChannelSet) = viewModelScope.launch {
        getChannelList(channelSet.settings, channels.value.settings).forEach(::setChannel)
        radioConfigRepository.replaceAllSettings(channelSet.settings)

        val newLoraConfig = channelSet.lora_config
        if (localConfig.value.lora != newLoraConfig) {
            setConfig(Config(lora = newLoraConfig))
        }
    }

    fun setChannel(channel: Channel) {
        try {
            serviceRepository.meshService?.setChannel(channel.encode())
        } catch (ex: RemoteException) {
            Logger.e(ex) { "Set channel error" }
        }
    }

    // Set the radio config (also updates our saved copy in preferences)
    fun setConfig(config: Config) {
        try {
            serviceRepository.meshService?.setConfig(config.encode())
        } catch (ex: RemoteException) {
            Logger.e(ex) { "Set config error" }
        }
    }

    fun trackShare() {
        analytics.track("share", DataPair("content_type", "channel"))
    }

    private inline fun updateLoraConfig(crossinline body: (Config.LoRaConfig) -> Config.LoRaConfig) {
        val data = body(localConfig.value.lora ?: Config.LoRaConfig())
        setConfig(Config(lora = data))
    }
}
