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

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.coroutineScope
import org.koin.core.annotation.Module
import org.koin.core.annotation.Named
import org.koin.core.annotation.Single
import org.meshtastic.core.common.di.PROCESS_LIFECYCLE
import org.meshtastic.core.common.util.LocaleChangeNotifier
import org.meshtastic.core.database.DatabaseBackupOperations
import org.meshtastic.core.database.DatabaseProvider
import org.meshtastic.core.database.WebDatabaseBackupService
import org.meshtastic.core.network.repository.SerialDevicePresence
import org.meshtastic.core.network.repository.ServiceDiscovery
import org.meshtastic.core.network.repository.WebSerialDevicePresence
import org.meshtastic.core.network.serial.WebSerialPortRegistry
import org.meshtastic.core.repository.AppWidgetUpdater
import org.meshtastic.core.repository.FileService
import org.meshtastic.core.repository.LocationRepository
import org.meshtastic.core.repository.LocationService
import org.meshtastic.core.repository.LockdownPassphraseStore
import org.meshtastic.core.repository.MagneticFieldProvider
import org.meshtastic.core.repository.MeshLocationManager
import org.meshtastic.core.repository.PhoneLocationProvider
import org.meshtastic.core.repository.PlatformAnalytics
import org.meshtastic.core.repository.ReverseGeocoder
import org.meshtastic.core.repository.SecurityKeyBackupStore
import org.meshtastic.core.service.DeviceAddressChangeHook
import org.meshtastic.core.service.LastBleNodeRecorder
import org.meshtastic.core.service.LocalNetworkAccess
import org.meshtastic.feature.discovery.ai.AlgorithmicSummaryProvider
import org.meshtastic.feature.discovery.ai.DiscoverySummaryAiProvider
import org.meshtastic.feature.docs.ai.AIDocAssistant
import org.meshtastic.feature.docs.ai.KeywordFallbackAssistant
import org.meshtastic.feature.docs.translation.DocTranslationService
import org.meshtastic.feature.docs.translation.NoOpDocTranslator
import org.meshtastic.feature.messaging.translation.MessageTranslationService
import org.meshtastic.feature.messaging.translation.NoOpMessageTranslator
import org.meshtastic.feature.node.compass.CompassHeadingProvider
import org.meshtastic.web.platform.WebCompassHeadingProvider
import org.meshtastic.web.platform.WebFileService
import org.meshtastic.web.platform.WebLocationRepository
import org.meshtastic.web.platform.WebLocationService
import org.meshtastic.web.platform.WebMeshLocationManager
import org.meshtastic.web.platform.WebPhoneLocationProvider
import org.meshtastic.web.stub.NoopAppWidgetUpdater
import org.meshtastic.web.stub.NoopDeviceAddressChangeHook
import org.meshtastic.web.stub.NoopLocalNetworkAccess
import org.meshtastic.web.stub.NoopLocaleChangeNotifier
import org.meshtastic.web.stub.NoopLockdownPassphraseStore
import org.meshtastic.web.stub.NoopMagneticFieldProvider
import org.meshtastic.web.stub.NoopPlatformAnalytics
import org.meshtastic.web.stub.NoopReverseGeocoder
import org.meshtastic.web.stub.NoopSecurityKeyBackupStore
import org.meshtastic.web.stub.NoopServiceDiscovery

/**
 * Platform bindings: browser-backed ones (Geolocation, compass, file pickers, Web Serial hot-plug, database backup) and
 * stubs for what a browser lacks: WorkManager, widgets, declination, mDNS and keystores.
 */
@Suppress("TooManyFunctions")
@Module
class WebStubsModule {

    @Single fun platformAnalytics(): PlatformAnalytics = NoopPlatformAnalytics()

    @Single fun appWidgetUpdater(): AppWidgetUpdater = NoopAppWidgetUpdater()

    @Single
    fun meshLocationManager(locationRepository: LocationRepository): MeshLocationManager =
        WebMeshLocationManager(locationRepository)

    @Single fun locationRepository(): LocationRepository = WebLocationRepository()

    @Single fun locationService(): LocationService = WebLocationService()

    @Single fun compassHeadingProvider(): CompassHeadingProvider = WebCompassHeadingProvider()

    @Single fun phoneLocationProvider(): PhoneLocationProvider = WebPhoneLocationProvider()

    @Single fun magneticFieldProvider(): MagneticFieldProvider = NoopMagneticFieldProvider()

    @Single fun serviceDiscovery(): ServiceDiscovery = NoopServiceDiscovery()

    @Single
    fun serialDevicePresence(
        registry: WebSerialPortRegistry,
        @Named(PROCESS_LIFECYCLE) processLifecycle: Lifecycle,
    ): SerialDevicePresence = WebSerialDevicePresence(registry, processLifecycle.coroutineScope)

    @Single fun securityKeyBackupStore(): SecurityKeyBackupStore = NoopSecurityKeyBackupStore()

    @Single fun lockdownPassphraseStore(): LockdownPassphraseStore = NoopLockdownPassphraseStore()

    @Single fun fileService(): FileService = WebFileService()

    @Single fun localNetworkAccess(): LocalNetworkAccess = NoopLocalNetworkAccess()

    /** The stranded-bootloader recovery it feeds keeps its state in DataStore, which web lacks. */
    @Single fun lastBleNodeRecorder(): LastBleNodeRecorder = LastBleNodeRecorder {}

    @Single fun localeChangeNotifier(): LocaleChangeNotifier = NoopLocaleChangeNotifier()

    @Single fun deviceAddressChangeHook(): DeviceAddressChangeHook = NoopDeviceAddressChangeHook()

    @Single fun reverseGeocoder(): ReverseGeocoder = NoopReverseGeocoder()

    @Single
    fun databaseBackupOperations(provider: DatabaseProvider): DatabaseBackupOperations =
        WebDatabaseBackupService(provider)

    @Single fun messageTranslationService(): MessageTranslationService = NoOpMessageTranslator()

    /** No on-device model in a browser: Chirpy answers from keyword search, and docs show untranslated. */
    @Single fun aiDocAssistant(fallback: KeywordFallbackAssistant): AIDocAssistant = fallback

    @Single fun docTranslationService(): DocTranslationService = NoOpDocTranslator()

    @Single fun discoverySummaryAiProvider(fallback: AlgorithmicSummaryProvider): DiscoverySummaryAiProvider = fallback
}
