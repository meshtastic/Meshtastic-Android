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
package org.meshtastic.core.service.di

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module
import org.koin.core.annotation.Named
import org.koin.core.annotation.Single
import org.meshtastic.core.common.database.DatabaseManager
import org.meshtastic.core.repository.AdminController
import org.meshtastic.core.repository.CommandSender
import org.meshtastic.core.repository.MeshDataHandler
import org.meshtastic.core.repository.MeshLocationManager
import org.meshtastic.core.repository.MeshMessageProcessor
import org.meshtastic.core.repository.MeshPrefs
import org.meshtastic.core.repository.MessagingController
import org.meshtastic.core.repository.NodeController
import org.meshtastic.core.repository.NodeManager
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.core.repository.NotificationManager
import org.meshtastic.core.repository.PacketRepository
import org.meshtastic.core.repository.PlatformAnalytics
import org.meshtastic.core.repository.RadioConfigRepository
import org.meshtastic.core.repository.RadioController
import org.meshtastic.core.repository.RadioInterfaceService
import org.meshtastic.core.repository.RequestController
import org.meshtastic.core.repository.ServiceRepository
import org.meshtastic.core.repository.UiPrefs
import org.meshtastic.core.service.DirectRadioControllerImpl
import org.meshtastic.core.service.MeshService
import org.meshtastic.core.service.startService

@Module
@ComponentScan("org.meshtastic.core.service")
class CoreServiceAndroidModule {
    @Suppress("LongParameterList")
    @Single(
        binds =
        [
            RadioController::class,
            AdminController::class,
            MessagingController::class,
            NodeController::class,
            RequestController::class,
        ],
    )
    fun radioController(
        context: Context,
        serviceRepository: ServiceRepository,
        nodeRepository: NodeRepository,
        commandSender: CommandSender,
        nodeManager: NodeManager,
        radioInterfaceService: RadioInterfaceService,
        locationManager: MeshLocationManager,
        packetRepository: Lazy<PacketRepository>,
        dataHandler: Lazy<MeshDataHandler>,
        analytics: PlatformAnalytics,
        meshPrefs: MeshPrefs,
        uiPrefs: UiPrefs,
        databaseManager: DatabaseManager,
        notificationManager: NotificationManager,
        messageProcessor: Lazy<MeshMessageProcessor>,
        radioConfigRepository: RadioConfigRepository,
        @Named("ServiceScope") scope: CoroutineScope,
    ): RadioController = DirectRadioControllerImpl(
        serviceRepository = serviceRepository,
        nodeRepository = nodeRepository,
        commandSender = commandSender,
        nodeManager = nodeManager,
        radioInterfaceService = radioInterfaceService,
        locationManager = locationManager,
        packetRepository = packetRepository,
        dataHandler = dataHandler,
        analytics = analytics,
        meshPrefs = meshPrefs,
        uiPrefs = uiPrefs,
        databaseManager = databaseManager,
        notificationManager = notificationManager,
        messageProcessor = messageProcessor,
        radioConfigRepository = radioConfigRepository,
        scope = scope,
        onDeviceAddressChanged = { MeshService.startService(context) },
    )
}
