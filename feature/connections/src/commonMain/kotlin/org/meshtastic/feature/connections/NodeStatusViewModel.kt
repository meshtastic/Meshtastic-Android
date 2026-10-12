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
package org.meshtastic.feature.connections

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.koin.core.annotation.KoinViewModel
import org.meshtastic.core.network.radio.NodeStatusRepository
import org.meshtastic.core.repository.RadioConfigRepository
import org.meshtastic.core.repository.RadioController
import org.meshtastic.core.ui.viewmodel.safeLaunch
import org.meshtastic.core.ui.viewmodel.stateInWhileSubscribed
import org.meshtastic.feature.connections.model.NodeStatusUi
import org.meshtastic.feature.connections.model.protocolFlag
import org.meshtastic.feature.connections.model.toUi
import org.meshtastic.feature.connections.model.withProtocol
import org.meshtastic.node.host.Bearer
import org.meshtastic.proto.Config

/** The node status card's state: null while this device is not running as the node. */
@KoinViewModel
class NodeStatusViewModel(
    private val repository: NodeStatusRepository,
    private val radioController: RadioController,
    private val radioConfigRepository: RadioConfigRepository,
) : ViewModel() {

    val status: StateFlow<NodeStatusUi?> =
        repository.status.map { it?.toUi() }.stateInWhileSubscribed(initialValue = null)

    private val networkWrites = Mutex()

    /** The cached section the last write started from, and what it wrote. */
    private var lastWrite: Pair<Config.NetworkConfig, Config.NetworkConfig>? = null

    /**
     * A host bearer is persisted by the node. A config bearer flips its `enabled_protocols` bit through the same
     * local `set_config` the Network screen sends, so the screen and the card show one state.
     */
    fun setBearer(bearer: Bearer, on: Boolean) {
        safeLaunch(tag = "setBearer") {
            if (!bearer.fromConfig) {
                repository.setHostBearer(bearer, on)
                return@safeLaunch
            }
            val flag = bearer.protocolFlag ?: return@safeLaunch
            networkWrites.withLock {
                val cached = radioConfigRepository.localConfigFlow.first().network ?: Config.NetworkConfig.Builder().build()
                // The cache is persisted after the send, so a second switch can read it before the first lands.
                val base = lastWrite?.takeIf { (seen, _) -> seen == cached }?.second ?: cached
                val next = base.withProtocol(flag, on)
                radioController.setLocalConfig(Config.Builder().also { wb -> wb.network = next }.build())
                lastWrite = cached to next
            }
        }
    }

    fun refreshRequirements() = repository.refreshRequirements()

    fun requestUsbPermission() = repository.requestUsbPermission()
}
