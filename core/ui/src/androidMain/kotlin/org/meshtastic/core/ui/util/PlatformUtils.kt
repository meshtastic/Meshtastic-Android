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
@file:Suppress("TooManyFunctions")

package org.meshtastic.core.ui.util

import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import co.touchlab.kermit.Logger
import com.eygraber.uri.toAndroidUri
import com.eygraber.uri.toKmpUri
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.meshtastic.core.common.gpsDisabled
import org.meshtastic.core.common.util.CommonUri
import org.meshtastic.core.common.util.ioDispatcher
import java.net.URLEncoder

@Composable
actual fun rememberOpenNfcSettings(): () -> Unit {
    val context = LocalContext.current
    return remember(context) {
        {
            val intent = Intent(Settings.ACTION_NFC_SETTINGS)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
        }
    }
}

@Composable
actual fun rememberShowToast(): suspend (String) -> Unit {
    val context = LocalContext.current
    return remember(context) { { text -> context.showToast(text) } }
}

@Composable
actual fun rememberShowToastResource(): suspend (StringResource) -> Unit {
    val context = LocalContext.current
    return remember(context) { { stringResource -> context.showToast(getString(stringResource)) } }
}

@Composable
actual fun rememberOpenMap(): (latitude: Double, longitude: Double, label: String) -> Unit {
    val context = LocalContext.current
    return remember(context) {
        { lat, lon, label ->
            val encodedLabel = URLEncoder.encode(label, "utf-8")
            val uri = "geo:0,0?q=$lat,$lon&z=17&label=$encodedLabel".toUri()
            val intent = Intent(Intent.ACTION_VIEW, uri).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }

            try {
                if (intent.resolveActivity(context.packageManager) != null) {
                    context.startActivity(intent)
                }
            } catch (ex: ActivityNotFoundException) {
                Logger.d { "Failed to open geo intent: $ex" }
            }
        }
    }
}

@Composable
actual fun rememberOpenUrl(): (url: String) -> Unit {
    val context = LocalContext.current
    return remember(context) {
        { url ->
            try {
                val intent = Intent(Intent.ACTION_VIEW, url.toUri()).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                context.startActivity(intent)
            } catch (ex: ActivityNotFoundException) {
                Logger.d { "Failed to open URL intent: $ex" }
            }
        }
    }
}

@Composable
@Suppress("Wrapping")
actual fun rememberSaveFileLauncher(
    onUriReceived: (org.meshtastic.core.common.util.CommonUri) -> Unit,
): (defaultFilename: String, mimeType: String) -> Unit {
    val launcher =
        androidx.activity.compose.rememberLauncherForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult(),
        ) { result ->
            if (result.resultCode == android.app.Activity.RESULT_OK) {
                result.data?.data?.let { uri -> onUriReceived(uri.toKmpUri()) }
            }
        }

    return remember(launcher) {
        { defaultFilename, mimeType ->
            val intent =
                Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = mimeType
                    putExtra(Intent.EXTRA_TITLE, defaultFilename)
                }
            launcher.launch(intent)
        }
    }
}

@Composable
actual fun rememberOpenFileLauncher(onUriReceived: (CommonUri?) -> Unit): (mimeType: String) -> Unit {
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            onUriReceived(uri?.let { it.toKmpUri() })
        }
    return remember(launcher) { { mimeType -> launcher.launch(mimeType) } }
}

@Suppress("Wrapping")
@Composable
actual fun rememberReadTextFromUri(): suspend (uri: CommonUri, maxChars: Int) -> String? {
    val context = LocalContext.current
    return remember(context) {
        { uri, maxChars ->
            withContext(ioDispatcher) {
                @Suppress("TooGenericExceptionCaught")
                try {
                    val androidUri = uri.toAndroidUri()
                    context.contentResolver.openInputStream(androidUri)?.use { stream ->
                        stream.bufferedReader().use { reader ->
                            val buffer = CharArray(maxChars)
                            val read = reader.read(buffer)
                            if (read > 0) String(buffer, 0, read) else null
                        }
                    }
                } catch (e: Exception) {
                    Logger.e(e) { "Failed to read text from URI: $uri" }
                    null
                }
            }
        }
    }
}

