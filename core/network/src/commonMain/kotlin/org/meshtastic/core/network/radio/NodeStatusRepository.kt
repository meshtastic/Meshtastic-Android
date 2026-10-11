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
package org.meshtastic.core.network.radio

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.koin.core.annotation.Single
import org.meshtastic.node.host.Bearer
import org.meshtastic.node.host.BearerFactory
import org.meshtastic.node.host.EmbeddedNode
import org.meshtastic.node.host.NodeStatus
import kotlin.concurrent.Volatile

/**
 * The in-process node as the UI sees it: null while no node transport runs, otherwise the facade's status model. The
 * host bearers are the switches a user flips on the node card; the config bearers go through the Network screen.
 */
interface NodeStatusRepository {
    val status: StateFlow<NodeStatus?>

    /** Switch a host bearer (Wi-Fi Aware, audio, video) on or off; persisted by the node. */
    suspend fun setHostBearer(bearer: Bearer, on: Boolean)

    /** Re-read permission and hardware state after a grant or an attach. */
    fun refreshRequirements()

    /** Ask the platform for the attached LoRa stick's USB permission. */
    fun requestUsbPermission()
}

/** Holds the live node the running [NodeRadioTransport] registers, and nothing while none runs. */
@Single(binds = [NodeStatusRepository::class, NodeStatusRepositoryImpl::class])
class NodeStatusRepositoryImpl : NodeStatusRepository {
    private class Live(val node: EmbeddedNode, val factory: BearerFactory)

    @Volatile private var live: Live? = null
    private val statusState = MutableStateFlow<NodeStatus?>(null)

    override val status: StateFlow<NodeStatus?> = statusState.asStateFlow()

    /** The transport calls this once its node exists; the returned job mirrors the node's status until cancelled. */
    internal fun attach(node: EmbeddedNode, factory: BearerFactory, scope: CoroutineScope): Job {
        live = Live(node, factory)
        return scope.launch { node.status.collect { statusState.value = it } }
    }

    /** The transport calls this on close. A stale call for a node that was already replaced changes nothing. */
    internal fun detach(node: EmbeddedNode) {
        if (live?.node === node) {
            live = null
            statusState.value = null
        }
    }

    override suspend fun setHostBearer(bearer: Bearer, on: Boolean) {
        live?.node?.hostBearers?.set(bearer, on)
    }

    override fun refreshRequirements() {
        live?.factory?.refreshRequirements()
    }

    override fun requestUsbPermission() {
        live?.factory?.requestUsbPermission()
    }
}
