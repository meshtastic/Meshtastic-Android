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

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import org.meshtastic.core.service.IMeshService

private const val TAG: String = "MeshServiceExample"

/** MainActivity for the MeshServiceExample application. */
class MainActivity : ComponentActivity() {

    private var meshService: IMeshService? = null
    private var isMeshServiceBound = false

    private val viewModel: MeshServiceViewModel by viewModels()

    private val serviceConnection =
        object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                meshService = IMeshService.Stub.asInterface(service)
                Log.i(TAG, "Connected to MeshService")
                isMeshServiceBound = true
                viewModel.onServiceConnected(meshService)
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                meshService = null
                isMeshServiceBound = false
                viewModel.onServiceDisconnected()
            }
        }

    private val meshtasticReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                Log.d(TAG, "BroadcastReceiver onReceive: ${intent?.action}")
                intent?.let { viewModel.handleIncomingIntent(it) }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        bindMeshService()

        val intentFilter =
            IntentFilter().apply {
                addAction("com.geeksville.mesh.NODE_CHANGE")
                addAction("com.geeksville.mesh.CONNECTION_CHANGED")
                addAction("com.geeksville.mesh.MESH_CONNECTED")
                addAction("com.geeksville.mesh.MESH_DISCONNECTED")
                addAction("com.geeksville.mesh.MESSAGE_STATUS")
                addAction("com.geeksville.mesh.RECEIVED.TEXT_MESSAGE_APP")
                addAction("com.geeksville.mesh.RECEIVED.POSITION_APP")
                addAction("com.geeksville.mesh.RECEIVED.TELEMETRY_APP")
                addAction("com.geeksville.mesh.RECEIVED.NODEINFO_APP")
            }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(meshtasticReceiver, intentFilter, RECEIVER_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(meshtasticReceiver, intentFilter)
        }

        setContent {
            ExampleTheme {
                MainScreen(viewModel)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(meshtasticReceiver)
        unbindMeshService()
    }

    private fun bindMeshService() {
        try {
            Log.i(TAG, "Attempting to bind to Mesh Service...")
            val intent = Intent("com.geeksville.mesh.Service")

            val resolveInfo =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    packageManager.queryIntentServices(intent, PackageManager.ResolveInfoFlags.of(0))
                } else {
                    @Suppress("DEPRECATION")
                    packageManager.queryIntentServices(intent, 0)
                }

            if (resolveInfo.isNotEmpty()) {
                val serviceInfo = resolveInfo[0].serviceInfo
                intent.setClassName(serviceInfo.packageName, serviceInfo.name)
                Log.i(TAG, "Found service in package: ${serviceInfo.packageName}")
            } else {
                Log.w(TAG, "No service found for action com.geeksville.mesh.Service. Falling back to default.")
                intent.setClassName("com.geeksville.mesh", "com.geeksville.mesh.service.MeshService")
            }

            val success = bindService(intent, serviceConnection, BIND_AUTO_CREATE)
            if (!success) {
                Log.e(TAG, "bindService returned false")
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException while binding: ${e.message}")
        }
    }

    private fun unbindMeshService() {
        if (isMeshServiceBound) {
            try {
                unbindService(serviceConnection)
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "MeshService not registered or already unbound: ${e.message}")
            }
            isMeshServiceBound = false
            meshService = null
        }
    }
}

@Composable
fun ExampleTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> darkColorScheme()
        else -> lightColorScheme()
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
