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
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.model.Node
import org.meshtastic.core.network.repository.DiscoveredService
import org.meshtastic.core.network.repository.MQTTRepository
import org.meshtastic.core.network.repository.SerialDevicePresence
import org.meshtastic.core.network.repository.ServiceDiscovery
import org.meshtastic.core.repository.AppWidgetUpdater
import org.meshtastic.core.repository.DataPair
import org.meshtastic.core.repository.FileService
import org.meshtastic.core.repository.Location
import org.meshtastic.core.repository.LocationRepository
import org.meshtastic.core.repository.LocationService
import org.meshtastic.core.repository.LockdownPassphraseStore
import org.meshtastic.core.repository.MeshLocationManager
import org.meshtastic.core.repository.MeshNotificationManager
import org.meshtastic.core.repository.MeshWorkerManager
import org.meshtastic.core.repository.Notification
import org.meshtastic.core.repository.NotificationManager
import org.meshtastic.core.repository.PersistedPacketId
import org.meshtastic.core.repository.PlatformAnalytics
import org.meshtastic.core.repository.SecurityKeyBackupStore
import org.meshtastic.core.repository.StoredPassphrase
import org.meshtastic.core.repository.StoredSecurityKeys
import org.meshtastic.core.service.LocalNetworkAccess
import org.meshtastic.feature.node.compass.CompassHeadingProvider
import org.meshtastic.feature.node.compass.HeadingState
import org.meshtastic.feature.node.compass.MagneticFieldProvider
import org.meshtastic.feature.node.compass.PhoneLocationProvider
import org.meshtastic.feature.node.compass.PhoneLocationState
import org.meshtastic.proto.ClientNotification
import org.meshtastic.proto.MqttClientProxyMessage
import org.meshtastic.proto.Telemetry
import org.meshtastic.mqtt.ConnectionState as MqttConnectionState

/**
 * No-op stub implementations for platform-specific interfaces with no commonMain implementation, or that this v0 pass
 * deliberately defers on web. Mirrors desktopApp's own `stub/NoopStubs.kt`/`CompassStubs.kt` — same interfaces, same
 * "no sensor/OS integration on this platform" reasoning, just no OS calls to make since web has none of these either.
 */
private const val TAG = "WebNoopStub"

// region Notification stubs — browser Notification API exists but is deliberately deferred (permission prompts,
// service-worker plumbing); real integration is a future pass, not this v0 slice.

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

    override fun showAlertNotification(contactKey: String, name: String, alert: String) {}

    override fun showNewNodeSeenNotification(node: Node) {}

    override fun showOrUpdateLowBatteryNotification(node: Node, isRemote: Boolean) {}

    override fun showClientNotification(clientNotification: ClientNotification) {}

    override suspend fun cancelMessageNotification(contactKey: String) {}

    override fun cancelLowBatteryNotification(node: Node) {}

    override fun clearClientNotification(notification: ClientNotification) {}
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

// region Location stubs — a real implementation would wrap navigator.geolocation; deferred, matching desktop's own
// "no consumer needs real location on web yet" posture.

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

// region Service discovery stub — JvmServiceDiscovery/AndroidServiceDiscovery are both mDNS (JmDNS/NsdManager), a
// raw-socket OS capability with no browser equivalent (no stable web mDNS-scanning API exists); permanent
// impossibility, not a deferred feature, same reasoning as core:service's NoopTakServerIntegration. Found via an
// actual browser load of webApp (NoDefinitionFoundException for ServiceDiscovery, since JvmServiceDiscovery's
// @Single lives in core:network's jvmMain and is never compiled for wasmJs) -- not caught by any compile-time
// check, since NetworkRepositoryImpl's constructor dependency is satisfied by Koin reflection, not an import.

class NoopServiceDiscovery : ServiceDiscovery {
    override val resolvedServices: Flow<List<DiscoveredService>> = flowOf(emptyList())
}

// endregion

