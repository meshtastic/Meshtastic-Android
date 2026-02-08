/*
 * Copyright (c) 2025-2026 Meshtastic LLC
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
package com.geeksville.mesh.service

import co.touchlab.kermit.Logger
import com.geeksville.mesh.concurrent.handledLaunch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import org.meshtastic.core.analytics.platform.PlatformAnalytics
import org.meshtastic.core.data.repository.NodeRepository
import org.meshtastic.core.data.repository.RadioConfigRepository
import org.meshtastic.core.database.entity.MetadataEntity
import org.meshtastic.core.database.entity.MyNodeEntity
import org.meshtastic.core.service.ConnectionState
import org.meshtastic.proto.DeviceMetadata
import org.meshtastic.proto.HardwareModel
import org.meshtastic.proto.Heartbeat
import org.meshtastic.proto.MyNodeInfo
import org.meshtastic.proto.NodeInfo
import org.meshtastic.proto.ToRadio
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Suppress("LongParameterList")
@Singleton
class MeshConfigFlowManager
@Inject
constructor(
    private val nodeManager: MeshNodeManager,
    private val connectionManager: MeshConnectionManager,
    private val nodeRepository: NodeRepository,
    private val radioConfigRepository: RadioConfigRepository,
    private val connectionStateHolder: ConnectionStateHandler,
    private val serviceBroadcasts: MeshServiceBroadcasts,
    private val analytics: PlatformAnalytics,
    private val commandSender: MeshCommandSender,
    private val packetHandler: PacketHandler,
) {
    private var scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val configOnlyNonce = 69420
    private val nodeInfoNonce = 69421
    private val wantConfigDelay = 100L

    fun start(scope: CoroutineScope) {
        this.scope = scope
    }

    private val newNodes = mutableListOf<NodeInfo>()
    val newNodeCount: Int
        get() = newNodes.size

    private var rawMyNodeInfo: MyNodeInfo? = null
    private var newMyNodeInfo: MyNodeEntity? = null
    private var myNodeInfo: MyNodeEntity? = null

    fun handleConfigComplete(configCompleteId: Int) {
        when (configCompleteId) {
            configOnlyNonce -> handleConfigOnlyComplete()
            nodeInfoNonce -> handleNodeInfoComplete()
            else -> Logger.w { "Config complete id mismatch: $configCompleteId" }
        }
    }

    private fun handleConfigOnlyComplete() {
        Logger.i { "Config-only complete" }
        if (newMyNodeInfo == null) {
            Logger.e { "Did not receive a valid config - newMyNodeInfo is null" }
        } else {
            myNodeInfo = newMyNodeInfo
            Logger.i { "myNodeInfo committed successfully" }
            connectionManager.onRadioConfigLoaded()
        }

        scope.handledLaunch {
            delay(wantConfigDelay)
            sendHeartbeat()
            delay(wantConfigDelay)
            connectionManager.startNodeInfoOnly()
        }
    }

    private fun sendHeartbeat() {
        try {
            packetHandler.sendToRadio(ToRadio(heartbeat = Heartbeat()))
            Logger.d { "Heartbeat sent between nonce stages" }
        } catch (ex: IOException) {
            Logger.w(ex) { "Failed to send heartbeat; proceeding with node-info stage" }
        }
    }

    private fun handleNodeInfoComplete() {
        Logger.i { "NodeInfo complete" }
        val entities =
            newNodes.map { info ->
                nodeManager.installNodeInfo(info, withBroadcast = false)
                nodeManager.nodeDBbyNodeNum[info.num]!!
            }
        newNodes.clear()

        scope.handledLaunch {
            myNodeInfo?.let {
                nodeRepository.installConfig(it, entities)
                sendAnalytics(it)
            }
            nodeManager.isNodeDbReady.value = true
            nodeManager.allowNodeDbWrites.value = true
            connectionStateHolder.setState(ConnectionState.Connected)
            serviceBroadcasts.broadcastConnection()
            connectionManager.onNodeDbReady()
        }
    }

    private fun sendAnalytics(mi: MyNodeEntity) {
        analytics.setDeviceAttributes(mi.firmwareVersion ?: "unknown", mi.model ?: "unknown")
    }

    fun handleMyInfo(myInfo: MyNodeInfo) {
        Logger.i { "MyNodeInfo received: ${myInfo.my_node_num}" }
        rawMyNodeInfo = myInfo
        nodeManager.myNodeNum = myInfo.my_node_num ?: 0
        regenMyNodeInfo()

        scope.handledLaunch {
            radioConfigRepository.clearChannelSet()
            radioConfigRepository.clearLocalConfig()
            radioConfigRepository.clearLocalModuleConfig()
        }
    }

    fun handleLocalMetadata(metadata: DeviceMetadata) {
        Logger.i { "Local Metadata received" }
        regenMyNodeInfo(metadata)
    }

    fun handleNodeInfo(info: NodeInfo) {
        newNodes.add(info)
    }

    private fun regenMyNodeInfo(metadata: DeviceMetadata? = DeviceMetadata()) {
        val myInfo = rawMyNodeInfo
        if (myInfo != null) {
            val mi =
                with(myInfo) {
                    MyNodeEntity(
                        myNodeNum = my_node_num ?: 0,
                        model =
                        when (val hwModel = metadata?.hw_model) {
                            null,
                            HardwareModel.UNSET,
                            -> null
                            else -> hwModel.name.replace('_', '-').replace('p', '.').lowercase()
                        },
                        firmwareVersion = metadata?.firmware_version,
                        couldUpdate = false,
                        shouldUpdate = false,
                        currentPacketId = commandSender.getCurrentPacketId() and 0xffffffffL,
                        messageTimeoutMsec = 300000,
                        minAppVersion = min_app_version ?: 0,
                        maxChannels = 8,
                        hasWifi = metadata?.hasWifi == true,
                        deviceId = device_id?.utf8() ?: "",
                        pioEnv = if (myInfo.pio_env.isNullOrEmpty()) null else myInfo.pio_env,
                    )
                }
            if (metadata != null && metadata != DeviceMetadata()) {
                scope.handledLaunch { nodeRepository.insertMetadata(MetadataEntity(mi.myNodeNum, metadata)) }
            }
            newMyNodeInfo = mi
        }
    }
}
