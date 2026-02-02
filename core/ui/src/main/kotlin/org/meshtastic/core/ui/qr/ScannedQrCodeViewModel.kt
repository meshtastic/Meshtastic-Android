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
package org.meshtastic.core.ui.qr

import android.os.RemoteException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import org.meshtastic.core.data.repository.RadioConfigRepository
import org.meshtastic.core.service.ServiceRepository
import org.meshtastic.core.ui.util.getChannelList
import org.meshtastic.core.ui.viewmodel.stateInWhileSubscribed
import org.meshtastic.proto.Channel
import org.meshtastic.proto.ChannelSet
import org.meshtastic.proto.Config
import org.meshtastic.proto.LocalConfig
import javax.inject.Inject

@HiltViewModel
class ScannedQrCodeViewModel
@Inject
constructor(
    private val radioConfigRepository: RadioConfigRepository,
    private val serviceRepository: ServiceRepository,
) : ViewModel() {

    val channels = radioConfigRepository.channelSetFlow.stateInWhileSubscribed(initialValue = ChannelSet())

    private val localConfig = radioConfigRepository.localConfigFlow.stateInWhileSubscribed(initialValue = LocalConfig())

    /** Set the radio config (also updates our saved copy in preferences). */
    fun setChannels(channelSet: ChannelSet) = viewModelScope.launch {
        getChannelList(channelSet.settings, channels.value.settings).forEach(::setChannel)
        radioConfigRepository.replaceAllSettings(channelSet.settings)

        val loraConfig = channelSet.lora_config
        if (loraConfig != null && localConfig.value.lora != loraConfig) {
            setConfig(Config(lora = loraConfig))
        }
    }

    private fun setChannel(channel: Channel) {
        try {
            serviceRepository.meshService?.setChannel(Channel.ADAPTER.encode(channel))
        } catch (ex: RemoteException) {
            Logger.e(ex) { "Set channel error" }
        }
    }

    // Set the radio config (also updates our saved copy in preferences)
    private fun setConfig(config: Config) {
        try {
            serviceRepository.meshService?.setConfig(Config.ADAPTER.encode(config))
        } catch (ex: RemoteException) {
            Logger.e(ex) { "Set config error" }
        }
    }
}
