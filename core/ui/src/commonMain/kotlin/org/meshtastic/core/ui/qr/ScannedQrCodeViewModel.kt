/*
 * Copyright (c) 2026 Meshtastic LLC
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

import androidx.lifecycle.ViewModel
import org.koin.core.annotation.KoinViewModel
import org.meshtastic.core.model.RadioController
import org.meshtastic.core.repository.RadioConfigRepository
import org.meshtastic.core.ui.util.getChannelList
import org.meshtastic.core.ui.viewmodel.safeLaunch
import org.meshtastic.core.ui.viewmodel.stateInWhileSubscribed
import org.meshtastic.proto.Channel
import org.meshtastic.proto.ChannelSet
import org.meshtastic.proto.Config
import org.meshtastic.proto.LocalConfig

@KoinViewModel
class ScannedQrCodeViewModel(
    private val radioConfigRepository: RadioConfigRepository,
    private val radioController: RadioController,
) : ViewModel() {

    val channels = radioConfigRepository.channelSetFlow.stateInWhileSubscribed(initialValue = ChannelSet())

    private val localConfig = radioConfigRepository.localConfigFlow.stateInWhileSubscribed(initialValue = LocalConfig())

    /** Set the radio config (also updates our saved copy in preferences). */
    fun setChannels(channelSet: ChannelSet) = safeLaunch(tag = "setChannels") {
        getChannelList(channelSet.settings, channels.value.settings).forEach(::setChannel)
        radioConfigRepository.replaceAllSettings(channelSet.settings)

        val loraConfig = channelSet.lora_config
        if (loraConfig != null && localConfig.value.lora != loraConfig) {
            setConfig(Config(lora = loraConfig))
        }
    }

    private fun setChannel(channel: Channel) {
        safeLaunch(tag = "setChannel") { radioController.setLocalChannel(channel) }
    }

    // Set the radio config (also updates our saved copy in preferences)
    private fun setConfig(config: Config) {
        safeLaunch(tag = "setConfig") { radioController.setLocalConfig(config) }
    }
}
