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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import okio.BufferedSink
import okio.BufferedSource
import org.meshtastic.core.common.util.CommonUri
import org.meshtastic.core.common.util.LocaleChangeNotifier
import org.meshtastic.core.database.DatabaseBackupOperations
import org.meshtastic.core.database.RestoreStageResult
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.model.FirmwareUpdateNotice
import org.meshtastic.core.model.MeshBeaconOffer
import org.meshtastic.core.model.Node
import org.meshtastic.core.network.repository.DiscoveredService
import org.meshtastic.core.network.repository.SerialDevicePresence
import org.meshtastic.core.network.repository.ServiceDiscovery
import org.meshtastic.core.repository.AppWidgetUpdater
import org.meshtastic.core.repository.DataPair
import org.meshtastic.core.repository.FileService
import org.meshtastic.core.repository.GeocodedPlace
import org.meshtastic.core.repository.Location
import org.meshtastic.core.repository.LocationRepository
import org.meshtastic.core.repository.LocationService
import org.meshtastic.core.repository.LockdownPassphraseStore
import org.meshtastic.core.repository.MagneticFieldProvider
import org.meshtastic.core.repository.MeshLocationManager
import org.meshtastic.core.repository.MeshNotificationManager
import org.meshtastic.core.repository.MeshWorkerManager
import org.meshtastic.core.repository.Notification
import org.meshtastic.core.repository.NotificationManager
import org.meshtastic.core.repository.PersistedPacketId
import org.meshtastic.core.repository.PhoneLocationProvider
import org.meshtastic.core.repository.PhoneLocationState
import org.meshtastic.core.repository.PlatformAnalytics
import org.meshtastic.core.repository.ReverseGeocoder
import org.meshtastic.core.repository.SecurityKeyBackupStore
import org.meshtastic.core.repository.StoredPassphrase
import org.meshtastic.core.repository.StoredSecurityKeys
import org.meshtastic.core.service.DeviceAddressChangeHook
import org.meshtastic.core.service.LocalNetworkAccess
import org.meshtastic.feature.node.compass.CompassHeadingProvider
import org.meshtastic.feature.node.compass.HeadingState
import org.meshtastic.proto.ClientNotification
import org.meshtastic.proto.Telemetry

/** No-op implementations for platform interfaces a browser has no counterpart for yet, shaped like desktop's stubs. */
private const val TAG = "WebNoopStub"

// region Notification stubs: the browser Notification API is not wired yet

class NoopNotificationManager : NotificationManager {
    override suspend fun dispatch(notification: Notification): Boolean = false

    override fun cancel(id: Int) {}

    override fun cancelAll() {}
}

class NoopMeshNotificationManager : MeshNotificationManager {
    override fun clearNotifications() {}

    override fun initChannels() {}

    override fun updateServiceStateNotification(state: ConnectionState, telemetry: Telemetry?) {}

    override suspend fun updateMessageNotification(
        contactKey: String,
        name: String,
        message: String,
        isBroadcast: Boolean,
        channelName: String?,
        isSilent: Boolean,
    ) {}

    override suspend fun updateWaypointNotification(
        contactKey: String,
        name: String,
        message: String,
        waypointId: Int,
        isSilent: Boolean,
    ) {}

    override suspend fun updateReactionNotification(
        contactKey: String,
        name: String,
        emoji: String,
        isBroadcast: Boolean,
        channelName: String?,
        isSilent: Boolean,
    ) {}

    override suspend fun showAlertNotification(contactKey: String, name: String, alert: String) {}

    override suspend fun showMeshBeaconNotification(offer: MeshBeaconOffer) {}

    override suspend fun showNewNodeSeenNotification(node: Node, title: String) {}

    override fun cancelNewNodeNotification(nodeNum: Int) {}

    override suspend fun notifyLowBattery(node: Node, isRemote: Boolean) {}

    override fun cancelLowBatteryNotification(nodeNum: Int) {}

    override suspend fun showClientNotification(
        clientNotification: ClientNotification,
        title: String,
        severity: Notification.Type,
    ) {}

