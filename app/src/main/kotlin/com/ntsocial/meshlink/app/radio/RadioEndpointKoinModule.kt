/*
 * NTsocial MeshLink original work and modifications:
 * Copyright (c) 2026 LiberaNt LLC
 *
 * Meshtastic Android-derived portions, where present:
 * Copyright (c) 2026 Meshtastic LLC
 *
 * Developed and/or modified for NTsocial MeshLink in 2026.
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
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
@file:Suppress("LongMethod")

package com.ntsocial.meshlink.app.radio

import com.ntsocial.meshlink.core.common.database.DatabaseManager
import com.ntsocial.meshlink.core.data.datasource.DeviceHardwareLocalDataSource
import com.ntsocial.meshlink.core.data.datasource.DeviceLinkLocalDataSource
import com.ntsocial.meshlink.core.data.datasource.FirmwareReleaseLocalDataSource
import com.ntsocial.meshlink.core.data.datasource.NodeInfoReadDataSource
import com.ntsocial.meshlink.core.data.datasource.NodeInfoWriteDataSource
import com.ntsocial.meshlink.core.data.datasource.SwitchingNodeInfoReadDataSource
import com.ntsocial.meshlink.core.data.datasource.SwitchingNodeInfoWriteDataSource
import com.ntsocial.meshlink.core.data.manager.AdminPacketHandlerImpl
import com.ntsocial.meshlink.core.data.manager.CommandSenderImpl
import com.ntsocial.meshlink.core.data.manager.DataLayerHeartbeatSender
import com.ntsocial.meshlink.core.data.manager.FromRadioPacketHandlerImpl
import com.ntsocial.meshlink.core.data.manager.HandshakeChannelSetCollector
import com.ntsocial.meshlink.core.data.manager.HistoryManagerImpl
import com.ntsocial.meshlink.core.data.manager.MeshActionHandlerImpl
import com.ntsocial.meshlink.core.data.manager.MeshConfigFlowManagerImpl
import com.ntsocial.meshlink.core.data.manager.MeshConfigHandlerImpl
import com.ntsocial.meshlink.core.data.manager.MeshConnectionManagerImpl
import com.ntsocial.meshlink.core.data.manager.MeshDataHandlerImpl
import com.ntsocial.meshlink.core.data.manager.MeshMessageProcessorImpl
import com.ntsocial.meshlink.core.data.manager.MeshRouterImpl
import com.ntsocial.meshlink.core.data.manager.MessageFilterImpl
import com.ntsocial.meshlink.core.data.manager.MqttManagerImpl
import com.ntsocial.meshlink.core.data.manager.NeighborInfoHandlerImpl
import com.ntsocial.meshlink.core.data.manager.NodeManagerImpl
import com.ntsocial.meshlink.core.data.manager.PacketHandlerImpl
import com.ntsocial.meshlink.core.data.manager.RadioIngressWorkTracker
import com.ntsocial.meshlink.core.data.manager.SessionManagerImpl
import com.ntsocial.meshlink.core.data.manager.SessionMessageQueue
import com.ntsocial.meshlink.core.data.manager.StoreForwardPacketHandlerImpl
import com.ntsocial.meshlink.core.data.manager.TelemetryPacketHandlerImpl
import com.ntsocial.meshlink.core.data.manager.TracerouteHandlerImpl
import com.ntsocial.meshlink.core.data.manager.XModemManagerImpl
import com.ntsocial.meshlink.core.data.ntsocial.NtsocialChannelProvisioner
import com.ntsocial.meshlink.core.data.repository.DeviceHardwareRepositoryImpl
import com.ntsocial.meshlink.core.data.repository.DeviceLinkRepositoryImpl
import com.ntsocial.meshlink.core.data.repository.FirmwareReleaseRepositoryImpl
import com.ntsocial.meshlink.core.data.repository.MeshLogRepositoryImpl
import com.ntsocial.meshlink.core.data.repository.NodeRepositoryImpl
import com.ntsocial.meshlink.core.data.repository.NtsocialGatewayRepositoryImpl
import com.ntsocial.meshlink.core.data.repository.PacketRepositoryImpl
import com.ntsocial.meshlink.core.data.repository.QuickChatActionRepositoryImpl
import com.ntsocial.meshlink.core.data.repository.RadioConfigRepositoryImpl
import com.ntsocial.meshlink.core.database.DatabaseProvider
import com.ntsocial.meshlink.core.database.EndpointDatabaseHandle
import com.ntsocial.meshlink.core.datastore.ChannelSetDataSource
import com.ntsocial.meshlink.core.datastore.LocalConfigDataSource
import com.ntsocial.meshlink.core.datastore.LocalStatsDataSource
import com.ntsocial.meshlink.core.datastore.ModuleConfigDataSource
import com.ntsocial.meshlink.core.domain.usecase.session.EnsureRemoteAdminSessionUseCase
import com.ntsocial.meshlink.core.domain.usecase.session.ObserveRemoteAdminSessionStatusUseCase
import com.ntsocial.meshlink.core.domain.usecase.settings.AdminActionsUseCase
import com.ntsocial.meshlink.core.domain.usecase.settings.ChannelReliabilityManagerImpl
import com.ntsocial.meshlink.core.domain.usecase.settings.CleanNodeDatabaseUseCase
import com.ntsocial.meshlink.core.domain.usecase.settings.ExportDataUseCase
import com.ntsocial.meshlink.core.domain.usecase.settings.InstallProfileUseCase
import com.ntsocial.meshlink.core.domain.usecase.settings.IsOtaCapableUseCase
import com.ntsocial.meshlink.core.domain.usecase.settings.IsOtaCapableUseCaseImpl
import com.ntsocial.meshlink.core.domain.usecase.settings.MeshLocationUseCase
import com.ntsocial.meshlink.core.domain.usecase.settings.RadioConfigUseCase
import com.ntsocial.meshlink.core.domain.usecase.settings.SetDatabaseCacheLimitUseCase
import com.ntsocial.meshlink.core.domain.usecase.settings.SetMeshLogSettingsUseCase
import com.ntsocial.meshlink.core.model.RadioController
import com.ntsocial.meshlink.core.model.util.MeshDataMapper
import com.ntsocial.meshlink.core.model.util.NodeIdLookup
import com.ntsocial.meshlink.core.repository.AdminPacketHandler
import com.ntsocial.meshlink.core.repository.AppWidgetUpdater
import com.ntsocial.meshlink.core.repository.ChannelMutationLock
import com.ntsocial.meshlink.core.repository.ChannelOperationLock
import com.ntsocial.meshlink.core.repository.ChannelReliabilityManager
import com.ntsocial.meshlink.core.repository.CommandSender
import com.ntsocial.meshlink.core.repository.DeviceHardwareRepository
import com.ntsocial.meshlink.core.repository.DeviceLinkRepository
import com.ntsocial.meshlink.core.repository.FirmwareReleaseRepository
import com.ntsocial.meshlink.core.repository.FromRadioPacketHandler
import com.ntsocial.meshlink.core.repository.GatewayIngressSessionGate
import com.ntsocial.meshlink.core.repository.HistoryManager
import com.ntsocial.meshlink.core.repository.MeshActionHandler
import com.ntsocial.meshlink.core.repository.MeshConfigFlowManager
import com.ntsocial.meshlink.core.repository.MeshConfigHandler
import com.ntsocial.meshlink.core.repository.MeshConnectionManager
import com.ntsocial.meshlink.core.repository.MeshDataHandler
import com.ntsocial.meshlink.core.repository.MeshLocationManager
import com.ntsocial.meshlink.core.repository.MeshLogRepository
import com.ntsocial.meshlink.core.repository.MeshMessageProcessor
import com.ntsocial.meshlink.core.repository.MeshPrefs
import com.ntsocial.meshlink.core.repository.MeshRouter
import com.ntsocial.meshlink.core.repository.MeshServiceNotifications
import com.ntsocial.meshlink.core.repository.MeshWorkerManager
import com.ntsocial.meshlink.core.repository.MessageFilter
import com.ntsocial.meshlink.core.repository.MessageQueue
import com.ntsocial.meshlink.core.repository.MqttManager
import com.ntsocial.meshlink.core.repository.NeighborInfoHandler
import com.ntsocial.meshlink.core.repository.NodeManager
import com.ntsocial.meshlink.core.repository.NodeRepository
import com.ntsocial.meshlink.core.repository.NtsocialGatewayRepository
import com.ntsocial.meshlink.core.repository.PacketHandler
import com.ntsocial.meshlink.core.repository.PacketRepository
import com.ntsocial.meshlink.core.repository.QuickChatActionRepository
import com.ntsocial.meshlink.core.repository.RadioConfigRepository
import com.ntsocial.meshlink.core.repository.RadioInterfaceService
import com.ntsocial.meshlink.core.repository.RadioPrefs
import com.ntsocial.meshlink.core.repository.ServiceBroadcasts
import com.ntsocial.meshlink.core.repository.ServiceRepository
import com.ntsocial.meshlink.core.repository.SessionManager
import com.ntsocial.meshlink.core.repository.StoreForwardPacketHandler
import com.ntsocial.meshlink.core.repository.TelemetryPacketHandler
import com.ntsocial.meshlink.core.repository.TracerouteHandler
import com.ntsocial.meshlink.core.repository.XModemManager
import com.ntsocial.meshlink.core.repository.usecase.SendMessageUseCase
import com.ntsocial.meshlink.core.repository.usecase.SendMessageUseCaseImpl
import com.ntsocial.meshlink.core.service.DirectRadioControllerImpl
import com.ntsocial.meshlink.core.service.ServiceRepositoryImpl
import com.ntsocial.meshlink.core.service.SharedRadioInterfaceService
import com.ntsocial.meshlink.feature.node.detail.CommonNodeRequestActions
import com.ntsocial.meshlink.feature.node.detail.NodeManagementActions
import com.ntsocial.meshlink.feature.node.detail.NodeRequestActions
import com.ntsocial.meshlink.feature.node.domain.usecase.CommonGetNodeDetailsUseCase
import com.ntsocial.meshlink.feature.node.domain.usecase.GetFilteredNodesUseCase
import com.ntsocial.meshlink.feature.node.domain.usecase.GetNodeDetailsUseCase
import kotlinx.coroutines.CoroutineScope
import org.koin.core.module.dsl.scopedOf
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.binds
import org.koin.dsl.module

internal val radioEndpointScopeQualifier = named("AndroidMeshtasticEndpoint")

/**
 * Radio-owned definitions are repeated once per secondary endpoint; app-global services continue to resolve at root.
 */
