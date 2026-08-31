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
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.model.Node
import org.meshtastic.core.network.repository.MQTTRepository
import org.meshtastic.core.repository.AppWidgetUpdater
import org.meshtastic.core.repository.DataPair
import org.meshtastic.core.repository.Location
import org.meshtastic.core.repository.LocationRepository
import org.meshtastic.core.repository.MeshLocationManager
import org.meshtastic.core.repository.MeshNotificationManager
import org.meshtastic.core.repository.MeshWorkerManager
import org.meshtastic.core.repository.Notification
import org.meshtastic.core.repository.NotificationManager
import org.meshtastic.core.repository.PersistedPacketId
import org.meshtastic.core.repository.PlatformAnalytics
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
