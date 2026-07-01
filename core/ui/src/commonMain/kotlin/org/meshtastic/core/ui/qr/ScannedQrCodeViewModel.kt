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
import kotlinx.coroutines.flow.map
import org.koin.core.annotation.KoinViewModel
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.core.repository.RadioConfigRepository
import org.meshtastic.core.repository.RadioController
import org.meshtastic.core.ui.util.applyReplacementChannelSet
import org.meshtastic.core.ui.viewmodel.safeLaunch
import org.meshtastic.core.ui.viewmodel.stateInWhileSubscribed
import org.meshtastic.proto.ChannelSet
import org.meshtastic.proto.Config
import org.meshtastic.proto.LocalConfig

internal const val DEFAULT_MAX_CHANNELS = 8

@KoinViewModel
class ScannedQrCodeViewModel(
    private val radioConfigRepository: RadioConfigRepository,
    private val radioController: RadioController,
    nodeRepository: NodeRepository,
) : ViewModel() {

    val channels = radioConfigRepository.channelSetFlow.stateInWhileSubscribed(initialValue = ChannelSet())

    val maxChannels =
        nodeRepository.myNodeInfo
            .map { it?.maxChannels?.takeIf { max -> max > 0 } ?: DEFAULT_MAX_CHANNELS }
            .stateInWhileSubscribed(initialValue = DEFAULT_MAX_CHANNELS)

    private val localConfig = radioConfigRepository.localConfigFlow.stateInWhileSubscribed(initialValue = LocalConfig())

    /** Set the radio config (also updates our saved copy in preferences). */
    fun setChannels(channelSet: ChannelSet) = safeLaunch(tag = "setChannels") {
        applyReplacementChannelSet(channelSet, radioController, radioConfigRepository)

        val loraConfig = channelSet.lora_config
        if (loraConfig != null && localConfig.value.lora != loraConfig) {
            setConfig(Config(lora = loraConfig))
        }
    }

    // Set the radio config (also updates our saved copy in preferences)
    private fun setConfig(config: Config) {
        safeLaunch(tag = "setConfig") { radioController.setLocalConfig(config) }
    }
}
