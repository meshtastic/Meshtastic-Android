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
@file:Suppress(
    "ktlint:standard:no-unused-imports",
) // Koin K2 compiler plugin generates aliased module extensions referenced in desktopModule()

package org.meshtastic.desktop.di

// Generated Koin module extensions from core KMP modules
import io.ktor.client.HttpClient
import io.ktor.client.engine.java.Java
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.url
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import org.koin.dsl.module
import org.meshtastic.core.data.datasource.BootloaderOtaQuirksJsonDataSource
import org.meshtastic.core.data.datasource.DeviceHardwareJsonDataSource
import org.meshtastic.core.data.datasource.FirmwareReleaseJsonDataSource
import org.meshtastic.core.model.BootloaderOtaQuirk
import org.meshtastic.core.model.NetworkDeviceHardware
import org.meshtastic.core.model.NetworkFirmwareReleases
import org.meshtastic.core.model.RadioController
import org.meshtastic.core.network.HttpClientDefaults
import org.meshtastic.core.network.KermitHttpLogger
import org.meshtastic.core.network.repository.MQTTRepository
import org.meshtastic.core.network.service.ApiService
import org.meshtastic.core.network.service.ApiServiceImpl
import org.meshtastic.core.repository.AppWidgetUpdater
import org.meshtastic.core.repository.LocationRepository
import org.meshtastic.core.repository.MeshLocationManager
import org.meshtastic.core.repository.MeshServiceNotifications
import org.meshtastic.core.repository.MeshWorkerManager
import org.meshtastic.core.repository.MessageQueue
import org.meshtastic.core.repository.NotificationManager
import org.meshtastic.core.repository.PlatformAnalytics
import org.meshtastic.core.repository.RadioTransportFactory
import org.meshtastic.core.repository.ServiceBroadcasts
import org.meshtastic.core.repository.ServiceRepository
import org.meshtastic.core.service.DirectRadioControllerImpl
import org.meshtastic.core.service.ServiceRepositoryImpl
import org.meshtastic.desktop.DesktopBuildConfig
import org.meshtastic.desktop.DesktopNotificationManager
import org.meshtastic.desktop.notification.DesktopMeshServiceNotifications
import org.meshtastic.desktop.notification.DesktopOS
import org.meshtastic.desktop.notification.LinuxNotificationSender
import org.meshtastic.desktop.notification.MacOSNotificationSender
import org.meshtastic.desktop.notification.NativeNotificationSender
import org.meshtastic.desktop.notification.WindowsNotificationSender
import org.meshtastic.desktop.radio.DesktopMessageQueue
import org.meshtastic.desktop.radio.DesktopRadioTransportFactory
import org.meshtastic.desktop.stub.NoopAppWidgetUpdater
import org.meshtastic.desktop.stub.NoopCompassHeadingProvider
import org.meshtastic.desktop.stub.NoopLocationRepository
import org.meshtastic.desktop.stub.NoopMQTTRepository
import org.meshtastic.desktop.stub.NoopMagneticFieldProvider
import org.meshtastic.desktop.stub.NoopMeshLocationManager
import org.meshtastic.desktop.stub.NoopMeshWorkerManager
import org.meshtastic.desktop.stub.NoopPhoneLocationProvider
import org.meshtastic.desktop.stub.NoopPlatformAnalytics
import org.meshtastic.desktop.stub.NoopServiceBroadcasts
import org.meshtastic.feature.node.compass.CompassHeadingProvider
import org.meshtastic.feature.node.compass.MagneticFieldProvider
import org.meshtastic.feature.node.compass.PhoneLocationProvider
import org.meshtastic.core.ble.di.module as coreBleModule
import org.meshtastic.core.common.di.module as coreCommonModule
import org.meshtastic.core.data.di.module as coreDataModule
import org.meshtastic.core.database.di.module as coreDatabaseModule
import org.meshtastic.core.datastore.di.module as coreDatastoreModule
import org.meshtastic.core.di.di.module as coreDiModule
import org.meshtastic.core.domain.di.module as coreDomainModule
import org.meshtastic.core.network.di.module as coreNetworkModule
import org.meshtastic.core.prefs.di.module as corePrefsModule
import org.meshtastic.core.repository.di.module as coreRepositoryModule
import org.meshtastic.core.service.di.module as coreServiceModule
import org.meshtastic.core.takserver.di.module as coreTakServerModule
import org.meshtastic.core.ui.di.module as coreUiModule
import org.meshtastic.desktop.di.module as desktopDiModule
import org.meshtastic.feature.connections.di.module as featureConnectionsModule
import org.meshtastic.feature.firmware.di.module as featureFirmwareModule
import org.meshtastic.feature.intro.di.module as featureIntroModule
import org.meshtastic.feature.map.di.module as featureMapModule
import org.meshtastic.feature.messaging.di.module as featureMessagingModule
import org.meshtastic.feature.node.di.module as featureNodeModule
import org.meshtastic.feature.settings.di.module as featureSettingsModule
import org.meshtastic.feature.wifiprovision.di.module as featureWifiProvisionModule

