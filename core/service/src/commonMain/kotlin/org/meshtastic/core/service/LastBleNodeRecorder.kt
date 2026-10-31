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

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.koin.core.annotation.Single
import org.meshtastic.core.common.util.safeCatching
import org.meshtastic.core.datastore.FirmwareRecoveryDataSource
import org.meshtastic.core.datastore.model.PendingFirmwareRecovery
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.model.DeviceType
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.core.repository.RadioPrefs
import org.meshtastic.core.repository.ServiceRepository
import org.meshtastic.proto.HardwareModel

/**
 * Keeps [FirmwareRecoveryDataSource.lastBleNode] pointing at the node of the last completed BLE handshake, so the
 * Connections screen can offer to re-flash it if it later turns up stranded in its bootloader.
 */
fun interface LastBleNodeRecorder {
    /** Records for as long as [scope] lives. */
    fun start(scope: CoroutineScope)
}

/**
 * Every BLE node is recorded, ESP32 included: only an nRF52 bootloader advertises the legacy DFU service the scan looks
 * for, so other architectures never match.
 */
@Single
class DefaultLastBleNodeRecorder(
    private val serviceRepository: ServiceRepository,
    private val nodeRepository: NodeRepository,
    private val radioPrefs: RadioPrefs,
    private val firmwareRecoveryDataSource: FirmwareRecoveryDataSource,
) : LastBleNodeRecorder {
    override fun start(scope: CoroutineScope) {
        combine(
            serviceRepository.connectionState,
            radioPrefs.devAddr,
            nodeRepository.myNodeInfo,
            nodeRepository.ourNodeInfo,
        ) { state, address, myNode, ourNode ->
            // Connected is reached only after the handshake, so the node records describe this address's node.
            val bleAddress = address?.takeIf { state is ConnectionState.Connected && it.isBle() }
            val hwModel = ourNode?.user?.hw_model?.takeIf { it != HardwareModel.UNSET }
            val pioEnv = myNode?.pioEnv?.takeIf { it.isNotEmpty() }
            if (bleAddress == null || hwModel == null || pioEnv == null) {
                null
            } else {
                PendingFirmwareRecovery(
                    fullAddress = bleAddress,
                    hwModel = hwModel.value,
                    pioEnv = pioEnv,
                    releaseType = STABLE_RELEASE,
                    deviceName = ourNode.user.long_name,
                )
            }
        }
            // Deduplicate before dropping nulls, so a reconnect to the same node writes it again after a dismiss.
            .distinctUntilChanged()
            .filterNotNull()
            .onEach { node ->
                safeCatching { firmwareRecoveryDataSource.setLastBleNode(node) }
                    .onFailure { Logger.w { "Could not record the last BLE node (${it::class.simpleName})" } }
            }
            .launchIn(scope)
    }

    private fun String.isBle() = DeviceType.fromAddress(this) == DeviceType.BLE

    private companion object {
        const val STABLE_RELEASE = "STABLE"
    }
}
