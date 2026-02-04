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
package com.meshtastic.android.meshserviceexample

import android.content.Intent
import android.os.Build
import android.os.Parcelable
import android.os.RemoteException
import android.util.Log
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okio.ByteString.Companion.toByteString
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.model.MessageStatus
import org.meshtastic.core.model.MyNodeInfo
import org.meshtastic.core.model.NodeInfo
import org.meshtastic.core.model.Position
import org.meshtastic.core.service.IMeshService
import org.meshtastic.proto.PortNum
import kotlin.random.Random

private const val TAG = "MeshServiceViewModel"

/** ViewModel for MeshServiceExample. Handles interaction with IMeshService AIDL and manages UI state. */
@Suppress("TooManyFunctions")
class MeshServiceViewModel : ViewModel() {

    private var meshService: IMeshService? = null

    private val _myNodeInfo = MutableStateFlow<MyNodeInfo?>(null)
    val myNodeInfo: StateFlow<MyNodeInfo?> = _myNodeInfo.asStateFlow()

    private val _myId = MutableStateFlow<String?>(null)
    val myId: StateFlow<String?> = _myId.asStateFlow()

    private val _nodes = MutableStateFlow<List<NodeInfo>>(emptyList())
    val nodes: StateFlow<List<NodeInfo>> = _nodes.asStateFlow()

    private val _serviceConnectionStatus = MutableStateFlow(false)
    val serviceConnectionStatus: StateFlow<Boolean> = _serviceConnectionStatus.asStateFlow()

    private val _message = MutableStateFlow("")
    val message: StateFlow<String> = _message.asStateFlow()

    private val _connectionState = MutableStateFlow("UNKNOWN")
    val connectionState: StateFlow<String> = _connectionState.asStateFlow()

    fun onServiceConnected(service: IMeshService?) {
        meshService = service
        _serviceConnectionStatus.value = true
        updateAllData()
    }

    fun onServiceDisconnected() {
        meshService = null
        _serviceConnectionStatus.value = false
    }

    private fun updateAllData() {
        requestMyNodeInfo()
        requestNodes()
        updateConnectionState()
        updateMyId()
    }

    fun updateMyId() {
        meshService?.let {
            try {
                _myId.value = it.myId
            } catch (e: RemoteException) {
                Log.e(TAG, "Failed to get MyId", e)
            }
        }
    }

    fun updateConnectionState() {
        meshService?.let {
            try {
                _connectionState.value = it.connectionState() ?: "UNKNOWN"
            } catch (e: RemoteException) {
                Log.e(TAG, "Failed to get connection state", e)
            }
        }
    }

    fun sendMessage(text: String) {
        meshService?.let { service ->
            try {
                val packet =
                    DataPacket(
                        to = DataPacket.ID_BROADCAST,
                        bytes = text.encodeToByteArray().toByteString(),
                        dataType = PortNum.TEXT_MESSAGE_APP.value,
                        from = DataPacket.ID_LOCAL,
                        time = System.currentTimeMillis(),
                        id = service.packetId, // Correctly sync with radio's ID
                        status = MessageStatus.UNKNOWN,
                        hopLimit = 3,
                        channel = 0,
                        wantAck = true,
                    )
                service.send(packet)
                Log.d(TAG, "Message sent successfully, assigned ID: ${packet.id}")
            } catch (e: RemoteException) {
                Log.e(TAG, "Failed to send message", e)
            }
        } ?: Log.w(TAG, "MeshService is not bound, cannot send message")
    }

    fun requestMyNodeInfo() {
        meshService?.let {
            try {
                _myNodeInfo.value = it.myNodeInfo
            } catch (e: RemoteException) {
                Log.e(TAG, "Failed to get MyNodeInfo", e)
            }
        }
    }

    fun requestNodes() {
        meshService?.let {
            try {
                _nodes.value = it.nodes ?: emptyList()
            } catch (e: RemoteException) {
                Log.e(TAG, "Failed to get nodes", e)
            }
        }
    }

    fun startProvideLocation() {
        try {
            meshService?.startProvideLocation()
        } catch (e: RemoteException) {
            Log.e(TAG, "Failed to start providing location", e)
        }
    }

    fun stopProvideLocation() {
        try {
            meshService?.stopProvideLocation()
        } catch (e: RemoteException) {
            Log.e(TAG, "Failed to stop providing location", e)
        }
    }

    fun requestTraceroute(nodeNum: Int) {
        meshService?.let {
            try {
                it.requestTraceroute(Random.nextInt(), nodeNum)
                Log.i(TAG, "Traceroute requested for node $nodeNum")
            } catch (e: RemoteException) {
                Log.e(TAG, "Failed to request traceroute", e)
            }
        }
    }