/**
 * Koin module for the Desktop target.
 *
 * Includes the generated Koin K2 modules from core KMP libraries (which provide real implementations of prefs, data
 * repositories, managers, datastore data sources, use cases, and ViewModels from `commonMain`).
 *
 * Only truly platform-specific interfaces are stubbed here — things that require Android APIs (BLE/USB transport,
 * notifications, WorkManager, location services, broadcasts, widgets).
 *
 * Platform infrastructure (DataStores, Room database, Lifecycle) is provided by [desktopPlatformModule].
 */
fun desktopModule() = module {
    // Include generated Koin K2 modules from core KMP libraries (commonMain implementations)
    includes(
        org.meshtastic.core.di.di.CoreDiModule().coreDiModule(),
        org.meshtastic.core.common.di.CoreCommonModule().coreCommonModule(),
        org.meshtastic.core.datastore.di.CoreDatastoreModule().coreDatastoreModule(),
        org.meshtastic.core.prefs.di.CorePrefsModule().corePrefsModule(),
        org.meshtastic.core.database.di.CoreDatabaseModule().coreDatabaseModule(),
        org.meshtastic.core.data.di.CoreDataModule().coreDataModule(),
        org.meshtastic.core.domain.di.CoreDomainModule().coreDomainModule(),
        org.meshtastic.core.repository.di.CoreRepositoryModule().coreRepositoryModule(),
        org.meshtastic.core.network.di.CoreNetworkModule().coreNetworkModule(),
        org.meshtastic.core.ble.di.CoreBleModule().coreBleModule(),
        org.meshtastic.core.ui.di.CoreUiModule().coreUiModule(),
        org.meshtastic.core.service.di.CoreServiceModule().coreServiceModule(),
        org.meshtastic.core.takserver.di.CoreTakServerModule().coreTakServerModule(),
        org.meshtastic.feature.settings.di.FeatureSettingsModule().featureSettingsModule(),
        org.meshtastic.feature.node.di.FeatureNodeModule().featureNodeModule(),
        org.meshtastic.feature.messaging.di.FeatureMessagingModule().featureMessagingModule(),
        org.meshtastic.feature.connections.di.FeatureConnectionsModule().featureConnectionsModule(),
        org.meshtastic.feature.map.di.FeatureMapModule().featureMapModule(),
        org.meshtastic.feature.firmware.di.FeatureFirmwareModule().featureFirmwareModule(),
        org.meshtastic.feature.intro.di.FeatureIntroModule().featureIntroModule(),
        org.meshtastic.feature.wifiprovision.di.FeatureWifiProvisionModule().featureWifiProvisionModule(),
        org.meshtastic.desktop.di.DesktopDiModule().desktopDiModule(),
        desktopPlatformStubsModule(),
    )
}

/**
 * Stubs for truly platform-specific interfaces that have no `commonMain` implementation. These require Android APIs
 * (BLE/USB transport, notifications, WorkManager, location, broadcasts, widgets).
 */
