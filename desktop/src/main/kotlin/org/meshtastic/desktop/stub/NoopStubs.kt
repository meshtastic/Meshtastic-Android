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
@file:Suppress("EmptyFunctionBlock", "TooManyFunctions")

package org.meshtastic.desktop.stub

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.model.InterfaceId
import org.meshtastic.core.model.MeshActivity
import org.meshtastic.core.model.MessageStatus
import org.meshtastic.core.model.Node
import org.meshtastic.core.network.repository.MQTTRepository
import org.meshtastic.core.repository.AppWidgetUpdater
import org.meshtastic.core.repository.DataPair
import org.meshtastic.core.repository.Location
import org.meshtastic.core.repository.LocationRepository
import org.meshtastic.core.repository.MeshLocationManager
import org.meshtastic.core.repository.MeshServiceNotifications
import org.meshtastic.core.repository.MeshWorkerManager
import org.meshtastic.core.repository.PlatformAnalytics
import org.meshtastic.core.repository.RadioInterfaceService
import org.meshtastic.core.repository.ServiceBroadcasts
import org.meshtastic.proto.ClientNotification
import org.meshtastic.proto.MqttClientProxyMessage
import org.meshtastic.proto.Telemetry
import org.meshtastic.proto.Position as ProtoPosition

/**
 * No-op stub implementations for truly platform-specific interfaces.
 *
 * These stubs exist ONLY for interfaces that have no `commonMain` implementation and require Android-specific APIs
 * (BLE/USB transport, notifications, WorkManager, location services, broadcasts, widgets). All other interfaces use
 * real `commonMain` implementations wired through the generated Koin K2 modules.
 *
 * As real desktop implementations become available (e.g., serial transport, TCP transport), they replace individual
 * stubs in [desktopModule].
 */
private const val TAG = "NoopStub"

private fun logWarn(message: String) {
    Logger.w(tag = TAG) { message }
}

// region Transport / Radio Stubs (Android BLE/USB — no commonMain impl)

class NoopRadioInterfaceService : RadioInterfaceService {
    override val supportedDeviceTypes: List<org.meshtastic.core.model.DeviceType> = emptyList()

    override val connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    override val currentDeviceAddressFlow = MutableStateFlow<String?>(null)

    override fun isMockInterface(): Boolean = false

    override val receivedData = MutableSharedFlow<ByteArray>()
    override val meshActivity = MutableSharedFlow<MeshActivity>()
    override val connectionError = MutableSharedFlow<String>()

    override fun sendToRadio(bytes: ByteArray) {
        logWarn("NoopRadioInterfaceService.sendToRadio(${bytes.size} bytes)")
    }

    override fun connect() {
        logWarn("NoopRadioInterfaceService.connect()")
    }

    override fun getDeviceAddress(): String? = null

    override fun setDeviceAddress(deviceAddr: String?): Boolean = false

    override fun toInterfaceAddress(interfaceId: InterfaceId, rest: String): String = ""

    override fun onConnect() {}

    override fun onDisconnect(isPermanent: Boolean, errorMessage: String?) {}

    override fun handleFromRadio(bytes: ByteArray) {}

    @Suppress("InjectDispatcher")
    override val serviceScope: CoroutineScope = CoroutineScope(SupervisorJob() + kotlinx.coroutines.Dispatchers.Default)
}

// endregion

// region Notification / Platform Stubs (Android-only)

@Suppress("TooManyFunctions")
class NoopMeshServiceNotifications : MeshServiceNotifications {
    override fun clearNotifications() {}

    override fun initChannels() {}

    override fun updateServiceStateNotification(
        state: org.meshtastic.core.model.ConnectionState,
        telemetry: Telemetry?,
    ): Any = Unit

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

    override fun cancelMessageNotification(contactKey: String) {}

    override fun cancelLowBatteryNotification(node: Node) {}

    override fun clearClientNotification(notification: ClientNotification) {}
}

class NoopPlatformAnalytics : PlatformAnalytics {
    override fun track(event: String, vararg properties: DataPair) {}

    override fun setDeviceAttributes(firmwareVersion: String, model: String) {}

    override val isPlatformServicesAvailable: Boolean = false
}

class NoopServiceBroadcasts : ServiceBroadcasts {
    override fun subscribeReceiver(receiverName: String, packageName: String) {}

    override fun broadcastReceivedData(dataPacket: DataPacket) {}

    override fun broadcastConnection() {}

    override fun broadcastNodeChange(node: Node) {}

    override fun broadcastMessageStatus(packetId: Int, status: MessageStatus) {}
}

class NoopAppWidgetUpdater : AppWidgetUpdater {
    override suspend fun updateAll() {}
}

// endregion

// region WorkManager / Location Stubs (Android-only)

class NoopMeshWorkerManager : MeshWorkerManager {
    override fun enqueueSendMessage(packetId: Int) {}
}

class NoopMeshLocationManager : MeshLocationManager {
    override fun start(scope: CoroutineScope, sendPositionFn: (ProtoPosition) -> Unit) {}

    override fun stop() {}
}

class NoopLocationRepository : LocationRepository {
    override val receivingLocationUpdates = MutableStateFlow(false)

    override fun getLocations(): Flow<Location> = emptyFlow()
}

// endregion

// region Network Stubs (MQTT — not yet available on Desktop)

class NoopMQTTRepository : MQTTRepository {
    override fun disconnect() {}

    override val proxyMessageFlow: Flow<MqttClientProxyMessage> = emptyFlow()

    override fun publish(topic: String, data: ByteArray, retained: Boolean) {}
}

// endregion
