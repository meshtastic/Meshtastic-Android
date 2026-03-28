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
package org.meshtastic.core.takserver.di

import org.koin.core.annotation.Module
import org.koin.core.annotation.Single
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.repository.CommandSender
import org.meshtastic.core.repository.MeshConfigHandler
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.core.repository.ServiceRepository
import org.meshtastic.core.takserver.TAKMeshIntegration
import org.meshtastic.core.takserver.TAKServer
import org.meshtastic.core.takserver.TAKServerManager
import org.meshtastic.core.takserver.TAKServerManagerImpl
import org.meshtastic.core.takserver.fountain.CoTHandler
import org.meshtastic.core.takserver.fountain.GenericCoTHandler

@Module
class CoreTakServerModule {
    @Single fun provideTAKServer(dispatchers: CoroutineDispatchers): TAKServer = TAKServer(dispatchers = dispatchers)

    @Single fun provideTAKServerManager(takServer: TAKServer): TAKServerManager = TAKServerManagerImpl(takServer)

    @Single
    fun provideGenericCoTHandler(commandSender: CommandSender, takServerManager: TAKServerManager): CoTHandler =
        GenericCoTHandler(commandSender, takServerManager)

    @Single
    fun provideTAKMeshIntegration(
        takServerManager: TAKServerManager,
        commandSender: CommandSender,
        nodeRepository: NodeRepository,
        serviceRepository: ServiceRepository,
        meshConfigHandler: MeshConfigHandler,
        cotHandler: CoTHandler,
    ): TAKMeshIntegration = TAKMeshIntegration(
        takServerManager,
        commandSender,
        nodeRepository,
        serviceRepository,
        meshConfigHandler,
        cotHandler,
    )
}