// region Serial device presence stub — same JVM/Desktop precedent (JvmSerialDevicePresence's own KDoc: "platforms
// without hot-plug observation... return a perpetually-empty set"), just with no OS serial-port API to poll at all.
// Web Serial exists but hot-plug detection via it is a future pass, not this v0 slice. Found the same way as
// NoopServiceDiscovery above: an actual browser load, not a compile-time check (SharedRadioInterfaceService's
// constructor dependency is satisfied by Koin reflection).

class NoopSerialDevicePresence : SerialDevicePresence {
    override val deviceKeys: StateFlow<Set<String>> = MutableStateFlow(emptySet())
}

// endregion

// region Secure-storage stubs — EncryptedSharedPreferences (Android) / Keychain (iOS) have no browser analogue in
// this v0 pass; a real implementation would need to weigh Web Crypto + IndexedDB against the same "not actually
// encrypted at rest in an extension-readable origin" caveat every browser secrets story carries. Deferred, not
// permanently impossible (unlike TAK/serial/mDNS) — found the same way as the two stubs above.

class NoopSecurityKeyBackupStore : SecurityKeyBackupStore {
    override fun get(nodeNum: Int): StoredSecurityKeys? = null

    override fun save(nodeNum: Int, publicKeyBase64: String, privateKeyBase64: String, timestamp: Long) {}

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

// region File I/O stub — a real implementation would use the File System Access API (limited browser support) or
// download/upload via <input type=file>/blob URLs; deferred, not permanently impossible, same posture as the
// secure-storage stubs above.

class NoopFileService : FileService {
    override suspend fun write(uri: CommonUri, block: suspend (BufferedSink) -> Unit): Boolean = false

    override suspend fun read(uri: CommonUri, block: suspend (BufferedSource) -> Unit): Boolean = false
}

// endregion

// region Local-network-access stub — the Android-17-specific ACCESS_LOCAL_NETWORK gate this interface exists for
// has no web equivalent; per the interface's own doc ("granted-by-construction everywhere the concept does not
// apply"), the correct answer is true, not false — TCP is rejected earlier anyway, by WasmJsRadioTransportFactory
// itself (browsers cannot open raw sockets at all, a permanent impossibility, not this gate's concern).

class NoopLocalNetworkAccess : LocalNetworkAccess {
    override fun isGranted(): Boolean = true
}

// endregion

// region Location-service stub — one-off "get current location" request; a real implementation would wrap
// navigator.geolocation.getCurrentPosition, same deferred posture as NoopLocationRepository above (no consumer
// needs it wired up yet).

class NoopLocationService : LocationService {
    override suspend fun getCurrentLocation(): Location? = null
}

// endregion

// region Locale-change stub — a real implementation would listen for the `languagechange` window event; deferred,
// not permanently impossible. Never emitting is honest, not a lie: LocaleUnitsProvider's contract only promises a
// value derived from *this* flow changes when the OS locale changes, and a full page reload already re-reads
// navigator.language fresh regardless.

class NoopLocaleChangeNotifier : LocaleChangeNotifier {
    override val localeChanges: Flow<Unit> = emptyFlow()
}

// endregion

// region MQTT stub — MQTTRepositoryImpl (core:network, @Single) is already auto-discovered for wasmJs via
// CoreNetworkModule's ComponentScan; overriding it here mirrors desktopApp's own deliberate override (MQTT proxying
// over a phone/desktop's own network stack has no obvious web analogue yet — same deferred posture, not a regression).

class NoopMQTTRepository : MQTTRepository {
    override fun disconnect() {}

    override val proxyMessageFlow: Flow<MqttClientProxyMessage> = emptyFlow()

    override fun publish(topic: String, data: ByteArray, retained: Boolean) {}

    override val connectionState = MutableStateFlow<MqttConnectionState>(MqttConnectionState.Disconnected.Idle)
}

// endregion

// region Compass/GPS stubs — browser has navigator.geolocation but no compass/magnetometer API with broad support;
// deferred, matching desktop's identical "no sensor" posture.

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

// endregion