@Suppress("LongMethod")
private fun desktopPlatformStubsModule() = module {
    single<ServiceRepository> { ServiceRepositoryImpl() }
    single<RadioTransportFactory> {
        DesktopRadioTransportFactory(
            dispatchers = get(),
            scanner = get(),
            bluetoothRepository = get(),
            connectionFactory = get(),
        )
    }
    single<RadioController> {
        DirectRadioControllerImpl(
            serviceRepository = get(),
            nodeRepository = get(),
            commandSender = get(),
            router = get(),
            nodeManager = get(),
            radioInterfaceService = get(),
            locationManager = get(),
        )
    }
    single<NativeNotificationSender> {
        when (DesktopOS.current()) {
            DesktopOS.Linux -> LinuxNotificationSender()
            DesktopOS.MacOS -> MacOSNotificationSender()
            DesktopOS.Windows -> WindowsNotificationSender()
        }
    }
    single { DesktopNotificationManager(prefs = get(), nativeSender = get()) }
    single<NotificationManager> { get<DesktopNotificationManager>() }
    single<MeshServiceNotifications> { DesktopMeshServiceNotifications(notificationManager = get()) }
    single<PlatformAnalytics> { NoopPlatformAnalytics() }
    single<ServiceBroadcasts> { NoopServiceBroadcasts() }
    single<AppWidgetUpdater> { NoopAppWidgetUpdater() }
    single<MeshWorkerManager> { NoopMeshWorkerManager() }
    single<MessageQueue> { DesktopMessageQueue(packetRepository = get(), radioController = get(), dispatchers = get()) }
    single<MeshLocationManager> { NoopMeshLocationManager() }
    single<LocationRepository> { NoopLocationRepository() }
    single<MQTTRepository> { NoopMQTTRepository() }
    single<CompassHeadingProvider> { NoopCompassHeadingProvider() }
    single<PhoneLocationProvider> { NoopPhoneLocationProvider() }
    single<MagneticFieldProvider> { NoopMagneticFieldProvider() }

    // Desktop uses the real ApiService implementation (no flavor stub needed)
    single<ApiService> { ApiServiceImpl(client = get()) }

    // Ktor HttpClient for JVM/Desktop (equivalent of CoreNetworkAndroidModule on Android)
    single<HttpClient> {
        HttpClient(Java) {
            install(ContentNegotiation) { json(get<Json>()) }
            install(DefaultRequest) { url(HttpClientDefaults.API_BASE_URL) }
            install(HttpTimeout) {
                requestTimeoutMillis = HttpClientDefaults.TIMEOUT_MS
                connectTimeoutMillis = HttpClientDefaults.TIMEOUT_MS
                socketTimeoutMillis = HttpClientDefaults.TIMEOUT_MS
            }
            install(HttpRequestRetry) {
                retryOnServerErrors(maxRetries = HttpClientDefaults.MAX_RETRIES)
                exponentialDelay()
            }
            if (DesktopBuildConfig.IS_DEBUG) {
                install(Logging) {
                    logger = KermitHttpLogger
                    level = LogLevel.BODY
                }
            }
        }
    }

    // Desktop stubs for data sources that load from Android assets on mobile
    single<FirmwareReleaseJsonDataSource> {
        object : FirmwareReleaseJsonDataSource {
            override fun loadFirmwareReleaseFromJsonAsset() = NetworkFirmwareReleases()
        }
    }
    single<DeviceHardwareJsonDataSource> {
        object : DeviceHardwareJsonDataSource {
            override fun loadDeviceHardwareFromJsonAsset(): List<NetworkDeviceHardware> = emptyList()
        }
    }
    single<BootloaderOtaQuirksJsonDataSource> {
        object : BootloaderOtaQuirksJsonDataSource {
            override fun loadBootloaderOtaQuirksFromJsonAsset(): List<BootloaderOtaQuirk> = emptyList()
        }
    }
}