    override fun clearClientNotification(clientNotification: ClientNotification) {}

    override suspend fun showFirmwareUpdateNotification(notice: FirmwareUpdateNotice): Boolean = false

    override suspend fun showReconnectBlockedNotification(title: String, message: String): Boolean = false

    override suspend fun cancelMessageNotification(contactKey: String) {}
}

// endregion

// region Platform / widget / worker stubs (Android-only concepts)

class NoopPlatformAnalytics : PlatformAnalytics {
    override fun track(event: String, vararg properties: DataPair) {}

    override fun setDeviceAttributes(firmwareVersion: String, model: String) {}

    override val isPlatformServicesAvailable: Boolean = false
}

class NoopAppWidgetUpdater : AppWidgetUpdater {
    override suspend fun updateAll() {}
}

class NoopMeshWorkerManager : MeshWorkerManager {
    override fun enqueueSendMessage(persistedId: PersistedPacketId) {}
}

// endregion

// region Location stubs: navigator.geolocation is not wired yet

class NoopMeshLocationManager : MeshLocationManager {
    override fun start(
        scope: kotlinx.coroutines.CoroutineScope,
        sendPositionFn: suspend (org.meshtastic.proto.Position) -> Unit,
    ) {}

    override fun restart() {}

    override fun stop() {}
}

class NoopLocationRepository : LocationRepository {
    override val receivingLocationUpdates = MutableStateFlow(false)

    override fun getLocations(): Flow<Location> = emptyFlow()
}

// endregion

// region Service discovery stub: mDNS needs raw sockets, which a browser cannot open

class NoopServiceDiscovery : ServiceDiscovery {
    override val resolvedServices: Flow<List<DiscoveredService>> = flowOf(emptyList())
}

// endregion

// region Serial device presence stub: Web Serial hot-plug is not wired yet

class NoopSerialDevicePresence : SerialDevicePresence {
    override val deviceKeys: StateFlow<Set<String>> = MutableStateFlow(emptySet())
}

// endregion

// region Secure-storage stubs: no browser keystore is wired yet

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

// region File I/O stub: no browser file picker is wired yet

class NoopFileService : FileService {
    override suspend fun write(uri: CommonUri, block: suspend (BufferedSink) -> Unit): Boolean = false

    override suspend fun read(uri: CommonUri, block: suspend (BufferedSource) -> Unit): Boolean = false
}

// endregion

// region Local-network-access stub: the Android 17 gate does not apply, so access is granted

class NoopLocalNetworkAccess : LocalNetworkAccess {
    override fun isGranted(): Boolean = true
}

// endregion

// region Location-service stub: navigator.geolocation is not wired yet

class NoopLocationService : LocationService {
    override suspend fun getCurrentLocation(): Location? = null
}

// endregion

// region Locale-change stub: a reload already re-reads navigator.language

class NoopLocaleChangeNotifier : LocaleChangeNotifier {
    override val localeChanges: Flow<Unit> = emptyFlow()
}

// endregion

// region Compass/GPS stubs: no sensor API is wired, as on desktop

class NoopCompassHeadingProvider : CompassHeadingProvider {
    override fun headingUpdates(): Flow<HeadingState> = flowOf(HeadingState(hasSensor = false))
}

class NoopPhoneLocationProvider : PhoneLocationProvider {
    override fun locationUpdates(): Flow<PhoneLocationState> =
        flowOf(PhoneLocationState(permissionGranted = false, providerEnabled = false))
}

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

/** The web database is not a file the browser can back up yet, so there is nothing to export or restore. */
class NoopDatabaseBackupOperations : DatabaseBackupOperations {
    override suspend fun backupTargetName(): String? = null

    override suspend fun export(dbName: String, sink: BufferedSink): Long = 0

    override suspend fun stageRestore(dbName: String, source: BufferedSource): RestoreStageResult =
        RestoreStageResult.Failed(UnsupportedOperationException("database restore is not available on web"))
}

// endregion
