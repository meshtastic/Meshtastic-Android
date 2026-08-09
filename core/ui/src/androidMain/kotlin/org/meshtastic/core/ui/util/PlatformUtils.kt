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
@file:Suppress("TooManyFunctions")

package org.meshtastic.core.ui.util

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
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
actual fun rememberOpenLocationSettings(): () -> Unit {
    val launcher =
        androidx.activity.compose.rememberLauncherForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult(),
        ) { _ ->
        }
    return remember(launcher) {
        {
            try {
                launcher.launch(Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            } catch (ex: ActivityNotFoundException) {
                Logger.w(ex) { "No location settings activity available" }
            }
        }
    }
}

// API level at which ACCESS_LOCAL_NETWORK became a real runtime permission (Android 17 / API 37).
// Hardcoded as an integer literal because Build.VERSION_CODES does not yet expose a named constant
// for API 37 in the SDK we compile against (current max named constant is VANILLA_ICE_CREAM / API 35).
// On older API levels the permission string is unknown to the system and requestPermission() returns
// an immediate denial, which would incorrectly disable any caller that disables itself on denial.
private const val LOCAL_NETWORK_PERMISSION_API = 37

@Composable
actual fun isGpsDisabled(): Boolean {
    val context = LocalContext.current
    return rememberOnResumeState { context.gpsDisabled() }
}

@Composable
actual fun rememberOpenBluetoothSettings(): () -> Unit {
    val context = LocalContext.current
    return remember(context) {
        {
            try {
                context.startActivity(
                    Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            } catch (ex: ActivityNotFoundException) {
                Logger.w(ex) { "No Bluetooth settings activity available" }
            }
        }
    }
}

@Composable
actual fun rememberOpenWifiSettings(): () -> Unit {
    val context = LocalContext.current
    return remember(context) {
        {
            try {
                context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (ex: ActivityNotFoundException) {
                Logger.w(ex) { "No Wi-Fi settings activity available" }
            }
        }
    }
}

@Composable
actual fun isBluetoothDisabled(): Boolean {
    val context = LocalContext.current
    return rememberObservedFlag(
        read = {
            // adapter == null means the device has no Bluetooth at all — not "disabled", so don't nag.
            val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
            adapter != null && !adapter.isEnabled
        },
        subscribe = { onChange ->
            val receiver =
                object : BroadcastReceiver() {
                    override fun onReceive(receiverContext: Context?, intent: Intent?) = onChange()
                }
            // ACTION_STATE_CHANGED is a protected system broadcast; NOT_EXPORTED keeps the receiver app-private.
            // Registered without a Handler, so onReceive is delivered on the main thread.
            ContextCompat.registerReceiver(
                context,
                receiver,
                IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            val unregister = { context.unregisterReceiver(receiver) }
            unregister
        },
    )
}

@Composable
actual fun isWifiUnavailable(): Boolean {
    val context = LocalContext.current
    return rememberObservedFlag(
        read = {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            // Scan every current network, not just `activeNetwork`: NSD/mDNS only needs *a* LAN, and
            // Android often keeps cellular as the default route (or leaves Wi-Fi unvalidated), which
            // previously stranded the banner "on" even after Wi-Fi returned.
            cm?.hasLocalNetwork() != true
        },
        subscribe = { onChange ->
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val handler = Handler(Looper.getMainLooper())
            // Three separate NetworkRequests are registered so changes to any individual transport
            // (Wi-Fi, Ethernet, or VPN) independently trigger a re-evaluation of local network
            // availability. Registering per-transport callbacks fires `onChange` on gain/loss/
            // capability-change of any Wi-Fi, Ethernet, or VPN network. VPN is tracked alongside the
            // physical LAN transports because a routed overlay (ZeroTier/Tailscale) is a valid
            // reachability path for a TCP node; cellular-only is not, so CELLULAR is excluded.
            val wifiRequest = NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build()
            val ethernetRequest =
                NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_ETHERNET).build()
            val vpnRequest = NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_VPN).build()
            val wifiCallback = localNetworkCallback(onChange)
            val ethernetCallback = localNetworkCallback(onChange)
            val vpnCallback = localNetworkCallback(onChange)
            cm.registerNetworkCallback(wifiRequest, wifiCallback, handler)
            cm.registerNetworkCallback(ethernetRequest, ethernetCallback, handler)
            cm.registerNetworkCallback(vpnRequest, vpnCallback, handler)
            val unregister = {
                cm.unregisterNetworkCallback(wifiCallback)
                cm.unregisterNetworkCallback(ethernetCallback)
                cm.unregisterNetworkCallback(vpnCallback)
            }
            unregister
        },
    )
}

/**
 * Returns `true` if any currently-tracked network carries a Wi-Fi, Ethernet, or VPN transport — the three transports
 * that can carry TCP traffic to a Meshtastic node and therefore back the network-scan discovery surfaced by
 * `ConnectionsScreen`. Cellular alone is **not** sufficient. Backs the `read` side of `isWifiUnavailable`; extracted so
 * the transport reduction can delegate to the platform-agnostic [anyNetworkScanTransportAvailable] helper (which is
 * unit-tested in `commonTest`).
 */
// ConnectivityManager.allNetworks is deprecated (API 31+) in favor of NetworkCallback-based discovery,
// but a synchronous snapshot of active networks is exactly what this transport probe needs. Suppress
// until a callback-based rewrite is warranted.
@Suppress("DEPRECATION")
private fun ConnectivityManager.hasLocalNetwork(): Boolean {
    val transports =
        allNetworks.mapNotNull { network ->
            getNetworkCapabilities(network)?.let { caps ->
                NetworkTransportInfo(
                    hasWifi = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI),
                    hasEthernet = caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET),
                    hasVpn = caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN),
                )
            }
        }
    return anyNetworkScanTransportAvailable(transports)
}

