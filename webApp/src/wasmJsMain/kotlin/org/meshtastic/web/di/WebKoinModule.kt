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
package org.meshtastic.web.di

// One import per package covers every @Module class in it — Kotlin resolves the right overload by receiver type
// (see desktopApp's DesktopKoinModule.kt for the identical pattern with CoreDatabaseModule/CoreDatabaseNonWebModule).
import org.koin.core.qualifier.named
import org.koin.dsl.module
import org.meshtastic.core.common.BuildConfigProvider
import org.meshtastic.core.common.database.DatabaseManager
import org.meshtastic.core.common.di.PROCESS_LIFECYCLE
import org.meshtastic.core.common.di.ServiceScope
import org.meshtastic.core.common.util.LocaleChangeNotifier
import org.meshtastic.core.data.datasource.BundledAssetReader
import org.meshtastic.core.network.repository.MQTTRepository
import org.meshtastic.core.network.repository.SerialDevicePresence
import org.meshtastic.core.network.repository.ServiceDiscovery
import org.meshtastic.core.repository.AdminController
import org.meshtastic.core.repository.AppWidgetUpdater
import org.meshtastic.core.repository.ConnectionStateProvider
import org.meshtastic.core.repository.FileService
import org.meshtastic.core.repository.LocationRepository
import org.meshtastic.core.repository.LocationService
import org.meshtastic.core.repository.LockdownPassphraseStore
import org.meshtastic.core.repository.MeshLocationManager
import org.meshtastic.core.repository.MeshNotificationManager
import org.meshtastic.core.repository.MeshWorkerManager
import org.meshtastic.core.repository.MessageQueue
import org.meshtastic.core.repository.MessagingController
import org.meshtastic.core.repository.NeighborInfoResponseProvider
import org.meshtastic.core.repository.NodeController
import org.meshtastic.core.repository.NotificationManager
import org.meshtastic.core.repository.PlatformAnalytics
import org.meshtastic.core.repository.QueryController
import org.meshtastic.core.repository.RadioController
import org.meshtastic.core.repository.SecurityKeyBackupStore
import org.meshtastic.core.repository.ServiceRepository
import org.meshtastic.core.repository.ServiceStateWriter
import org.meshtastic.core.repository.TracerouteResponseProvider
import org.meshtastic.core.service.LocalNetworkAccess
import org.meshtastic.core.service.RadioControllerImpl
import org.meshtastic.core.service.ServiceRepositoryImpl
import org.meshtastic.feature.messaging.translation.MessageTranslationService
import org.meshtastic.feature.messaging.translation.NoOpMessageTranslator
import org.meshtastic.feature.node.compass.CompassHeadingProvider
import org.meshtastic.feature.node.compass.MagneticFieldProvider
import org.meshtastic.feature.node.compass.PhoneLocationProvider
import org.meshtastic.web.WebBuildConfig
import org.meshtastic.web.db.WebDatabaseManager
import org.meshtastic.web.lifecycle.webProcessLifecycle
import org.meshtastic.web.radio.WebMessageQueue
import org.meshtastic.web.stub.NoopAppWidgetUpdater
import org.meshtastic.web.stub.NoopCompassHeadingProvider
import org.meshtastic.web.stub.NoopFileService
import org.meshtastic.web.stub.NoopLocalNetworkAccess
import org.meshtastic.web.stub.NoopLocaleChangeNotifier
import org.meshtastic.web.stub.NoopLocationRepository
import org.meshtastic.web.stub.NoopLocationService
import org.meshtastic.web.stub.NoopLockdownPassphraseStore
import org.meshtastic.web.stub.NoopMQTTRepository
import org.meshtastic.web.stub.NoopMagneticFieldProvider
import org.meshtastic.web.stub.NoopMeshLocationManager
import org.meshtastic.web.stub.NoopMeshNotificationManager
import org.meshtastic.web.stub.NoopMeshWorkerManager
import org.meshtastic.web.stub.NoopNotificationManager
import org.meshtastic.web.stub.NoopPhoneLocationProvider
import org.meshtastic.web.stub.NoopPlatformAnalytics
import org.meshtastic.web.stub.NoopSecurityKeyBackupStore
import org.meshtastic.web.stub.NoopSerialDevicePresence
import org.meshtastic.web.stub.NoopServiceDiscovery
import org.meshtastic.core.ble.di.module as coreBleWasmJsModule
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
import org.meshtastic.core.ui.di.module as coreUiModule
import org.meshtastic.feature.connections.di.module as featureConnectionsModule
import org.meshtastic.feature.messaging.di.module as featureMessagingModule
import org.meshtastic.feature.node.di.module as featureNodeModule
import org.meshtastic.feature.settings.di.module as featureSettingsModule

