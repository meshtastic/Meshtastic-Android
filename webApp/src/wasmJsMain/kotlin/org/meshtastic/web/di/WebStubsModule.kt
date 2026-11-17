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

import org.koin.core.annotation.Module
import org.koin.core.annotation.Single
import org.meshtastic.core.common.util.LocaleChangeNotifier
import org.meshtastic.core.database.DatabaseBackupOperations
import org.meshtastic.core.network.repository.SerialDevicePresence
import org.meshtastic.core.network.repository.ServiceDiscovery
import org.meshtastic.core.repository.AppWidgetUpdater
import org.meshtastic.core.repository.FileService
import org.meshtastic.core.repository.LocationRepository
import org.meshtastic.core.repository.LocationService
import org.meshtastic.core.repository.LockdownPassphraseStore
import org.meshtastic.core.repository.MagneticFieldProvider
import org.meshtastic.core.repository.MeshLocationManager
import org.meshtastic.core.repository.MeshWorkerManager
import org.meshtastic.core.repository.PhoneLocationProvider
import org.meshtastic.core.repository.PlatformAnalytics
import org.meshtastic.core.repository.ReverseGeocoder
import org.meshtastic.core.repository.SecurityKeyBackupStore
import org.meshtastic.core.service.DeviceAddressChangeHook
import org.meshtastic.core.service.LastBleNodeRecorder
import org.meshtastic.core.service.LocalNetworkAccess
import org.meshtastic.feature.messaging.translation.MessageTranslationService
import org.meshtastic.feature.messaging.translation.NoOpMessageTranslator
import org.meshtastic.feature.node.compass.CompassHeadingProvider
import org.meshtastic.web.stub.NoopAppWidgetUpdater
import org.meshtastic.web.stub.NoopCompassHeadingProvider
import org.meshtastic.web.stub.NoopDatabaseBackupOperations
import org.meshtastic.web.stub.NoopDeviceAddressChangeHook
import org.meshtastic.web.stub.NoopFileService
import org.meshtastic.web.stub.NoopLocalNetworkAccess
import org.meshtastic.web.stub.NoopLocaleChangeNotifier
import org.meshtastic.web.stub.NoopLocationRepository
import org.meshtastic.web.stub.NoopLocationService
import org.meshtastic.web.stub.NoopLockdownPassphraseStore
import org.meshtastic.web.stub.NoopMagneticFieldProvider
import org.meshtastic.web.stub.NoopMeshLocationManager
import org.meshtastic.web.stub.NoopMeshWorkerManager
import org.meshtastic.web.stub.NoopPhoneLocationProvider
import org.meshtastic.web.stub.NoopPlatformAnalytics
import org.meshtastic.web.stub.NoopReverseGeocoder
import org.meshtastic.web.stub.NoopSecurityKeyBackupStore
import org.meshtastic.web.stub.NoopSerialDevicePresence
import org.meshtastic.web.stub.NoopServiceDiscovery

/**
 * Stubs for interfaces whose real implementations need platform APIs a browser lacks or that web has not wired yet:
 * WorkManager, widgets, sensors, mDNS, OS serial hot-plug, keystores and file pickers.
 */
@Suppress("TooManyFunctions")
@Module
class WebStubsModule {

    @Single fun platformAnalytics(): PlatformAnalytics = NoopPlatformAnalytics()

    @Single fun appWidgetUpdater(): AppWidgetUpdater = NoopAppWidgetUpdater()

    @Single fun meshWorkerManager(): MeshWorkerManager = NoopMeshWorkerManager()

    @Single fun meshLocationManager(): MeshLocationManager = NoopMeshLocationManager()

    @Single fun locationRepository(): LocationRepository = NoopLocationRepository()

    @Single fun locationService(): LocationService = NoopLocationService()

    @Single fun compassHeadingProvider(): CompassHeadingProvider = NoopCompassHeadingProvider()

    @Single fun phoneLocationProvider(): PhoneLocationProvider = NoopPhoneLocationProvider()

    @Single fun magneticFieldProvider(): MagneticFieldProvider = NoopMagneticFieldProvider()

    @Single fun serviceDiscovery(): ServiceDiscovery = NoopServiceDiscovery()

    @Single fun serialDevicePresence(): SerialDevicePresence = NoopSerialDevicePresence()

    @Single fun securityKeyBackupStore(): SecurityKeyBackupStore = NoopSecurityKeyBackupStore()

    @Single fun lockdownPassphraseStore(): LockdownPassphraseStore = NoopLockdownPassphraseStore()

    @Single fun fileService(): FileService = NoopFileService()

    @Single fun localNetworkAccess(): LocalNetworkAccess = NoopLocalNetworkAccess()

    /** The stranded-bootloader recovery it feeds keeps its state in DataStore, which web lacks. */
    @Single fun lastBleNodeRecorder(): LastBleNodeRecorder = LastBleNodeRecorder {}

    @Single fun localeChangeNotifier(): LocaleChangeNotifier = NoopLocaleChangeNotifier()

    @Single fun deviceAddressChangeHook(): DeviceAddressChangeHook = NoopDeviceAddressChangeHook()

    @Single fun reverseGeocoder(): ReverseGeocoder = NoopReverseGeocoder()

    @Single fun databaseBackupOperations(): DatabaseBackupOperations = NoopDatabaseBackupOperations()

    @Single fun messageTranslationService(): MessageTranslationService = NoOpMessageTranslator()
}
