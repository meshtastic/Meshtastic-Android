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

package com.geeksville.mesh.ui.common.components

import android.os.RemoteException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.meshtastic.core.data.repository.RadioConfigRepository
import org.meshtastic.core.proto.getChannelList
import org.meshtastic.core.service.ServiceRepository
import org.meshtastic.proto.AppOnlyProtos
import org.meshtastic.proto.ChannelProtos
import org.meshtastic.proto.ConfigProtos.Config
import org.meshtastic.proto.LocalOnlyProtos.LocalConfig
import org.meshtastic.proto.channelSet
import org.meshtastic.proto.config
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class ScannedQrCodeViewModel
@Inject
constructor(
    private val radioConfigRepository: RadioConfigRepository,
    private val serviceRepository: ServiceRepository,
) : ViewModel() {

    val channels =
        radioConfigRepository.channelSetFlow.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000L),
            channelSet {},
        )

    private val localConfig =
        radioConfigRepository.localConfigFlow.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000L),
            LocalConfig.getDefaultInstance(),
        )

    /** Set the radio config (also updates our saved copy in preferences). */
    fun setChannels(channelSet: AppOnlyProtos.ChannelSet) = viewModelScope.launch {
        getChannelList(channelSet.settingsList, channels.value.settingsList).forEach(::setChannel)
        radioConfigRepository.replaceAllSettings(channelSet.settingsList)

        val newConfig = config { lora = channelSet.loraConfig }
        if (localConfig.value.lora != newConfig.lora) setConfig(newConfig)
    }

    private fun setChannel(channel: ChannelProtos.Channel) {
        try {
            serviceRepository.meshService?.setChannel(channel.toByteArray())
        } catch (ex: RemoteException) {
            Timber.e(ex, "Set channel error")
        }
    }

    // Set the radio config (also updates our saved copy in preferences)
    private fun setConfig(config: Config) {
        try {
            serviceRepository.meshService?.setConfig(config.toByteArray())
        } catch (ex: RemoteException) {
            Timber.e(ex, "Set config error")
        }
    }
}
