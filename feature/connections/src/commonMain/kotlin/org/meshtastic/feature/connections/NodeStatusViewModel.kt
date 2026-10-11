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
import kotlinx.coroutines.flow.map
import org.koin.core.annotation.KoinViewModel
import org.meshtastic.core.network.radio.NodeStatusRepository
import org.meshtastic.core.ui.viewmodel.safeLaunch
import org.meshtastic.core.ui.viewmodel.stateInWhileSubscribed
import org.meshtastic.feature.connections.model.NodeStatusUi
import org.meshtastic.feature.connections.model.toUi
import org.meshtastic.node.host.Bearer

/** The node status card's state: null while this device is not running as the node. */
@KoinViewModel
class NodeStatusViewModel(private val repository: NodeStatusRepository) : ViewModel() {

    val status: StateFlow<NodeStatusUi?> =
        repository.status.map { it?.toUi() }.stateInWhileSubscribed(initialValue = null)

    fun setHostBearer(bearer: Bearer, on: Boolean) {
        safeLaunch(tag = "setHostBearer") { repository.setHostBearer(bearer, on) }
    }

    fun refreshRequirements() = repository.refreshRequirements()

    fun requestUsbPermission() = repository.requestUsbPermission()
}