@Composable
actual fun KeepScreenOn(enabled: Boolean) {
    val view = LocalView.current
    DisposableEffect(enabled) {
        if (enabled) {
            view.keepScreenOn = true
        }
        onDispose {
            if (enabled) {
                view.keepScreenOn = false
            }
        }
    }
}

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
    BackHandler(enabled = enabled, onBack = onBack)
}

@Composable
actual fun rememberRequestLocationPermission(onGranted: () -> Unit, onDenied: () -> Unit): () -> Unit {
    val launcher =
        androidx.activity.compose.rememberLauncherForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions(),
        ) { permissions ->
            if (permissions.values.any { it }) {
                onGranted()
            } else {
                onDenied()
            }
        }
    return remember(launcher) {
        {
            launcher.launch(
                arrayOf(
                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                    android.Manifest.permission.ACCESS_COARSE_LOCATION,
                ),
            )
        }
    }
}

@Composable
actual fun rememberOpenLocationSettings(): () -> Unit {
    val launcher =
        androidx.activity.compose.rememberLauncherForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult(),
        ) { _ ->
        }
    return remember(launcher) { { launcher.launch(Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS)) } }
}

@Composable
actual fun rememberRequestBluetoothPermission(onGranted: () -> Unit, onDenied: () -> Unit): () -> Unit {
    if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S) {
        // On pre-Android 12, BLE scanning is gated by location permission, not Bluetooth.
        return remember { { onGranted() } }
    }
    val currentOnGranted = rememberUpdatedState(onGranted)
    val currentOnDenied = rememberUpdatedState(onDenied)
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            if (permissions.values.all { it }) currentOnGranted.value() else currentOnDenied.value()
        }
    return remember(launcher) {
        {
            launcher.launch(
                arrayOf(android.Manifest.permission.BLUETOOTH_SCAN, android.Manifest.permission.BLUETOOTH_CONNECT),
            )
        }
    }
}

@Composable
actual fun rememberRequestNotificationPermission(onGranted: () -> Unit, onDenied: () -> Unit): () -> Unit {
    if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) {
        // Pre-Android 13, no runtime notification permission required.
        return remember { { onGranted() } }
    }
    val currentOnGranted = rememberUpdatedState(onGranted)
    val currentOnDenied = rememberUpdatedState(onDenied)
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) currentOnGranted.value() else currentOnDenied.value()
        }
    return remember(launcher) { { launcher.launch(android.Manifest.permission.POST_NOTIFICATIONS) } }
}

@Composable
actual fun rememberRequestLocalNetworkPermission(onGranted: () -> Unit, onDenied: () -> Unit): () -> Unit {
    if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S) {
        // Pre-Android 12, no local network permission required
        return remember { { onGranted() } }
    }
    val currentOnGranted = rememberUpdatedState(onGranted)
    val currentOnDenied = rememberUpdatedState(onDenied)
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) currentOnGranted.value() else currentOnDenied.value()
        }
    return remember(launcher) { { launcher.launch(android.Manifest.permission.ACCESS_LOCAL_NETWORK) } }
}

@Composable
actual fun isLocalNetworkPermissionGranted(): Boolean {
    if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S) {
        // Pre-Android 12, no runtime local-network permission exists; access is implicit via INTERNET.
        return true
    }
    val context = LocalContext.current
    return rememberOnResumeState {
        androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACCESS_LOCAL_NETWORK,
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }
}

@Composable
actual fun isLocationPermissionGranted(): Boolean {
    val context = LocalContext.current
    return rememberOnResumeState {
        androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACCESS_FINE_LOCATION,
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }
}

@Composable
actual fun isGpsDisabled(): Boolean {
    val context = LocalContext.current
    return rememberOnResumeState { context.gpsDisabled() }
}

/**
 * Remembers a boolean state that is re-evaluated on each [Lifecycle.Event.ON_RESUME], ensuring the value stays fresh
 * when the user returns from a permission dialog or system settings screen.
 */
@Composable
private fun rememberOnResumeState(check: () -> Boolean): Boolean {
    val state = remember { mutableStateOf(check()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { state.value = check() }
    return state.value
}
