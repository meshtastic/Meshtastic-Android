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
package org.meshtastic.core.data.manager

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.koin.core.annotation.Named
import org.koin.core.annotation.Single
import org.meshtastic.core.common.util.handledLaunch
import org.meshtastic.core.repository.MeshConfigHandler
import org.meshtastic.core.repository.MeshConnectionManager
import org.meshtastic.core.repository.NodeManager
import org.meshtastic.core.repository.RadioConfigRepository
import org.meshtastic.core.repository.RadioInterfaceService
import org.meshtastic.core.repository.RadioSessionContext
import org.meshtastic.core.repository.ServiceStateWriter
import org.meshtastic.proto.Channel
import org.meshtastic.proto.Config
import org.meshtastic.proto.DeviceUIConfig
import org.meshtastic.proto.LoRaRegionPresetMap
import org.meshtastic.proto.LocalConfig
import org.meshtastic.proto.LocalModuleConfig
import org.meshtastic.proto.ModuleConfig

@Single
class MeshConfigHandlerImpl(
    private val radioConfigRepository: RadioConfigRepository,
    private val serviceStateWriter: ServiceStateWriter,
    private val nodeManager: NodeManager,
    private val connectionManager: Lazy<MeshConnectionManager>,
    private val radioInterfaceService: RadioInterfaceService,
    @Named("ServiceScope") private val scope: CoroutineScope,
) : MeshConfigHandler {

    private val _localConfig = MutableStateFlow(LocalConfig())
    override val localConfig = _localConfig.asStateFlow()

    private val _moduleConfig = MutableStateFlow(LocalModuleConfig())
    override val moduleConfig = _moduleConfig.asStateFlow()

    init {
        radioConfigRepository.localConfigFlow.onEach { _localConfig.value = it }.launchIn(scope)
        radioConfigRepository.moduleConfigFlow.onEach { _moduleConfig.value = it }.launchIn(scope)
    }

    private fun runForSession(session: RadioSessionContext, block: () -> Unit): Boolean =
        radioInterfaceService.runIfSessionActive(session, block)

    private suspend fun runWhileForSession(session: RadioSessionContext, block: suspend () -> Unit): Boolean =
        radioInterfaceService.runWhileSessionActive(session, block)

    override fun handleDeviceConfig(config: Config, session: RadioSessionContext): Boolean {
        val admitted =
            runForSession(session) {
                Logger.d { "Device config received: ${config.summarize()}" }
                serviceStateWriter.setConnectionProgress("Device config received")
                connectionManager.value.onHandshakeProgress()
            }
        if (admitted) {
            launchPersistenceForSession(session) { radioConfigRepository.setLocalConfig(config) }
        }
        if (!admitted) Logger.d { "Discarding device config from stale transport session" }
        return admitted
    }

    override fun handleModuleConfig(config: ModuleConfig, session: RadioSessionContext): Boolean {
        var statusUpdate: Pair<Int, String?>? = null
        val admitted =
            runForSession(session) {
                statusUpdate =
                    config.statusmessage?.let { status ->
                        nodeManager.myNodeNum.value?.let { nodeNum -> nodeNum to status.node_status }
                    }
                Logger.d { "Module config received: ${config.summarize()}" }
                serviceStateWriter.setConnectionProgress("Module config received")
                connectionManager.value.onHandshakeProgress()
            }
        if (admitted) {
            launchPersistenceForSession(session) {
                radioConfigRepository.setLocalModuleConfig(config)
                statusUpdate?.let { (nodeNum, status) ->
                    try {
                        nodeManager.updateNodeStatusAndPersist(nodeNum, status)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                        Logger.w(e) { "Node status persistence failed after module config was persisted" }
                    }
                }
            }
        }
        if (!admitted) Logger.d { "Discarding module config from stale transport session" }
        return admitted
    }

    override fun handleChannel(channel: Channel, session: RadioSessionContext): Boolean {
        val admitted =
            runForSession(session) {
                // Update status message if we have node info, otherwise use a generic one.
                val mi = nodeManager.getMyNodeInfo()
                val index = channel.index
                if (mi != null) {
                    serviceStateWriter.setConnectionProgress("Channels (${index + 1} / ${mi.maxChannels})")
                } else {
                    serviceStateWriter.setConnectionProgress("Channels (${index + 1})")
                }
                connectionManager.value.onHandshakeProgress()
            }
        if (admitted) {
            // We always want to save channel settings we receive from the radio.
            launchPersistenceForSession(session) { radioConfigRepository.updateChannelSettings(channel) }
        }
        if (!admitted) Logger.d { "Discarding channel config from stale transport session" }
        return admitted
    }

    override fun handleDeviceUIConfig(config: DeviceUIConfig, session: RadioSessionContext): Boolean {
        val admitted =
            runForSession(session) {
                Logger.d { "DeviceUI config received" }
                // deviceuiConfig arrives during Stage 1 immediately after my_info. It proves the transport
                // is alive, so surface it as handshake progress — without this, a long gap before the next
                // meaningful packet could falsely trip the fast-path watchdog on TCP/USB.
                connectionManager.value.onHandshakeProgress()
            }
        if (admitted) {
            launchPersistenceForSession(session) { radioConfigRepository.setDeviceUIConfig(config) }
        }
        if (!admitted) Logger.d { "Discarding DeviceUI config from stale transport session" }
        return admitted
    }

    override fun handleRegionPresets(map: LoRaRegionPresetMap, session: RadioSessionContext): Boolean {
        val admitted =
            runForSession(session) {
                Logger.d { "Region presets received (${map.region_groups.size} regions, ${map.groups.size} groups)" }
                connectionManager.value.onHandshakeProgress()
            }
        if (admitted) {
            launchPersistenceForSession(session) { radioConfigRepository.setLoraRegionPresetMap(map) }
        }
        if (!admitted) Logger.d { "Discarding region presets from stale transport session" }
        return admitted
    }

    /**
     * Queues handshake persistence on the serialized session-operation lane before the FIFO consumer admits the next
     * packet.
     */
    private fun launchPersistenceForSession(session: RadioSessionContext, block: suspend () -> Unit) {
        scope.handledLaunch(start = CoroutineStart.UNDISPATCHED) { runWhileForSession(session, block) }
    }
}

/** Returns a short summary of which Config variant is set. */
private fun Config.summarize(): String = when {
    device != null -> "device"
    position != null -> "position"
    power != null -> "power"
    network != null -> "network"
    display != null -> "display"
    lora != null -> "lora"
    bluetooth != null -> "bluetooth"
    security != null -> "security"
    else -> "unknown"
}

/** Returns a short summary of which ModuleConfig variant is set. */
@Suppress("CyclomaticComplexMethod")
private fun ModuleConfig.summarize(): String = when {
    mqtt != null -> "mqtt"
    serial != null -> "serial"
    external_notification != null -> "external_notification"
    store_forward != null -> "store_forward"
    range_test != null -> "range_test"
    telemetry != null -> "telemetry"
    canned_message != null -> "canned_message"
    audio != null -> "audio"
    remote_hardware != null -> "remote_hardware"
    neighbor_info != null -> "neighbor_info"
    ambient_lighting != null -> "ambient_lighting"
    detection_sensor != null -> "detection_sensor"
    paxcounter != null -> "paxcounter"
    statusmessage != null -> "statusmessage"
    tak != null -> "tak"
    else -> "unknown"
}
