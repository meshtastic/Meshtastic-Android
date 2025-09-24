/*
 * Copyright (c) 2025 Meshtastic LLC
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

package com.geeksville.mesh.repository.datastore

import com.geeksville.mesh.AppOnlyProtos.ChannelSet
import com.geeksville.mesh.ChannelProtos.Channel
import com.geeksville.mesh.ChannelProtos.ChannelSettings
import com.geeksville.mesh.ClientOnlyProtos.DeviceProfile
import com.geeksville.mesh.ConfigProtos.Config
import com.geeksville.mesh.IMeshService
import com.geeksville.mesh.LocalOnlyProtos.LocalConfig
import com.geeksville.mesh.LocalOnlyProtos.LocalModuleConfig
import com.geeksville.mesh.MeshProtos
import com.geeksville.mesh.MeshProtos.DeviceMetadata
import com.geeksville.mesh.MeshProtos.MeshPacket
import com.geeksville.mesh.ModuleConfigProtos.ModuleConfig
import com.geeksville.mesh.database.NodeRepository
import com.geeksville.mesh.deviceProfile
import com.geeksville.mesh.model.getChannelUrl
import com.geeksville.mesh.service.ConnectionState
import com.geeksville.mesh.service.ServiceAction
import com.geeksville.mesh.service.ServiceRepository
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import org.meshtastic.core.database.entity.MetadataEntity
import org.meshtastic.core.database.entity.MyNodeEntity
import org.meshtastic.core.database.entity.NodeEntity
import org.meshtastic.core.database.model.Node
import org.meshtastic.core.datastore.ChannelSetDataSource
import org.meshtastic.core.datastore.LocalConfigDataSource
import org.meshtastic.core.datastore.ModuleConfigDataSource
import javax.inject.Inject

/**
 * Class responsible for radio configuration data. Combines access to [nodeDB], [ChannelSet], [LocalConfig] &
 * [LocalModuleConfig].
 */
