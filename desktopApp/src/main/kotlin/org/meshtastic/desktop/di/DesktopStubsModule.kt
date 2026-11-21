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

import org.koin.core.annotation.Module
import org.koin.core.annotation.Single
import org.meshtastic.core.repository.AppWidgetUpdater
import org.meshtastic.core.repository.LocationRepository
import org.meshtastic.core.repository.MagneticFieldProvider
import org.meshtastic.core.repository.MeshLocationManager
import org.meshtastic.core.repository.NoopAppWidgetUpdater
import org.meshtastic.core.repository.NoopMagneticFieldProvider
import org.meshtastic.core.repository.NoopPlatformAnalytics
import org.meshtastic.core.repository.NoopReverseGeocoder
import org.meshtastic.core.repository.PhoneLocationProvider
import org.meshtastic.core.repository.PlatformAnalytics
import org.meshtastic.core.repository.ReverseGeocoder
import org.meshtastic.core.service.DeviceAddressChangeHook
import org.meshtastic.core.service.LocalNetworkAccess
import org.meshtastic.core.service.NoopDeviceAddressChangeHook
import org.meshtastic.core.service.NoopLocalNetworkAccess
import org.meshtastic.desktop.stub.NoopCameraPoseProvider
import org.meshtastic.desktop.stub.NoopCompassHeadingProvider
import org.meshtastic.desktop.stub.NoopLocationRepository
import org.meshtastic.desktop.stub.NoopMeshLocationManager
import org.meshtastic.desktop.stub.NoopPhoneLocationProvider
import org.meshtastic.desktop.stub.NoopPhonePressureProvider
import org.meshtastic.feature.map.hud.CameraPoseProvider
import org.meshtastic.feature.map.hud.PhonePressureProvider
import org.meshtastic.feature.node.compass.CompassHeadingProvider

/**
 * Stubs for interfaces whose only real implementation needs Android APIs — WorkManager, widgets, location, sensors and
 * analytics.
 */
@Suppress("TooManyFunctions")
@Module
class DesktopStubsModule {

    @Single fun platformAnalytics(): PlatformAnalytics = NoopPlatformAnalytics()

    @Single fun appWidgetUpdater(): AppWidgetUpdater = NoopAppWidgetUpdater()

    @Single fun meshLocationManager(): MeshLocationManager = NoopMeshLocationManager()

    @Single fun locationRepository(): LocationRepository = NoopLocationRepository()

    @Single fun compassHeadingProvider(): CompassHeadingProvider = NoopCompassHeadingProvider()

    @Single fun phoneLocationProvider(): PhoneLocationProvider = NoopPhoneLocationProvider()

    @Single fun magneticFieldProvider(): MagneticFieldProvider = NoopMagneticFieldProvider()

    @Single fun cameraPoseProvider(): CameraPoseProvider = NoopCameraPoseProvider()

    @Single fun phonePressureProvider(): PhonePressureProvider = NoopPhonePressureProvider()

    @Single fun deviceAddressChangeHook(): DeviceAddressChangeHook = NoopDeviceAddressChangeHook()

    @Single fun localNetworkAccess(): LocalNetworkAccess = NoopLocalNetworkAccess()

    @Single fun reverseGeocoder(): ReverseGeocoder = NoopReverseGeocoder()
}
