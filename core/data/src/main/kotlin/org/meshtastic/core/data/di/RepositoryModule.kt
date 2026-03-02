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

package org.meshtastic.core.data.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import org.meshtastic.core.data.manager.CommandSenderImpl
import org.meshtastic.core.data.manager.HistoryManagerImpl
import org.meshtastic.core.data.manager.MqttManagerImpl
import org.meshtastic.core.data.manager.NeighborInfoHandlerImpl
import org.meshtastic.core.data.manager.NodeManagerImpl
import org.meshtastic.core.data.manager.TracerouteHandlerImpl
import org.meshtastic.core.data.repository.DeviceHardwareRepositoryImpl
import org.meshtastic.core.data.repository.NodeRepositoryImpl
import org.meshtastic.core.data.repository.PacketRepositoryImpl
import org.meshtastic.core.data.repository.RadioConfigRepositoryImpl
import org.meshtastic.core.model.util.MeshDataMapper
import org.meshtastic.core.repository.CommandSender
import org.meshtastic.core.repository.DeviceHardwareRepository
import org.meshtastic.core.repository.HistoryManager
import org.meshtastic.core.repository.MqttManager
import org.meshtastic.core.repository.NeighborInfoHandler
import org.meshtastic.core.repository.NodeManager
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.core.repository.PacketRepository
import org.meshtastic.core.repository.RadioConfigRepository
import org.meshtastic.core.repository.TracerouteHandler
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindNodeRepository(
        nodeRepositoryImpl: NodeRepositoryImpl
    ): NodeRepository

    @Binds
    @Singleton
    abstract fun bindRadioConfigRepository(
        radioConfigRepositoryImpl: RadioConfigRepositoryImpl
    ): RadioConfigRepository

    @Binds
    @Singleton
    abstract fun bindDeviceHardwareRepository(
        deviceHardwareRepositoryImpl: DeviceHardwareRepositoryImpl
    ): DeviceHardwareRepository

    @Binds
    @Singleton
    abstract fun bindPacketRepository(
        packetRepositoryImpl: PacketRepositoryImpl
    ): PacketRepository

    @Binds
    @Singleton
    abstract fun bindNodeManager(
        nodeManagerImpl: NodeManagerImpl
    ): NodeManager

    @Binds
    @Singleton
    abstract fun bindCommandSender(
        commandSenderImpl: CommandSenderImpl
    ): CommandSender

    @Binds
    @Singleton
    abstract fun bindHistoryManager(
        historyManagerImpl: HistoryManagerImpl
    ): HistoryManager

    @Binds
    @Singleton
    abstract fun bindTracerouteHandler(
        tracerouteHandlerImpl: TracerouteHandlerImpl
    ): TracerouteHandler

    @Binds
    @Singleton
    abstract fun bindNeighborInfoHandler(
        neighborInfoHandlerImpl: NeighborInfoHandlerImpl
    ): NeighborInfoHandler

    @Binds
    @Singleton
    abstract fun bindMqttManager(
        mqttManagerImpl: MqttManagerImpl
    ): MqttManager

    companion object {
        @Provides
        @Singleton
        fun provideMeshDataMapper(nodeManager: NodeManager): MeshDataMapper = MeshDataMapper(nodeManager)
    }
}
