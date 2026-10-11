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

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import org.meshtastic.core.repository.RadioTransport
import org.meshtastic.core.repository.RadioTransportCallback
import org.meshtastic.node.MeshLogLevel
import org.meshtastic.node.MeshLogger
import org.meshtastic.node.host.BearerFactory
import org.meshtastic.node.host.EmbeddedNode
import org.meshtastic.node.host.NodePhase
import org.meshtastic.node.host.NodeStatus
import org.meshtastic.node.host.ProtocolFlagBits
import org.meshtastic.node.phoneapi.nodeDefaults
import org.meshtastic.node.store.file.nodeFileStores
import org.meshtastic.proto.BackupPreferences
import org.meshtastic.proto.HardwareModel

/**
 * This device as the radio: an in-process node-kmp node ([EmbeddedNode]) serving the app's own phone API.
 *
 * ToRadio bytes queue into a bounded channel drained by one coroutine, the single writer the session needs. FromRadio
 * bytes flow back under the lifecycle gate. The facade's phases map onto the callback: Running connects once, Rebooting
 * (a LoRa retune, a rename, a license change, a reset) is a non-permanent disconnect the app treats as a reboot, Failed
 * and a shutdown verb are permanent.
 */
class NodeRadioTransport(
    private val callback: RadioTransportCallback,
    private val scope: CoroutineScope,
    private val bearers: NodeBearers,
    private val statusRepository: NodeStatusRepositoryImpl,
    private val isDebug: Boolean,
    private val newNode: (CoroutineScope, NodeBearers, BearerFactory, MeshLogger) -> EmbeddedNode = ::embeddedNode,
) : RadioTransport {
    private val lifecycle = TransportLifecycleGate("Node")
    private val transportJob = SupervisorJob(scope.coroutineContext[Job])
    private val transportScope = CoroutineScope(scope.coroutineContext + transportJob)
    private val inbound = Channel<ByteArray>(capacity = TO_RADIO_BUFFER)
    private var factory: BearerFactory? = null
    private var node: EmbeddedNode? = null
    private var connected = false
    private var closing = false

    override fun start() {
        lifecycle.runIfOpen {
            val built = bearers.factory(bearers.hostSignals).also { factory = it }
            val live = newNode(transportScope, bearers, built, KermitMeshLogger(isDebug)).also { node = it }
            statusRepository.attach(live, built, transportScope)
            transportScope.launch { live.status.collect(::onPhase) }
            transportScope.launch {
                live.phoneApi.fromRadio.collect { bytes -> lifecycle.runIfOpen { callback.handleFromRadio(bytes) } }
            }
            transportScope.launch { for (bytes in inbound) live.phoneApi.toRadio(bytes) }
        }
    }

    private fun onPhase(status: NodeStatus) {
        when (status.phase) {
            NodePhase.Running ->
                if (!connected) {
                    connected = true
                    callback.onConnect()
                }

            NodePhase.Rebooting ->
                if (connected) {
                    connected = false
                    callback.onDisconnect(isPermanent = false)
                }

            NodePhase.Failed -> {
                connected = false
                callback.onDisconnect(isPermanent = true, errorMessage = status.failure)
            }

            // A shutdown verb from the admin screen; the service's own close reports its disconnect itself.
            NodePhase.Stopped ->
                if (connected && !closing) {
                    connected = false
                    callback.onDisconnect(isPermanent = true)
                }

            NodePhase.Starting -> Unit
        }
    }

    /** False when the inbound buffer is full: the caller treats it as back-pressure, as the contract says. */
    override fun handleSendToRadio(p: ByteArray): Boolean =
        lifecycle.runIfOpen { inbound.trySend(p).isSuccess } ?: false

    override suspend fun close() {
        closing = true
        val completed =
            lifecycle.close(
                beforeDrain = { inbound.close() },
                teardown = {
                    node?.let { live ->
                        statusRepository.detach(live)
                        live.close()
                    }
                    node = null
                    factory?.close()
                    factory = null
                    transportJob.cancelAndJoin()
                },
            )
        if (!completed) Logger.w { "Node transport teardown did not complete within its lifecycle bounds" }
    }

    private companion object {
        const val TO_RADIO_BUFFER = 64
    }
}

/** The node every platform host constructs the same way; the platform contributes only [NodeBearers]. */
internal fun embeddedNode(
    scope: CoroutineScope,
    bearers: NodeBearers,
    factory: BearerFactory,
    logger: MeshLogger,
): EmbeddedNode = EmbeddedNode(
    scope = scope,
    stores = nodeFileStores(bearers.storesDir()),
    bearers = factory,
    signals = bearers.hostSignals,
    hostBearerStore = bearers.hostBearerStore(),
    defaults = firstRunDefaults(),
    logger = logger,
    positionSource = bearers.positionSource,
    hardwareModel = HardwareModel.NODE_KMP,
)

/**
 * A fresh install starts with the three bearers that need no hardware on: UDP, BLE advertisements and the GATT peer.
 * Firmware ships with `enabled_protocols` at 0, but a phone node with every bearer off is deaf, so the host seeds it.
 */
internal fun firstRunDefaults(): BackupPreferences = nodeDefaults {
    network {
        newBuilder()
            .enabled_protocols(
                ProtocolFlagBits.UDP_BROADCAST or ProtocolFlagBits.BLE_BROADCAST or ProtocolFlagBits.BLE_GATT_PEER,
            )
            .build()
    }
}

/** The library's diagnostics onto the app's logger; its per-frame debug lines only in a debug build. */
internal class KermitMeshLogger(private val debug: Boolean) : MeshLogger {
    override fun log(level: MeshLogLevel, tag: String, message: String) {
        val logger = Logger.withTag(tag)
        when (level) {
            MeshLogLevel.DEBUG -> if (debug) logger.d { message }
            MeshLogLevel.INFO -> logger.i { message }
            MeshLogLevel.WARN -> logger.w { message }
            MeshLogLevel.ERROR -> logger.e { message }
            MeshLogLevel.OFF -> Unit
        }
    }
}