/**
 * Bridges `ConnectivityManager` events to the `rememberObservedFlag` `onChange` trigger. Each callback registration
 * gets its own instance so `unregisterNetworkCallback` is symmetric.
 */
private fun localNetworkCallback(onChange: () -> Unit) = object : ConnectivityManager.NetworkCallback() {
    override fun onAvailable(network: Network) = onChange()

    override fun onLost(network: Network) = onChange()

    override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) = onChange()
}

@Composable
actual fun rememberOpenAppSettings(): () -> Unit {
    val context = LocalContext.current
    return remember(context) {
        {
            val intent =
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", context.packageName, null)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            try {
                context.startActivity(intent)
            } catch (ex: ActivityNotFoundException) {
                Logger.w(ex) { "Failed to open app settings" }
            }
        }
    }
}

@Composable
actual fun rememberLocationPermissionState(): PermissionUiState = rememberRuntimePermissionState(
    permissions =
    arrayOf(
        android.Manifest.permission.ACCESS_FINE_LOCATION,
        android.Manifest.permission.ACCESS_COARSE_LOCATION,
    ),
    // Coarse-only grants are an accepted degraded mode, so any granted permission counts.
    requireAll = false,
)

@Composable
actual fun rememberBluetoothPermissionState(): PermissionUiState {
    if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S) {
        // Pre-Android 12 has no runtime Bluetooth permission — the platform gates BLE scanning on fine location
        // instead. Reporting "granted" here left a user who declined location with an empty device list and no way to
        // re-prompt, so surface the permission that actually blocks the scan. Fine specifically: API 29/30 return zero
        // scan results on a coarse-only grant.
        return rememberRuntimePermissionState(
            permissions = arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION),
            requireAll = true,
        )
    }
    return rememberRuntimePermissionState(
        permissions =
        arrayOf(android.Manifest.permission.BLUETOOTH_SCAN, android.Manifest.permission.BLUETOOTH_CONNECT),
        requireAll = true,
    )
}

@Composable
actual fun rememberNotificationPermissionState(): PermissionUiState {
    if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) {
        // Pre-Android 13, no runtime notification permission required.
        return rememberGrantedPermissionState()
    }
    return rememberRuntimePermissionState(
        permissions = arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
        requireAll = true,
    )
}

