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

import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.keepScreenOn
import co.touchlab.kermit.Logger
import kotlinx.browser.window
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.await
import org.jetbrains.compose.resources.StringResource
import org.meshtastic.core.common.util.CommonUri
import kotlin.js.Promise

// Android system-settings concepts have no browser equivalent, so these mirror the iOS no-ops.

@Composable actual fun rememberOpenNfcSettings(): () -> Unit = {}

/** The Web Share API's share sheet where the browser has one for text, otherwise the clipboard as on desktop. */
@Composable
actual fun rememberShareText(): (text: String, subject: String) -> Unit {
    val copy = rememberClipboardShare()
    return remember(copy) { { text, subject -> shareOrElse(text, subject) { copy(text) } } }
}

// navigator.share must run inside the click's user activation, so nothing suspends before it. Closing the share sheet
// rejects with AbortError and ends there; any other refusal, or no Web Share for text, falls back to the clipboard.
@Suppress("UnusedParameter") // read by name inside the js() body
internal fun shareOrElse(text: String, subject: String, fallback: () -> Unit) {
    js(
        """{
        const data = { title: subject, text: text };
        if (typeof navigator.share === 'function' && (typeof navigator.canShare !== 'function' || navigator.canShare(data))) {
            navigator.share(data).catch((e) => { if (!e || e.name !== 'AbortError') fallback(); });
        } else {
            fallback();
        }
    }""",
    )
}

@Composable actual fun rememberShowToast(): suspend (String) -> Unit = rememberSnackbarToast()

@Composable actual fun rememberShowToastResource(): suspend (StringResource) -> Unit = rememberSnackbarToastResource()

@Composable
actual fun rememberOpenMap(): (latitude: Double, longitude: Double, label: String) -> Unit = rememberOpenMapInBrowser()

/** Web — opens the URL in a new browser tab via `window.open`, so the running app's own tab/state is left intact. */
@Composable actual fun rememberOpenUrl(): (url: String) -> Unit = { url -> window.open(url, "_blank", "") }

/** Chromium's save picker where it exists, otherwise the write becomes a browser download. See [launchSaveFile]. */
@Composable
actual fun rememberSaveFileLauncher(
    onUriReceived: (CommonUri) -> Unit,
): (defaultFilename: String, mimeType: String) -> Unit {
    val scope = rememberCoroutineScope()
    val currentOnUriReceived by rememberUpdatedState(onUriReceived)
    return remember(scope) {
        { fileName, mimeType -> launchSaveFile(scope, fileName, mimeType) { currentOnUriReceived(it) } }
    }
}

/** The browser's `<input type=file>` chooser. See [launchOpenFile]. */
@Composable
actual fun rememberOpenFileLauncher(onUriReceived: (CommonUri?) -> Unit): (mimeType: String) -> Unit {
    val currentOnUriReceived by rememberUpdatedState(onUriReceived)
    return remember { { mimeType -> launchOpenFile(mimeType) { currentOnUriReceived(it) } } }
}

/** Only Chromium can pick a directory, and the UF2 volume flow it serves needs USB mass storage a page cannot reach. */
@Composable actual fun rememberOpenDocumentTreeLauncher(onTreeUriSelect: (CommonUri?) -> Unit): () -> Unit = {}

/**
 * Compose's own [keepScreenOn] holds the Screen Wake Lock for the window and takes it back when the tab is shown again.
 * It is a modifier, so this places a zero-size node in the caller's layout while [enabled].
 */
@Composable
@Suppress("ModifierMissing") // the expect signature has no modifier to take
actual fun KeepScreenOn(enabled: Boolean) {
    if (enabled) Spacer(Modifier.keepScreenOn())
}

@Composable actual fun rememberOpenLocationSettings(): () -> Unit = {}

@Composable actual fun rememberOpenBluetoothSettings(): () -> Unit = {}

@Composable actual fun rememberOpenWifiSettings(): () -> Unit = {}

actual val bleScanRequiresLocationServices: Boolean = false

@Composable actual fun isGpsDisabled(): Boolean = false

/** Web Bluetooth where the browser has it and, where it can say, a Bluetooth adapter to use. */
@Composable
actual fun isBluetoothSupported(): Boolean {
    val present = remember { webBluetoothAvailable() }
    val adapter by produceState(initialValue = present) { value = present && webBluetoothAdapterAvailable() }
    return adapter
}

/** Whether this browser has Web Bluetooth at all. Firefox, Safari and Chrome on Linux without its flag do not. */
fun webBluetoothAvailable(): Boolean = js("typeof navigator !== 'undefined' && !!navigator.bluetooth")

/** Whether this browser has Web Serial: Chrome, Edge and Opera on a computer. */
fun webSerialAvailable(): Boolean = js("typeof navigator !== 'undefined' && !!navigator.serial")

private fun bluetoothAvailabilityOrNull(): Promise<JsBoolean>? = js(
    "navigator.bluetooth && typeof navigator.bluetooth.getAvailability === 'function' " +
        "? navigator.bluetooth.getAvailability() : null",
)

/** `getAvailability()`, false with no usable adapter; true where the browser cannot tell, as the chooser still can. */
@Suppress("TooGenericExceptionCaught")
private suspend fun webBluetoothAdapterAvailable(): Boolean {
    val availability = bluetoothAvailabilityOrNull() ?: return true
    return try {
        availability.await<JsBoolean>().toBoolean()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        Logger.d(e) { "Web Bluetooth getAvailability() rejected" }
        true
    }
}

@Composable actual fun isBluetoothDisabled(): Boolean = false

@Composable actual fun isWifiUnavailable(): Boolean = false

@Composable actual fun rememberOpenAppSettings(): () -> Unit = {}

@Composable
actual fun rememberLocationPermissionState(): PermissionUiState =
    rememberBrowserPermissionState("geolocation", "prompt") { requestGeolocationPermission(it) }

/** A browser grants location without an approximate tier, so precise is the same grant. */
@Composable actual fun rememberPreciseLocationPermissionState(): PermissionUiState = rememberLocationPermissionState()

@Composable actual fun rememberBluetoothPermissionState(): PermissionUiState = grantedPermissionUiState()

/** Without the Notifications API (iOS Safari outside a home-screen app) nothing can be shown, so nothing is asked. */
@Composable
actual fun rememberNotificationPermissionState(): PermissionUiState = if (notificationsSupported()) {
    rememberBrowserPermissionState("notifications", notificationPermission()) {
        requestNotificationPermission(it)
    }
} else {
    PermissionUiState(status = PermissionStatus.PERMANENTLY_DENIED, request = {}, openAppSettings = {})
}

@Composable actual fun rememberLocalNetworkPermissionState(): PermissionUiState = grantedPermissionUiState()

// Firefox answers no Permissions API query for the camera, so there the state stays unasked until a request answers.
@Composable
actual fun rememberCameraPermissionState(): PermissionUiState = if (cameraSupported()) {
    rememberBrowserPermissionState("camera", "prompt") { requestCameraPermission(it) }
} else {
    PermissionUiState(status = PermissionStatus.PERMANENTLY_DENIED, request = {}, openAppSettings = {})
}
