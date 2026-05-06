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
package org.meshtastic.core.data.radio

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.meshtastic.proto.PortNum
import org.meshtastic.sdk.NeighborInfo

internal class SdkTopologyBridge(
    private val topologyService: MeshTopologyService,
) {
    fun observe(accessor: RadioClientAccessor, scope: CoroutineScope) {
        accessor.client
            .flatMapLatest { client -> client?.packets ?: emptyFlow() }
            .filter { it.decoded?.portnum == PortNum.NEIGHBORINFO_APP }
            .onEach { packet ->
                val payload = packet.decoded?.payload?.toByteArray() ?: return@onEach
                runCatching {
                    val proto = org.meshtastic.proto.NeighborInfo.ADAPTER.decode(payload)
                    val info = NeighborInfo.fromProto(
                        reportingNode = packet.from,
                        neighborNodeIds = proto.neighbors.map { it.node_id },
                        snrValues = proto.neighbors.map { it.snr },
                        timestamp = proto.last_sent_by_id,
                    )
                    topologyService.ingestNeighborInfo(info)
                }.onFailure { e -> Logger.w(e) { "[SdkBridge] Failed to parse NeighborInfo" } }
            }
            .launchIn(scope)
    }
}