/**
 * Koin module for the Web (wasmJs) target — mirrors `desktopApp`'s `desktopModule()`/`desktopPlatformStubsModule()`
 * shape (the most directly analogous existing precedent: both are "not mobile" hosts that assemble the shared KMP graph
 * plus a handful of platform stubs).
 *
 * Wires in every wasmJs-specific Koin module this whole effort left unregistered, since this is the module meant to
 * wire them in: [CorePrefsWasmJsModule][org.meshtastic.core.prefs.di.CorePrefsWasmJsModule],
 * [CoreDatastoreWasmJsModule][org.meshtastic.core.datastore.di.CoreDatastoreWasmJsModule],
 * [CoreNetworkWasmJsModule][org.meshtastic.core.network.di.CoreNetworkWasmJsModule], and
 * [CoreBleWasmJsModule][org.meshtastic.core.ble.di.CoreBleWasmJsModule]. `core:database`'s `SingleDatabaseProvider` and
 * `core:service`'s `NoopTakServerIntegration` need no separate include: both are `@Single`-annotated classes reached by
 * their own module's existing `@ComponentScan` once this target's compilation includes them (see each class's own KDoc)
 * — unlike the four modules above, which live in a *separate* `@Module` class from their commonMain counterpart and so
 * must be listed explicitly, the same way `CorePrefsAndroidModule` sits beside `CorePrefsModule` in `androidApp`'s own
 * module list. `core:takserver` is deliberately absent — v0 excludes it entirely (`feature:settings`'s own
 * `nonWebMain`/`wasmJsMain` TAK seam already handles that at the feature layer).
 */
fun webModule() = module {
    includes(
        org.meshtastic.core.di.di.CoreDiModule().coreDiModule(),
        org.meshtastic.core.common.di.CoreCommonModule().coreCommonModule(),
        org.meshtastic.core.datastore.di.CoreDatastoreModule().coreDatastoreModule(),
        org.meshtastic.core.datastore.di.CoreDatastoreWasmJsModule().coreDatastoreModule(),
        org.meshtastic.core.prefs.di.CorePrefsModule().corePrefsModule(),
        org.meshtastic.core.prefs.di.CorePrefsWasmJsModule().corePrefsModule(),
        // CoreDatabaseModule only — NOT CoreDatabaseNonWebModule, which lives in nonWebMain and isn't even
        // compiled for this target (androidx.datastore.preferences has no wasmJs variant).
        org.meshtastic.core.database.di.CoreDatabaseModule().coreDatabaseModule(),
        org.meshtastic.core.data.di.CoreDataModule().coreDataModule(),
        org.meshtastic.core.domain.di.CoreDomainModule().coreDomainModule(),
        org.meshtastic.core.repository.di.CoreRepositoryModule().coreRepositoryModule(),
        org.meshtastic.core.network.di.CoreNetworkModule().coreNetworkModule(),
        org.meshtastic.core.network.di.CoreNetworkWasmJsModule().coreNetworkModule(),
        // CoreBleWasmJsModule only — NOT CoreBleModule, which lives in nonWebMain (Kable has no wasmJs target).
        org.meshtastic.core.ble.di.CoreBleWasmJsModule().coreBleWasmJsModule(),
        org.meshtastic.core.ui.di.CoreUiModule().coreUiModule(),
        org.meshtastic.core.service.di.CoreServiceModule().coreServiceModule(),
        org.meshtastic.feature.settings.di.FeatureSettingsModule().featureSettingsModule(),
        org.meshtastic.feature.node.di.FeatureNodeModule().featureNodeModule(),
        org.meshtastic.feature.messaging.di.FeatureMessagingModule().featureMessagingModule(),
        org.meshtastic.feature.connections.di.FeatureConnectionsModule().featureConnectionsModule(),
        webPlatformStubsModule(),
    )
}

/**
 * Platform bindings with no commonMain implementation, or that this v0 pass deliberately defers on web. Shaped exactly
 * like `desktopApp`'s `desktopPlatformStubsModule()`, dropping what v0 doesn't need (map/discovery/docs/
 * firmware/wifi-provision/intro/widget stubs — those feature modules aren't dependencies of this module at all) and
 * replacing what's genuinely platform-specific (`RadioController`'s `DatabaseManager`, `MessageQueue`, process
 * lifecycle, build config).
 */
