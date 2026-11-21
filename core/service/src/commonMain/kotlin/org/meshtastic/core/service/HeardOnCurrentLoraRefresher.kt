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
package org.meshtastic.core.service

import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.meshtastic.core.common.state.RadioOperation
import org.meshtastic.core.common.state.RadioOperationLock
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.repository.MeshConfigFlowManager
import org.meshtastic.core.repository.NodeManager
import org.meshtastic.core.repository.RadioConfigRepository
import org.meshtastic.core.repository.ServiceRepository
import org.meshtastic.proto.Channel
import org.meshtastic.proto.Config
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Re-requests the node DB after a local write moves the LoRa slot: the region, modem settings, frequency and primary
 * channel name firmware fingerprints and compares each node's heard slot against. Firmware applies such a write without
 * a reboot and reports `heard_on_current_lora` only in a node DB dump, so without this the flags keep answering for the
 * old slot until the next connection.
 *
 * Only an acknowledged write counts, and only the newest acknowledged one requests. A reboot inside [REBOOT_WINDOW]
 * brings a fresh handshake instead, and a discovery scan restores the home settings with a rebooting commit, so nothing
 * is requested while one runs.
 */
internal class HeardOnCurrentLoraRefresher(
    private val nodeManager: NodeManager,
    private val meshConfigFlowManager: Lazy<MeshConfigFlowManager>,
    private val serviceRepository: ServiceRepository,
    private val radioConfigRepository: RadioConfigRepository,
    private val radioOperationLock: RadioOperationLock,
    private val scope: CoroutineScope,
) {
    private val latestAppliedChange = atomic(0L)

    /** True when [config], written to the local node, moves the LoRa slot; read before the write. */
    suspend fun movesSlot(config: Config): Boolean {
        val lora = config.lora ?: return false
        val current = radioConfigRepository.localConfigFlow.firstOrNull()?.lora
        return refreshes() && (current == null || lora.slot != current.slot)
    }

    /** True when [channel], written to the local node, renames the primary channel; read before the write. */
    suspend fun movesSlot(channel: Channel): Boolean {
        if (channel.role != Channel.Role.PRIMARY) return false
        val current = radioConfigRepository.channelSetFlow.firstOrNull()?.settings?.firstOrNull()?.name
        return refreshes() && (current == null || channel.settings?.name != current)
    }

    /** Requests the node DB once a slot-moving write is acknowledged, unless the node reboots or a newer one lands. */
    fun afterAcknowledgedWrite() {
        val change = latestAppliedChange.incrementAndGet()
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            val dropped =
                withTimeoutOrNull(REBOOT_WINDOW) {
                    serviceRepository.connectionState.first { it != ConnectionState.Connected }
                }
            if (dropped == null && latestAppliedChange.value == change) meshConfigFlowManager.value.refreshNodeInfo()
        }
    }

    private fun refreshes(): Boolean = nodeManager.reportsHeardOnCurrentLora.value &&
        RadioOperation.DiscoveryScan !in radioOperationLock.activeOperations

    private companion object {
        val REBOOT_WINDOW: Duration = 8.seconds
    }
}

/** The LoRa fields firmware's slot fingerprint covers; the unused half of preset or custom modem moves nothing. */
private val Config.LoRaConfig.slot: List<Any?>
    get() {
        val modem = if (use_preset) listOf(modem_preset) else listOf(bandwidth, spread_factor, coding_rate)
        return listOf(region, use_preset, override_frequency, channel_num) + modem
    }