class RadioConfigRepository
@Inject
constructor(
    private val serviceRepository: ServiceRepository,
    private val nodeDB: NodeRepository,
    private val channelSetDataSource: ChannelSetDataSource,
    private val localConfigDataSource: LocalConfigDataSource,
    private val moduleConfigDataSource: ModuleConfigDataSource,
) {
    val meshService: IMeshService?
        get() = serviceRepository.meshService

    // Connection state to our radio device
    val connectionState
        get() = serviceRepository.connectionState

    fun setConnectionState(state: ConnectionState) = serviceRepository.setConnectionState(state)

    /** Flow representing the unique userId of our node. */
    val myId: StateFlow<String?>
        get() = nodeDB.myId

    /** Flow representing the [MyNodeEntity] database. */
    val myNodeInfo: StateFlow<MyNodeEntity?>
        get() = nodeDB.myNodeInfo

    /** Flow representing the [Node] database. */
    val nodeDBbyNum: StateFlow<Map<Int, Node>>
        get() = nodeDB.nodeDBbyNum

    fun getUser(nodeNum: Int) = nodeDB.getUser(nodeNum)

    suspend fun getNodeDBbyNum() = nodeDB.getNodeDBbyNum().first()

    suspend fun upsert(node: NodeEntity) = nodeDB.upsert(node)

    suspend fun installMyNodeInfo(mi: MyNodeEntity) {
        nodeDB.installMyNodeInfo(mi)
    }

    suspend fun installNodeDb(nodes: List<NodeEntity>) {
        nodeDB.installNodeDb(nodes)
    }

    suspend fun insertMetadata(fromNum: Int, metadata: DeviceMetadata) {
        nodeDB.insertMetadata(MetadataEntity(fromNum, metadata))
    }

    suspend fun clearNodeDB() {
        nodeDB.clearNodeDB()
    }

    /** Flow representing the [ChannelSet] data store. */
    val channelSetFlow: Flow<ChannelSet> = channelSetDataSource.channelSetFlow

    /** Clears the [ChannelSet] data in the data store. */
    suspend fun clearChannelSet() {
        channelSetDataSource.clearChannelSet()
    }

    /** Replaces the [ChannelSettings] list with a new [settingsList]. */
    suspend fun replaceAllSettings(settingsList: List<ChannelSettings>) {
        channelSetDataSource.clearSettings()
        channelSetDataSource.addAllSettings(settingsList)
    }

    /**
     * Updates the [ChannelSettings] list with the provided channel and returns the index of the admin channel after the
     * update (if not found, returns 0).
     *
     * @param channel The [Channel] provided.
     * @return the index of the admin channel after the update (if not found, returns 0).
     */
    suspend fun updateChannelSettings(channel: Channel) = channelSetDataSource.updateChannelSettings(channel)

    /** Flow representing the [LocalConfig] data store. */
    val localConfigFlow: Flow<LocalConfig> = localConfigDataSource.localConfigFlow

    /** Clears the [LocalConfig] data in the data store. */
    suspend fun clearLocalConfig() {
        localConfigDataSource.clearLocalConfig()
    }

    /**
     * Updates [LocalConfig] from each [Config] oneOf.
     *
     * @param config The [Config] to be set.
     */
    suspend fun setLocalConfig(config: Config) {
        localConfigDataSource.setLocalConfig(config)
        if (config.hasLora()) channelSetDataSource.setLoraConfig(config.lora)
    }

    /** Flow representing the [LocalModuleConfig] data store. */
    val moduleConfigFlow: Flow<LocalModuleConfig> = moduleConfigDataSource.moduleConfigFlow

    /** Clears the [LocalModuleConfig] data in the data store. */
    suspend fun clearLocalModuleConfig() {
        moduleConfigDataSource.clearLocalModuleConfig()
    }

    /**
     * Updates [LocalModuleConfig] from each [ModuleConfig] oneOf.
     *
     * @param config The [ModuleConfig] to be set.
     */
    suspend fun setLocalModuleConfig(config: ModuleConfig) {
        moduleConfigDataSource.setLocalModuleConfig(config)
    }

    /** Flow representing the combined [DeviceProfile] protobuf. */
    val deviceProfileFlow: Flow<DeviceProfile> =
        combine(nodeDB.ourNodeInfo, channelSetFlow, localConfigFlow, moduleConfigFlow) {
                node,
                channels,
                localConfig,
                localModuleConfig,
            ->
            deviceProfile {
                node?.user?.let {
                    longName = it.longName
                    shortName = it.shortName
                }
                channelUrl = channels.getChannelUrl().toString()
                config = localConfig
                moduleConfig = localModuleConfig
                if (node != null && localConfig.position.fixedPosition) {
                    fixedPosition = node.position
                }
            }
        }

    val clientNotification = serviceRepository.clientNotification

    fun setClientNotification(notification: MeshProtos.ClientNotification?) {
        serviceRepository.setClientNotification(notification)
    }

    fun clearClientNotification() {
        serviceRepository.clearClientNotification()
    }

    val errorMessage: StateFlow<String?>
        get() = serviceRepository.errorMessage

    fun setErrorMessage(text: String) {
        serviceRepository.setErrorMessage(text)
    }

    fun clearErrorMessage() {
        serviceRepository.clearErrorMessage()
    }

    fun setStatusMessage(text: String) {
        serviceRepository.setStatusMessage(text)
    }

    val meshPacketFlow: SharedFlow<MeshPacket>
        get() = serviceRepository.meshPacketFlow

    suspend fun emitMeshPacket(packet: MeshPacket) = coroutineScope { serviceRepository.emitMeshPacket(packet) }

    val serviceAction: Flow<ServiceAction>
        get() = serviceRepository.serviceAction

    suspend fun onServiceAction(action: ServiceAction) = coroutineScope { serviceRepository.onServiceAction(action) }

    val tracerouteResponse: StateFlow<String?>
        get() = serviceRepository.tracerouteResponse

    fun setTracerouteResponse(value: String?) {
        serviceRepository.setTracerouteResponse(value)
    }

    fun clearTracerouteResponse() {
        setTracerouteResponse(null)
    }
}
