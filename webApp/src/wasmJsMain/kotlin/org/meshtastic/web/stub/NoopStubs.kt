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
@file:Suppress("EmptyFunctionBlock", "TooManyFunctions")

package org.meshtastic.web.stub

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import org.meshtastic.core.common.util.LocaleChangeNotifier
import org.meshtastic.core.network.repository.DiscoveredService
import org.meshtastic.core.network.repository.ServiceDiscovery
import org.meshtastic.core.repository.AppWidgetUpdater
import org.meshtastic.core.repository.DataPair
import org.meshtastic.core.repository.GeocodedPlace
import org.meshtastic.core.repository.LockdownPassphraseStore
import org.meshtastic.core.repository.MagneticFieldProvider
import org.meshtastic.core.repository.PlatformAnalytics
import org.meshtastic.core.repository.ReverseGeocoder
import org.meshtastic.core.repository.SecurityKeyBackupStore
import org.meshtastic.core.repository.StoredPassphrase
import org.meshtastic.core.repository.StoredSecurityKeys
import org.meshtastic.core.service.DeviceAddressChangeHook
import org.meshtastic.core.service.LocalNetworkAccess

// No-op implementations for platform interfaces a browser has no counterpart for, shaped like desktop's stubs.

// region Platform / widget stubs: no analytics SDK runs on web, and a tab has no widgets

class NoopPlatformAnalytics : PlatformAnalytics {
    override fun track(event: String, vararg properties: DataPair) {}

    override fun setDeviceAttributes(firmwareVersion: String, model: String) {}

    override val isPlatformServicesAvailable: Boolean = false
}

class NoopAppWidgetUpdater : AppWidgetUpdater {
    override suspend fun updateAll() {}
}

// endregion

// region Service discovery stub: mDNS needs raw sockets, which a browser cannot open

class NoopServiceDiscovery : ServiceDiscovery {
    override val resolvedServices: Flow<List<DiscoveredService>> = flowOf(emptyList())
}

// endregion

// region Secure-storage stubs: origin storage is readable by any script on the page, so keys and passphrases stay out

class NoopSecurityKeyBackupStore : SecurityKeyBackupStore {
    override fun get(nodeNum: Int): StoredSecurityKeys? = null

    override fun all(): Map<Int, StoredSecurityKeys> = emptyMap()

    override fun save(
        nodeNum: Int,
        publicKeyBase64: String,
        privateKeyBase64: String,
        timestamp: Long,
        longName: String?,
    ) {}

    override fun delete(nodeNum: Int) {}
}

class NoopLockdownPassphraseStore : LockdownPassphraseStore {
    override fun getPassphrase(deviceAddress: String): StoredPassphrase? = null

    override fun savePassphrase(
        deviceAddress: String,
        passphrase: String,
        boots: Int,
        hours: Int,
        maxSessionSeconds: Int,
    ) {}

    override fun clearPassphrase(deviceAddress: String) {}
}

// endregion

// region Local-network-access stub: the Android 17 gate does not apply, so access is granted

class NoopLocalNetworkAccess : LocalNetworkAccess {
    override fun isGranted(): Boolean = true
}

// endregion

// region Locale-change stub: a reload already re-reads navigator.language

class NoopLocaleChangeNotifier : LocaleChangeNotifier {
    override val localeChanges: Flow<Unit> = emptyFlow()
}

// endregion

// region Declination stub: Android reads it from the platform's World Magnetic Model, which a browser does not have

class NoopMagneticFieldProvider : MagneticFieldProvider {
    override fun getDeclination(latitude: Double, longitude: Double, altitude: Double, timeMillis: Long): Float = 0f
}

/** The browser has no mesh service to start on an address change. */
class NoopDeviceAddressChangeHook : DeviceAddressChangeHook {
    override fun onDeviceAddressChanged() = Unit
}

/** The browser has no geocoder. */
class NoopReverseGeocoder : ReverseGeocoder {
    override suspend fun place(latitude: Double, longitude: Double): GeocodedPlace? = null
}

// endregion
