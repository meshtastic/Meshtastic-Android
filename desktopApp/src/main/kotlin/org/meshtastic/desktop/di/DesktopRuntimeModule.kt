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
package org.meshtastic.desktop.di

import io.ktor.client.HttpClient
import io.ktor.client.engine.java.Java
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single
import org.meshtastic.core.ble.BleConnectionFactory
import org.meshtastic.core.ble.BleScanner
import org.meshtastic.core.ble.BluetoothRepository
import org.meshtastic.core.common.database.DatabaseManager
import org.meshtastic.core.common.di.ServiceScope
import org.meshtastic.core.data.datasource.BundledAssetReader
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.network.HttpClientDefaults
import org.meshtastic.core.network.KermitHttpLogger
import org.meshtastic.core.network.configureDefaultRetry
import org.meshtastic.core.network.service.ApiService
import org.meshtastic.core.network.service.ApiServiceImpl
import org.meshtastic.core.repository.AdminController
import org.meshtastic.core.repository.CommandSender
import org.meshtastic.core.repository.ConnectionStateProvider
import org.meshtastic.core.repository.MeshDataHandler
import org.meshtastic.core.repository.MeshLocationManager
import org.meshtastic.core.repository.MeshMessageProcessor
import org.meshtastic.core.repository.MeshNotificationManager
import org.meshtastic.core.repository.MeshPrefs
import org.meshtastic.core.repository.MessageQueue
import org.meshtastic.core.repository.MessagingController
import org.meshtastic.core.repository.NeighborInfoResponseProvider
import org.meshtastic.core.repository.NodeController
import org.meshtastic.core.repository.NodeManager
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.core.repository.NotificationManager
import org.meshtastic.core.repository.NotificationPrefs
import org.meshtastic.core.repository.PacketRepository
import org.meshtastic.core.repository.PlatformAnalytics
import org.meshtastic.core.repository.QueryController
import org.meshtastic.core.repository.RadioConfigRepository
import org.meshtastic.core.repository.RadioController
import org.meshtastic.core.repository.RadioInterfaceService
import org.meshtastic.core.repository.RadioTransportFactory
import org.meshtastic.core.repository.ServiceRepository
import org.meshtastic.core.repository.ServiceStateWriter
import org.meshtastic.core.repository.TracerouteResponseProvider
import org.meshtastic.core.repository.UiPrefs
import org.meshtastic.core.service.RadioControllerImpl
import org.meshtastic.core.service.ServiceRepositoryImpl
import org.meshtastic.desktop.DesktopBuildConfig
import org.meshtastic.desktop.DesktopNotificationManager
import org.meshtastic.desktop.notification.DesktopMeshNotificationManager
import org.meshtastic.desktop.notification.DesktopOS
import org.meshtastic.desktop.notification.LinuxNotificationSender
import org.meshtastic.desktop.notification.MacOSNotificationSender
import org.meshtastic.desktop.notification.NativeNotificationSender
import org.meshtastic.desktop.notification.WindowsNotificationSender
import org.meshtastic.desktop.radio.DesktopMessageQueue
import org.meshtastic.desktop.radio.DesktopRadioTransportFactory

/**
 * Desktop runtime wiring: the radio stack, notifications and networking the JVM host owns.
 *
 * These replace bindings that exist only in Android source sets, so nothing here duplicates a `commonMain` default.
 */
@Module
class DesktopRuntimeModule {

    @Single(
        binds =
        [
            ServiceRepository::class,
            ConnectionStateProvider::class,
            TracerouteResponseProvider::class,
            NeighborInfoResponseProvider::class,
            ServiceStateWriter::class,
        ],
    )
    fun serviceRepository(): ServiceRepository = ServiceRepositoryImpl()

    @Single
    fun radioTransportFactory(
        dispatchers: CoroutineDispatchers,
        scanner: BleScanner,
        bluetoothRepository: BluetoothRepository,
        connectionFactory: BleConnectionFactory,
    ): RadioTransportFactory = DesktopRadioTransportFactory(
        dispatchers = dispatchers,
        scanner = scanner,
        bluetoothRepository = bluetoothRepository,
        connectionFactory = connectionFactory,
    )

    @Suppress("LongParameterList")
    @Single(
        binds =
        [
            RadioController::class,
            AdminController::class,
            MessagingController::class,
            NodeController::class,
            QueryController::class,
        ],
    )
    fun radioController(
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
        scope: ServiceScope,
    ): RadioController = RadioControllerImpl(
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
    )

    /**
     * Only the Linux sender holds a native handle; the others are stateless. `Main.kt` closes it explicitly during
     * shutdown, because annotations have no `onClose` equivalent.
     */
    @Single
    fun nativeNotificationSender(): NativeNotificationSender = when (DesktopOS.current()) {
        DesktopOS.Linux -> LinuxNotificationSender()
        DesktopOS.MacOS -> MacOSNotificationSender()
        DesktopOS.Windows -> WindowsNotificationSender()
    }

    @Single(binds = [DesktopNotificationManager::class, NotificationManager::class])
    fun desktopNotificationManager(
        prefs: NotificationPrefs,
        nativeSender: NativeNotificationSender,
    ): DesktopNotificationManager = DesktopNotificationManager(prefs = prefs, nativeSender = nativeSender)

    @Single
    fun meshNotificationManager(notificationManager: NotificationManager): MeshNotificationManager =
        DesktopMeshNotificationManager(notificationManager = notificationManager)

    @Single
    fun messageQueue(
        packetRepository: PacketRepository,
        radioController: RadioController,
        dispatchers: CoroutineDispatchers,
    ): MessageQueue = DesktopMessageQueue(
        packetRepository = packetRepository,
        radioController = radioController,
        dispatchers = dispatchers,
    )

    /** Desktop uses the real `ApiService` implementation over the JVM `HttpClient` below — no flavor stub needed. */
    @Single fun apiService(apiServiceImpl: ApiServiceImpl): ApiService = apiServiceImpl

    /** Ktor [HttpClient] for JVM/Desktop — the equivalent of `CoreNetworkAndroidModule`'s OkHttp-backed client. */
    @Single
    fun httpClient(json: Json): HttpClient = HttpClient(Java) {
        engine {
            protocolVersion = java.net.http.HttpClient.Version.HTTP_2
            config { followRedirects(java.net.http.HttpClient.Redirect.NORMAL) }
        }
        install(ContentNegotiation) { json(json) }
        install(DefaultRequest) {
            url(HttpClientDefaults.API_BASE_URL)
            header(HttpHeaders.UserAgent, "Meshtastic-Desktop/${DesktopBuildConfig.VERSION_NAME}")
        }
        install(HttpTimeout) {
            requestTimeoutMillis = HttpClientDefaults.REQUEST_TIMEOUT_MS
            connectTimeoutMillis = HttpClientDefaults.TIMEOUT_MS
            socketTimeoutMillis = HttpClientDefaults.TIMEOUT_MS
        }
        install(HttpRequestRetry) { configureDefaultRetry() }
        if (DesktopBuildConfig.IS_DEBUG) {
            install(Logging) {
                logger = KermitHttpLogger
                level = LogLevel.INFO
            }
        }
    }

    /** Desktop has no bundled Android assets; repositories seed from the network instead. */
    @Single fun bundledAssetReader(): BundledAssetReader = BundledAssetReader { null }
}