@Composable
actual fun rememberLocalNetworkPermissionState(): PermissionUiState {
    if (android.os.Build.VERSION.SDK_INT < LOCAL_NETWORK_PERMISSION_API) {
        // Pre-Android 17, ACCESS_LOCAL_NETWORK is implicit via INTERNET; treat as granted.
        return rememberGrantedPermissionState()
    }
    return rememberRuntimePermissionState(
        permissions = arrayOf(android.Manifest.permission.ACCESS_LOCAL_NETWORK),
        requireAll = true,
    )
}

@Composable
actual fun rememberCameraPermissionState(): PermissionUiState =
    rememberRuntimePermissionState(permissions = arrayOf(android.Manifest.permission.CAMERA), requireAll = true)

/** A constant [PermissionUiState] for API levels where the permission is not gated at runtime. */
@Composable private fun rememberGrantedPermissionState(): PermissionUiState = remember { grantedPermissionUiState() }

/**
 * Shared engine behind every `rememberXxxPermissionState()`. Computes the [PermissionStatus] from the live grant state,
 * the persisted "has-been-requested" flag, and `shouldShowRequestPermissionRationale`, refreshing on `ON_RESUME`
 * (return from settings) and immediately after a request completes.
 *
 * @param requireAll when true, all [permissions] must be granted to count as [PermissionStatus.GRANTED]; when false,
 *   any single grant suffices (used by location so a coarse-only grant is accepted — R7).
 */
@Composable
private fun rememberRuntimePermissionState(permissions: Array<String>, requireAll: Boolean): PermissionUiState {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val tracker = remember(context) { PermissionRequestTracker(context) }
    val openAppSettings = rememberOpenAppSettings()
    // The permission whose rationale + requested flag represents the group.
    val primaryPermission = permissions.first()

    fun compute(): PermissionStatus {
        val granted =
            isPermissionGroupGranted(
                results =
                permissions.map {
                    ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
                },
                requireAll = requireAll,
            )
        val shouldShowRationale =
            if (activity != null) {
                ActivityCompat.shouldShowRequestPermissionRationale(activity, primaryPermission)
            } else {
                // No Activity to query (e.g. a non-Activity-hosted composition). Assume a rationale is still warranted
                // rather than risk a false PERMANENTLY_DENIED that would strand the user with only a settings link.
                true
            }
        return computePermissionStatus(
            granted = granted,
            hasRequested = tracker.hasRequested(primaryPermission),
            shouldShowRationale = shouldShowRationale,
        )
    }

    val statusState = remember { mutableStateOf(compute()) }
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { _ ->
            // The OS has now adjudicated the request; only here is it true that we have asked the user. The result
            // callback runs on the main thread, so updating the state directly here is safe and recomposes the caller.
            tracker.markRequested(primaryPermission)
            statusState.value = compute()
        }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { statusState.value = compute() }

    val request = remember(launcher) { { launcher.launch(permissions) } }
    return PermissionUiState(status = statusState.value, request = request, openAppSettings = openAppSettings)
}

/**
 * Remembers a boolean derived from [read], kept live by an observer registered via [subscribe] for the duration of the
 * composition. [subscribe] receives an `onChange` callback to invoke whenever the underlying state may have changed and
 * must return a teardown function. The value is re-seeded via [read] at registration, so it is correct even before the
 * first event arrives. Used for adapter/connectivity state that changes outside the activity lifecycle (e.g. toggling
 * Bluetooth or Wi-Fi from the quick-settings shade).
 */
@Composable
private fun rememberObservedFlag(read: () -> Boolean, subscribe: (onChange: () -> Unit) -> () -> Unit): Boolean {
    val currentRead = rememberUpdatedState(read)
    val currentSubscribe = rememberUpdatedState(subscribe)
    val state = remember { mutableStateOf(read()) }
    DisposableEffect(Unit) {
        state.value = currentRead.value()
        val unsubscribe = currentSubscribe.value { state.value = currentRead.value() }
        onDispose { unsubscribe() }
    }
    return state.value
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
