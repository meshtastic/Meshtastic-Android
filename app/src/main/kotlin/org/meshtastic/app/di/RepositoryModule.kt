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
package org.meshtastic.app.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import org.meshtastic.core.data.manager.CommandSenderImpl
import org.meshtastic.core.data.manager.FromRadioPacketHandlerImpl
import org.meshtastic.core.data.manager.HistoryManagerImpl
import org.meshtastic.core.data.manager.MeshActionHandlerImpl
import org.meshtastic.core.data.manager.MeshConfigFlowManagerImpl
import org.meshtastic.core.data.manager.MeshConfigHandlerImpl
import org.meshtastic.core.data.manager.MeshConnectionManagerImpl
import org.meshtastic.core.data.manager.MeshDataHandlerImpl
import org.meshtastic.core.data.manager.MeshMessageProcessorImpl
import org.meshtastic.core.data.manager.MeshRouterImpl
import org.meshtastic.core.data.manager.MessageFilterImpl
import org.meshtastic.core.data.manager.MqttManagerImpl
import org.meshtastic.core.data.manager.NeighborInfoHandlerImpl
import org.meshtastic.core.data.manager.NodeManagerImpl
import org.meshtastic.core.data.manager.PacketHandlerImpl
import org.meshtastic.core.data.manager.TracerouteHandlerImpl
import org.meshtastic.core.data.repository.DeviceHardwareRepositoryImpl
import org.meshtastic.core.data.repository.LocationRepositoryImpl
import org.meshtastic.core.data.repository.MeshLogRepositoryImpl
import org.meshtastic.core.data.repository.NodeRepositoryImpl
import org.meshtastic.core.data.repository.PacketRepositoryImpl
import org.meshtastic.core.data.repository.RadioConfigRepositoryImpl
import org.meshtastic.core.model.util.MeshDataMapper
import org.meshtastic.core.repository.CommandSender
import org.meshtastic.core.repository.DeviceHardwareRepository
import org.meshtastic.core.repository.FromRadioPacketHandler
import org.meshtastic.core.repository.HistoryManager
import org.meshtastic.core.repository.LocationRepository
import org.meshtastic.core.repository.MeshActionHandler
import org.meshtastic.core.repository.MeshConfigFlowManager
import org.meshtastic.core.repository.MeshConfigHandler
import org.meshtastic.core.repository.MeshConnectionManager
import org.meshtastic.core.repository.MeshDataHandler
import org.meshtastic.core.repository.MeshLogRepository
import org.meshtastic.core.repository.MeshMessageProcessor
import org.meshtastic.core.repository.MeshRouter
import org.meshtastic.core.repository.MessageFilter
import org.meshtastic.core.repository.MqttManager
import org.meshtastic.core.repository.NeighborInfoHandler
import org.meshtastic.core.repository.NodeManager
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.core.repository.PacketHandler
import org.meshtastic.core.repository.PacketRepository
import org.meshtastic.core.repository.RadioConfigRepository
import org.meshtastic.core.repository.TracerouteHandler
import javax.inject.Singleton

@Suppress("TooManyFunctions")
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds @Singleton
    abstract fun bindNodeRepository(nodeRepositoryImpl: NodeRepositoryImpl): NodeRepository

    @Binds
    @Singleton
    abstract fun bindRadioConfigRepository(radioConfigRepositoryImpl: RadioConfigRepositoryImpl): RadioConfigRepository

    @Binds
    @Singleton
    abstract fun bindLocationRepository(locationRepositoryImpl: LocationRepositoryImpl): LocationRepository

    @Binds
    @Singleton
    abstract fun bindDeviceHardwareRepository(
        deviceHardwareRepositoryImpl: DeviceHardwareRepositoryImpl,
    ): DeviceHardwareRepository

    @Binds @Singleton
    abstract fun bindPacketRepository(packetRepositoryImpl: PacketRepositoryImpl): PacketRepository

    @Binds
    @Singleton
    abstract fun bindMeshLogRepository(meshLogRepositoryImpl: MeshLogRepositoryImpl): MeshLogRepository

    @Binds @Singleton
    abstract fun bindNodeManager(nodeManagerImpl: NodeManagerImpl): NodeManager

    @Binds @Singleton
    abstract fun bindCommandSender(commandSenderImpl: CommandSenderImpl): CommandSender

    @Binds @Singleton
    abstract fun bindHistoryManager(historyManagerImpl: HistoryManagerImpl): HistoryManager

    @Binds
    @Singleton
    abstract fun bindTracerouteHandler(tracerouteHandlerImpl: TracerouteHandlerImpl): TracerouteHandler

    @Binds
    @Singleton
    abstract fun bindNeighborInfoHandler(neighborInfoHandlerImpl: NeighborInfoHandlerImpl): NeighborInfoHandler

    @Binds @Singleton
    abstract fun bindMqttManager(mqttManagerImpl: MqttManagerImpl): MqttManager

    @Binds @Singleton
    abstract fun bindPacketHandler(packetHandlerImpl: PacketHandlerImpl): PacketHandler

    @Binds
    @Singleton
    abstract fun bindMeshConnectionManager(meshConnectionManagerImpl: MeshConnectionManagerImpl): MeshConnectionManager

    @Binds @Singleton
    abstract fun bindMeshDataHandler(meshDataHandlerImpl: MeshDataHandlerImpl): MeshDataHandler

    @Binds
    @Singleton
    abstract fun bindMeshActionHandler(meshActionHandlerImpl: MeshActionHandlerImpl): MeshActionHandler

    @Binds
    @Singleton
    abstract fun bindMeshMessageProcessor(meshMessageProcessorImpl: MeshMessageProcessorImpl): MeshMessageProcessor

    @Binds @Singleton
    abstract fun bindMeshRouter(meshRouterImpl: MeshRouterImpl): MeshRouter

    @Binds
    @Singleton
    abstract fun bindFromRadioPacketHandler(
        fromRadioPacketHandlerImpl: FromRadioPacketHandlerImpl,
    ): FromRadioPacketHandler

    @Binds
    @Singleton
    abstract fun bindMeshConfigHandler(meshConfigHandlerImpl: MeshConfigHandlerImpl): MeshConfigHandler

    @Binds
    @Singleton
    abstract fun bindMeshConfigFlowManager(meshConfigFlowManagerImpl: MeshConfigFlowManagerImpl): MeshConfigFlowManager

    @Binds @Singleton
    abstract fun bindMessageFilter(messageFilterImpl: MessageFilterImpl): MessageFilter

    companion object {
        @Provides
        @Singleton
        fun provideMeshDataMapper(nodeManager: NodeManager): MeshDataMapper = MeshDataMapper(nodeManager)
    }
}