internal val radioEndpointKoinModule = module {
    scope(radioEndpointScopeQualifier) {
        scoped<EndpointDatabaseHandle> { get<RadioEndpointScopeContext>().database }
        scoped<DatabaseProvider> { get<RadioEndpointScopeContext>().database }
        scoped<DatabaseManager> { get<RadioEndpointScopeContext>().database }
        scoped<RadioPrefs> { get<RadioEndpointScopeContext>().radioPrefs }
        scoped<MeshPrefs> { get<RadioEndpointScopeContext>().meshPrefs }
        scoped<CoroutineScope>(named("ServiceScope")) { get<RadioEndpointScopeContext>().serviceScope }

        scoped<ChannelSetDataSource> { get<RadioEndpointScopeContext>().dataSources.channelSet }
        scoped<LocalConfigDataSource> { get<RadioEndpointScopeContext>().dataSources.localConfig }
        scoped<ModuleConfigDataSource> { get<RadioEndpointScopeContext>().dataSources.moduleConfig }
        scoped<LocalStatsDataSource> { get<RadioEndpointScopeContext>().dataSources.localStats }

        scoped<ServiceBroadcasts> { EndpointServiceBroadcasts }
        scoped<MeshServiceNotifications> { EndpointServiceNotifications }
        scoped<MeshLocationManager> { EndpointMeshLocationManager }
        scoped<AppWidgetUpdater> { EndpointAppWidgetUpdater }
        // Gateway v1/v2 remains owned by the legacy-primary graph. A secondary endpoint must
        // never inherit the root Provider route or publish through an endpoint-less contract.
        scoped<NtsocialGatewayRepository> {
            NtsocialGatewayRepositoryImpl(
                commandSender = get(),
                packetRepository = get(),
                messageQueue = get(),
                nodeRepository = get(),
                radioConfigRepository = get(),
                radioInterfaceService = get(),
                databaseManager = get(),
                ingressWorkTracker = get(),
                scope = get(named("ServiceScope")),
                ingressSessionGate = get(),
            )
        }

        scopedOf(::SwitchingNodeInfoReadDataSource).bind<NodeInfoReadDataSource>()
        scopedOf(::SwitchingNodeInfoWriteDataSource).bind<NodeInfoWriteDataSource>()
        scoped {
            NodeRepositoryImpl(
                processLifecycle = get(named("ProcessLifecycle")),
                nodeInfoReadDataSource = get(),
                nodeInfoWriteDataSource = get(),
                dispatchers = get(),
                localStatsDataSource = get(),
                ingressWorkTracker = get(),
            )
        }
            .bind<NodeRepository>()
        scopedOf(::PacketRepositoryImpl).bind<PacketRepository>()
        scopedOf(::QuickChatActionRepositoryImpl).bind<QuickChatActionRepository>()
        scopedOf(::MeshLogRepositoryImpl).bind<MeshLogRepository>()
        scopedOf(::RadioConfigRepositoryImpl).bind<RadioConfigRepository>()
        scopedOf(::FirmwareReleaseLocalDataSource)
        scopedOf(::FirmwareReleaseRepositoryImpl).bind<FirmwareReleaseRepository>()
        scopedOf(::DeviceHardwareLocalDataSource)
        scopedOf(::DeviceHardwareRepositoryImpl).bind<DeviceHardwareRepository>()
        scopedOf(::DeviceLinkLocalDataSource)
        scopedOf(::DeviceLinkRepositoryImpl).bind<DeviceLinkRepository>()

        scopedOf(::RadioIngressWorkTracker)
        scopedOf(::GatewayIngressSessionGate)
        scopedOf(::ChannelOperationLock)
        scopedOf(::ChannelMutationLock)
        scopedOf(::SessionManagerImpl).bind<SessionManager>()
        scoped {
            NodeManagerImpl(
                nodeRepository = get(),
                serviceBroadcasts = get(),
                notificationManager = get(),
                ingressWorkTracker = get(),
                scope = get(named("ServiceScope")),
            )
        }
            .binds(arrayOf(NodeManager::class, NodeIdLookup::class))
        scoped { MeshDataMapper(get()) }

        scopedOf(::ServiceRepositoryImpl).bind<ServiceRepository>()
        scoped {
            SharedRadioInterfaceService(
                dispatchers = get(),
                bluetoothRepository = get(),
                networkRepository = get(),
                processLifecycle = get(named("ProcessLifecycle")),
                radioPrefs = get(),
                transportFactory = get(),
                analytics = get(),
            )
        }
            .bind<RadioInterfaceService>()
        scoped {
            PacketHandlerImpl(
                packetRepository = lazy { get<PacketRepository>() },
                serviceBroadcasts = get(),
                radioInterfaceService = get(),
                meshLogRepository = lazy { get<MeshLogRepository>() },
                serviceRepository = get(),
                ingressWorkTracker = get(),
                channelOperationLock = get(),
                radioConfigRepository = get(),
                gatewayIngressSessionGate = get(),
                scope = get(named("ServiceScope")),
            )
        }
            .bind<PacketHandler>()
        scoped {
            CommandSenderImpl(
                packetHandler = get(),
                nodeManager = get(),
                radioConfigRepository = get(),
                tracerouteHandler = get(),
                neighborInfoHandler = get(),
                sessionManager = get(),
                radioInterfaceService = get(),
                channelOperationLock = get(),
                scope = get(named("ServiceScope")),
            )
        }
            .bind<CommandSender>()
        scopedOf(::HandshakeChannelSetCollector)
        scoped {
            MeshConfigHandlerImpl(
                radioConfigRepository = get(),
                serviceRepository = get(),
                nodeManager = get(),
                channelSetCollector = get(),
                ingressWorkTracker = get(),
                scope = get(named("ServiceScope")),
            )
        }
            .bind<MeshConfigHandler>()
        scoped {
            AdminPacketHandlerImpl(
                nodeManager = get(),
                configHandler = lazy { get<MeshConfigHandler>() },
                configFlowManager = lazy { get<MeshConfigFlowManager>() },
                sessionManager = get(),
            )
        }
            .bind<AdminPacketHandler>()
        scoped {
            TracerouteHandlerImpl(
                serviceRepository = get(),
                nodeRepository = get(),
                ingressWorkTracker = get(),
                scope = get(named("ServiceScope")),
            )
        }
            .bind<TracerouteHandler>()
        scopedOf(::NeighborInfoHandlerImpl).bind<NeighborInfoHandler>()
        scoped {
            TelemetryPacketHandlerImpl(
                nodeManager = get(),
                connectionManager = lazy { get<MeshConnectionManager>() },
                notificationManager = get(),
                ingressWorkTracker = get(),
                scope = get(named("ServiceScope")),
            )
        }
            .bind<TelemetryPacketHandler>()
        scopedOf(::HistoryManagerImpl).bind<HistoryManager>()
        scoped {
            StoreForwardPacketHandlerImpl(
                nodeManager = get(),
                packetRepository = lazy { get<PacketRepository>() },
                serviceBroadcasts = get(),
                historyManager = get(),
                dataHandler = lazy { get<MeshDataHandler>() },
                ingressWorkTracker = get(),
                scope = get(named("ServiceScope")),
            )
        }
            .bind<StoreForwardPacketHandler>()
        scopedOf(::MessageFilterImpl).bind<MessageFilter>()
        scoped {
            MqttManagerImpl(
                mqttRepository = get(),
                packetHandler = get(),
                serviceRepository = get(),
                nodeRepository = get(),
                scope = get(named("ServiceScope")),
            )
        }
            .bind<MqttManager>()
        scoped {
            MeshDataHandlerImpl(
                nodeManager = get(),
                packetHandler = get(),
                serviceRepository = get(),
                packetRepository = lazy { get<PacketRepository>() },
                serviceBroadcasts = get(),
                notificationManager = get(),
                serviceNotifications = get(),
                analytics = get(),
                dataMapper = get(),
                tracerouteHandler = get(),
                neighborInfoHandler = get(),
                radioConfigRepository = get(),
                messageFilter = get(),
                storeForwardHandler = get(),
                telemetryHandler = get(),
                adminPacketHandler = get(),
                ntsocialGatewayRepository = get(),
                ingressWorkTracker = get(),
                scope = get(named("ServiceScope")),
            )
        }
            .bind<MeshDataHandler>()
        scoped {
            MeshConfigFlowManagerImpl(
                nodeManager = get(),
                connectionManager = lazy { get<MeshConnectionManager>() },
                radioInterfaceService = get(),
                nodeRepository = get(),
                radioConfigRepository = get(),
                serviceRepository = get(),
                serviceBroadcasts = get(),
                analytics = get(),
                commandSender = get(),
                heartbeatSender = get(),
                notificationPrefs = get(),
                channelSetCollector = get(),
                channelOperationLock = get(),
                scope = get(named("ServiceScope")),
            )
        }
            .bind<MeshConfigFlowManager>()
        scoped {
            FromRadioPacketHandlerImpl(
                serviceRepository = get(),
                router = lazy { get<MeshRouter>() },
                mqttManager = get(),
                packetHandler = get(),
                notificationManager = get(),
            )
        }
            .bind<FromRadioPacketHandler>()
        scoped {
            MeshMessageProcessorImpl(
                nodeManager = get(),
                serviceRepository = get(),
                meshLogRepository = lazy { get<MeshLogRepository>() },
                router = lazy { get<MeshRouter>() },
                fromRadioDispatcher = get(),
                ingressWorkTracker = get(),
                scope = get(named("ServiceScope")),
            )
        }
            .bind<MeshMessageProcessor>()
        scopedOf(::DataLayerHeartbeatSender)
        scopedOf(::XModemManagerImpl).bind<XModemManager>()
        scoped {
            MeshActionHandlerImpl(
                nodeManager = get(),
                commandSender = get(),
                packetRepository = lazy { get<PacketRepository>() },
                serviceBroadcasts = get(),
                dataHandler = lazy { get<MeshDataHandler>() },
                analytics = get(),
                meshPrefs = get(),
                uiPrefs = get(),
                databaseManager = get(),
                notificationManager = get(),
                messageProcessor = lazy { get<MeshMessageProcessor>() },
                radioConfigRepository = get(),
                channelMutationLock = get(),
                scope = get(named("ServiceScope")),
            )
        }
            .bind<MeshActionHandler>()
        scoped {
            MeshRouterImpl(
                dataHandlerLazy = lazy { get<MeshDataHandler>() },
                configHandlerLazy = lazy { get<MeshConfigHandler>() },
                tracerouteHandlerLazy = lazy { get<TracerouteHandler>() },
                neighborInfoHandlerLazy = lazy { get<NeighborInfoHandler>() },
                configFlowManagerLazy = lazy { get<MeshConfigFlowManager>() },
                mqttManagerLazy = lazy { get<MqttManager>() },
                actionHandlerLazy = lazy { get<MeshActionHandler>() },
                xmodemManagerLazy = lazy { get<XModemManager>() },
            )
        }
            .bind<MeshRouter>()
        scoped {
            MeshConnectionManagerImpl(
                radioInterfaceService = get(),
                serviceRepository = get(),
                serviceBroadcasts = get(),
                serviceNotifications = get(),
                uiPrefs = get(),
                packetHandler = get(),
                nodeRepository = get(),
                locationManager = get(),
                mqttManager = get(),
                historyManager = get(),
                radioConfigRepository = get(),
                commandSender = get(),
                sessionManager = get(),
                nodeManager = get(),
                analytics = get(),
                packetRepository = get(),
                workerManager = get(),
                appWidgetUpdater = get(),
                heartbeatSender = get(),
                ntsocialChannelProvisioner = get(),
                ntsocialGatewayRepository = get(),
                channelReliabilityManager = get(),
                channelOperationLock = get(),
                channelMutationLock = get(),
                scope = get(named("ServiceScope")),
            )
        }
            .bind<MeshConnectionManager>()

        scoped {
            NtsocialChannelProvisioner(
                commandSender = get(),
                radioConfigRepository = get(),
                sessionManager = get(),
                channelOperationLock = get(),
                channelMutationLock = get(),
                radioInterfaceService = get(),
                ntsocialGatewayRepository = get(),
                meshConfigFlowManager = lazy { get<MeshConfigFlowManager>() },
            )
        }
        scoped {
            EnsureRemoteAdminSessionUseCase(
                sessionManager = get(),
                meshActionHandler = get(),
                serviceRepository = get(),
                commandSender = get(),
                radioInterfaceService = get(),
                serviceScope = get(named("ServiceScope")),
            )
        }
        scopedOf(::ObserveRemoteAdminSessionStatusUseCase)
        scoped {
            ChannelReliabilityManagerImpl(
                commandSender = get(),
                serviceRepository = get(),
                nodeRepository = get(),
                radioConfigRepository = get(),
                channelSnapshotRepository = get(),
                ensureRemoteAdminSession = get(),
                meshConfigFlowManager = lazy { get<MeshConfigFlowManager>() },
                operationLock = get(),
                mutationLock = get(),
                radioInterfaceService = get(),
                ntsocialGatewayRepository = get(),
                serviceScope = get(named("ServiceScope")),
            )
        }
            .bind<ChannelReliabilityManager>()
        scopedOf(::DirectRadioControllerImpl).bind<RadioController>()

        scoped {
            SessionMessageQueue(
                packetRepository = get(),
                radioController = lazy { get<RadioController>() },
                commandSender = get(),
                radioConfigRepository = get(),
                radioInterfaceService = get(),
                gatewayIngressSessionGate = get(),
                parentScope = get(named("ServiceScope")),
            )
        }
            .bind<MessageQueue>()
        scopedOf(::EndpointMeshWorkerManager).bind<MeshWorkerManager>()

        scopedOf(::AdminActionsUseCase)
        scopedOf(::CleanNodeDatabaseUseCase)
        scopedOf(::ExportDataUseCase)
        scopedOf(::InstallProfileUseCase)
        scopedOf(::MeshLocationUseCase)
        scopedOf(::RadioConfigUseCase)
        scopedOf(::SetDatabaseCacheLimitUseCase)
        scopedOf(::SetMeshLogSettingsUseCase)
        scopedOf(::IsOtaCapableUseCaseImpl).bind<IsOtaCapableUseCase>()
        scopedOf(::SendMessageUseCaseImpl).bind<SendMessageUseCase>()

        scopedOf(::NodeManagementActions)
        scopedOf(::CommonNodeRequestActions).bind<NodeRequestActions>()
        scopedOf(::GetFilteredNodesUseCase)
        scopedOf(::CommonGetNodeDetailsUseCase).bind<GetNodeDetailsUseCase>()
    }
}
