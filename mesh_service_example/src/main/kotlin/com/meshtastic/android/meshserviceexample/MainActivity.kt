/*
 * Copyright (c) 2025 Meshtastic LLC
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

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.model.MessageStatus
import org.meshtastic.core.model.NodeInfo
import org.meshtastic.core.service.IMeshService
import org.meshtastic.proto.Portnums

private const val TAG: String = "MeshServiceExample"

class MainActivity : AppCompatActivity() {
    private var meshService: IMeshService? = null
    private var serviceConnection: ServiceConnection? = null
    private var isMeshServiceBound = false

    @RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
    @Suppress("TooGenericExceptionCaught", "LongMethod")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        this.enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val mainTextView = findViewById<TextView>(R.id.mainTextView)
        val statusImageView = findViewById<ImageView>(R.id.statusImageView)

        findViewById<Button>(R.id.sendBtn).setOnClickListener { _ ->
            meshService?.let {
                try {
                    it.send(
                        DataPacket(
                            to = DataPacket.ID_BROADCAST,
                            bytes = "Hello from MeshServiceExample".toByteArray(),
                            dataType = Portnums.PortNum.TEXT_MESSAGE_APP_VALUE,
                            from = DataPacket.ID_LOCAL,
                            time = System.currentTimeMillis(),
                            id = 0,
                            status = MessageStatus.UNKNOWN,
                            hopLimit = 3,
                            channel = 0,
                            wantAck = true,
                        ),
                    )
                    Log.d(TAG, "Message sent successfully")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to send message", e)
                }
            } ?: Log.w(TAG, "MeshService is not bound, cannot send message")
        }

        // Now you can call methods on meshService
        serviceConnection =
            object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                    meshService = IMeshService.Stub.asInterface(service)
                    Log.i(TAG, "Connected to MeshService")
                    isMeshServiceBound = true
                    statusImageView.setImageResource(android.R.color.holo_green_light)
                }

                override fun onServiceDisconnected(name: ComponentName?) {
                    meshService = null
                    isMeshServiceBound = false
                }
            }

        // Handle the received broadcast
        // handle node changed
        // handle position app data
        val meshtasticReceiver: BroadcastReceiver =
            object : BroadcastReceiver() {
                @SuppressLint("SetTextI18n")
                @Suppress("ReturnCount")
                override fun onReceive(context: Context?, intent: Intent?) {
                    if (intent == null) {
                        Log.w(TAG, "Received null intent")
                        return
                    }

                    val action = intent.action

                    if (intent.action == null) {
                        Log.w(TAG, "Received null action")
                        return
                    }

                    Log.d(TAG, "Received broadcast: $action")

                    when (action) {
                        "com.geeksville.mesh.NODE_CHANGE" ->
                            try {
                                val ni = intent.getParcelableExtra("com.geeksville.mesh.NodeInfo", NodeInfo::class.java)
                                Log.d(TAG, "NodeInfo: $ni")
                                mainTextView.text = "NodeInfo: $ni"
                            } catch (e: Exception) {
                                Log.e(TAG, "onReceive: ${e.message}")
                                return
                            }

                        "com.geeksville.mesh.MESSAGE_STATUS" -> {
                            val id = intent.getIntExtra("com.geeksville.mesh.PacketId", 0)
                            val status =
                                intent.getParcelableExtra("com.geeksville.mesh.Status", MessageStatus::class.java)
                            Log.d(TAG, "Message Status ID: $id Status: $status")
                        }

                        "com.geeksville.mesh.MESH_CONNECTED" -> {
                            val extraConnected = intent.getStringExtra("com.geeksville.mesh.Connected")
                            val connected = extraConnected.equals("connected", ignoreCase = true)
                            Log.d(TAG, "Received ACTION_MESH_CONNECTED: $extraConnected")
                            if (connected) {
                                statusImageView.setImageResource(android.R.color.holo_green_light)
                            }
                        }

                        "com.geeksville.mesh.MESH_DISCONNECTED" -> {
                            val extraConnected = intent.getStringExtra("com.geeksville.mesh.Disconnected")
                            val disconnected = extraConnected.equals("disconnected", ignoreCase = true)
                            Log.d(TAG, "Received ACTION_MESH_DISCONNECTED: $extraConnected")
                            if (disconnected) {
                                statusImageView.setImageResource(android.R.color.holo_red_light)
                            }
                        }

                        "com.geeksville.mesh.RECEIVED.POSITION_APP" -> {
                            try {
                                val ni = intent.getParcelableExtra("com.geeksville.mesh.NodeInfo", NodeInfo::class.java)
                                Log.d(TAG, "Position App NodeInfo: $ni")
                                mainTextView.text = "Position App NodeInfo: $ni"
                            } catch (e: Exception) {
                                Log.e(TAG, "onReceive: $e")
                                return
                            }
                        }

                        else -> Log.w(TAG, "Unknown action: $action")
                    }
                }
            }

        val filter =
            IntentFilter().apply {
                addAction("com.geeksville.mesh.NODE_CHANGE")
                addAction("com.geeksville.mesh.RECEIVED.NODEINFO_APP")
                addAction("com.geeksville.mesh.RECEIVED.POSITION_APP")
                addAction("com.geeksville.mesh.MESH_CONNECTED")
                addAction("com.geeksville.mesh.MESH_DISCONNECTED")
            }
        registerReceiver(meshtasticReceiver, filter, RECEIVER_NOT_EXPORTED)
        Log.d(TAG, "Registered meshtasticPacketReceiver")

        while (!bindMeshService()) {
            try {
                @Suppress("MagicNumber")
                Thread.sleep(1_000)
            } catch (e: InterruptedException) {
                Log.e(TAG, "Binding interrupted", e)
                break
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unbindMeshService()
    }

    private fun bindMeshService(): Boolean {
        try {
            Log.i(TAG, "Attempting to bind to Mesh Service...")
            val intent = Intent("com.geeksville.mesh.Service")
            intent.setClassName("com.geeksville.mesh", "com.geeksville.mesh.service.MeshService")
            return bindService(intent, serviceConnection!!, BIND_AUTO_CREATE)
        } catch (e: java.lang.Exception) {
            Log.e(TAG, "Failed to bind", e)
        }
        return false
    }

    private fun unbindMeshService() {
        if (isMeshServiceBound) {
            try {
                unbindService(serviceConnection!!)
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "MeshService not registered or already unbound: " + e.message)
            }
            isMeshServiceBound = false
            meshService = null
        }
    }
}