    fun requestTelemetry(nodeNum: Int) {
        meshService?.let {
            try {
                // DEVICE_METRICS_FIELD_NUMBER = 1
                it.requestTelemetry(Random.nextInt(), nodeNum, 1)
                Log.i(TAG, "Telemetry requested for node $nodeNum")
            } catch (e: RemoteException) {
                Log.e(TAG, "Failed to request telemetry", e)
            }
        }
    }

    fun requestNeighborInfo(nodeNum: Int) {
        meshService?.let {
            try {
                it.requestNeighborInfo(Random.nextInt(), nodeNum)
                Log.i(TAG, "Neighbor info requested for node $nodeNum")
            } catch (e: RemoteException) {
                Log.e(TAG, "Failed to request neighbor info", e)
            }
        }
    }

    fun requestPosition(nodeNum: Int) {
        meshService?.let {
            try {
                it.requestPosition(nodeNum, Position(0.0, 0.0, 0))
                Log.i(TAG, "Position requested for node $nodeNum")
            } catch (e: RemoteException) {
                Log.e(TAG, "Failed to request position", e)
            }
        }
    }

    fun requestUserInfo(nodeNum: Int) {
        meshService?.let {
            try {
                it.requestUserInfo(nodeNum)
                Log.i(TAG, "User info requested for node $nodeNum")
            } catch (e: RemoteException) {
                Log.e(TAG, "Failed to request user info", e)
            }
        }
    }

    fun requestDeviceConnectionStatus(nodeNum: Int) {
        meshService?.let {
            try {
                it.getDeviceConnectionStatus(Random.nextInt(), nodeNum)
                Log.i(TAG, "Device connection status requested for node $nodeNum")
            } catch (e: RemoteException) {
                Log.e(TAG, "Failed to request device connection status", e)
            }
        }
    }

    fun rebootLocalDevice() {
        meshService?.let {
            try {
                it.requestReboot(Random.nextInt(), 0)
                Log.w(TAG, "Local reboot requested!")
            } catch (e: RemoteException) {
                Log.e(TAG, "Failed to request reboot", e)
            }
        }
    }

    fun handleIncomingIntent(intent: Intent) {
        val action = intent.action ?: return
        Log.d(TAG, "Received broadcast: $action")

        when (action) {
            "com.geeksville.mesh.NODE_CHANGE" -> handleNodeChange(intent)
            "com.geeksville.mesh.CONNECTION_CHANGED",
            "com.geeksville.mesh.MESH_CONNECTED",
            "com.geeksville.mesh.MESH_DISCONNECTED",
            -> updateConnectionState()
            "com.geeksville.mesh.MESSAGE_STATUS" -> handleMessageStatus(intent)
            else ->
                if (action.startsWith("com.geeksville.mesh.RECEIVED.")) {
                    handleReceivedPacket(action, intent)
                }
        }
    }

    private fun handleNodeChange(intent: Intent) {
        val nodeInfo = intent.getParcelableCompat("com.geeksville.mesh.NodeInfo", NodeInfo::class.java)
        nodeInfo?.let { ni ->
            Log.d(TAG, "Node updated: ${ni.num}")
            _nodes.value =
                _nodes.value.toMutableList().apply {
                    val index = indexOfFirst { it.num == ni.num }
                    if (index != -1) set(index, ni) else add(ni)
                }
        }
    }

    private fun handleMessageStatus(intent: Intent) {
        val id = intent.getIntExtra("com.geeksville.mesh.PacketId", 0)
        val status = intent.getParcelableCompat("com.geeksville.mesh.Status", MessageStatus::class.java)
        Log.d(TAG, "Message Status for ID $id: $status")
    }

    private fun handleReceivedPacket(action: String, intent: Intent) {
        val packet = intent.getParcelableCompat("com.geeksville.mesh.Payload", DataPacket::class.java) ?: return
        if (packet.dataType == PortNum.TEXT_MESSAGE_APP.value) {
            val receivedText = packet.bytes?.utf8() ?: ""
            _message.value = "From ${packet.from}: $receivedText"
        } else {
            _message.value = "Received port ${action.substringAfterLast(".")} packet"
        }
    }

    private fun <T : Parcelable> Intent.getParcelableCompat(key: String, clazz: Class<T>): T? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(key, clazz)
        } else {
            @Suppress("DEPRECATION")
            getParcelableExtra(key)
        }
}