@Suppress("LongMethod")
private fun webPlatformStubsModule() = module {
    single<ServiceRepository> { ServiceRepositoryImpl() }
    single<ConnectionStateProvider> { get<ServiceRepository>() }
    single<TracerouteResponseProvider> { get<ServiceRepository>() }
    single<NeighborInfoResponseProvider> { get<ServiceRepository>() }
    single<ServiceStateWriter> { get<ServiceRepository>() }
    // RadioTransportFactory: no manual binding — WasmJsRadioTransportFactory (core:network wasmJsMain) is already
    // `@Single(binds = [RadioTransportFactory::class])`, auto-discovered by CoreNetworkModule's ComponentScan.
    single<DatabaseManager> { WebDatabaseManager() }
    single<RadioController> {
        RadioControllerImpl(
            serviceRepository = get(),
            nodeRepository = get(),
            commandSender = get(),
            nodeManager = get(),
            radioInterfaceService = get(),
            locationManager = get(),
            packetRepository = lazy { get() },
            dataHandler = lazy { get() },
            analytics = get(),
            meshPrefs = get(),
            uiPrefs = get(),
            databaseManager = get(),
            notificationManager = get(),
            messageProcessor = lazy { get() },
            radioConfigRepository = get(),
            scope = get<ServiceScope>(),
        )
    }
    single<AdminController> { get<RadioController>() }
    single<MessagingController> { get<RadioController>() }
    single<NodeController> { get<RadioController>() }
    single<QueryController> { get<RadioController>() }
    single<NotificationManager> { NoopNotificationManager() }
    single<MeshNotificationManager> { NoopMeshNotificationManager() }
    single<PlatformAnalytics> { NoopPlatformAnalytics() }
    single<AppWidgetUpdater> { NoopAppWidgetUpdater() }
    single<MeshWorkerManager> { NoopMeshWorkerManager() }
    single<MessageQueue> { WebMessageQueue(packetRepository = get(), radioController = get(), dispatchers = get()) }
    single<MeshLocationManager> { NoopMeshLocationManager() }
    single<LocationRepository> { NoopLocationRepository() }
    // Deliberate override of the real, auto-discovered MQTTRepositoryImpl — same choice desktopApp makes.
    single<MQTTRepository> { NoopMQTTRepository() }
    // mDNS-based; no browser equivalent — see NoopServiceDiscovery's own KDoc.
    single<ServiceDiscovery> { NoopServiceDiscovery() }
    // No OS serial-port hot-plug API on web — see NoopSerialDevicePresence's own KDoc.
    single<SerialDevicePresence> { NoopSerialDevicePresence() }
    // The remaining five: all found the same way (an actual browser load, not a compile-time check) — each
    // interface's own KDoc in NoopStubs.kt explains why the fix is a no-op rather than a real implementation.
    single<SecurityKeyBackupStore> { NoopSecurityKeyBackupStore() }
    single<LockdownPassphraseStore> { NoopLockdownPassphraseStore() }
    single<FileService> { NoopFileService() }
    single<LocalNetworkAccess> { NoopLocalNetworkAccess() }
    single<LocationService> { NoopLocationService() }
    single<LocaleChangeNotifier> { NoopLocaleChangeNotifier() }
    single<CompassHeadingProvider> { NoopCompassHeadingProvider() }
    single<PhoneLocationProvider> { NoopPhoneLocationProvider() }
    single<MagneticFieldProvider> { NoopMagneticFieldProvider() }
    single<MessageTranslationService> { NoOpMessageTranslator() }

    single<BuildConfigProvider> {
        object : BuildConfigProvider {
            override val isDebug: Boolean = WebBuildConfig.IS_DEBUG
            override val applicationId: String = WebBuildConfig.APPLICATION_ID
            override val versionCode: Int = WebBuildConfig.VERSION_CODE
            override val versionName: String = WebBuildConfig.VERSION_NAME
            override val absoluteMinFwVersion: String = WebBuildConfig.ABS_MIN_FW_VERSION
            override val minFwVersion: String = WebBuildConfig.MIN_FW_VERSION
        }
    }

    single(named(PROCESS_LIFECYCLE)) { webProcessLifecycle() }

    // No bundled assets ship with a browser tab; repositories seed from the network instead (same as desktopApp).
    single<BundledAssetReader> { BundledAssetReader { null } }
}
