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
@file:Suppress("TooManyFunctions", "UnusedParameter")

package org.meshtastic.core.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalClipboard
import kotlinx.browser.window
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.await
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.meshtastic.core.common.util.CommonUri
import kotlin.js.Promise

// Android system-settings concepts have no browser equivalent, so these mirror the iOS no-ops.

@Composable actual fun rememberOpenNfcSettings(): () -> Unit = {}

/** A browser tab has no share sheet everywhere; the clipboard is the honest equivalent, as on desktop. */
@Composable
actual fun rememberShareText(): (text: String, subject: String) -> Unit {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    return { text, _ -> scope.launch { clipboard.setClipEntry(createClipEntry(text, sensitive = true)) } }
}

@Composable actual fun rememberShowToast(): suspend (String) -> Unit = { _ -> }

@Composable actual fun rememberShowToastResource(): suspend (StringResource) -> Unit = { _ -> }

@Composable actual fun rememberOpenMap(): (latitude: Double, longitude: Double, label: String) -> Unit = { _, _, _ -> }

/** Web — opens the URL in a new browser tab via `window.open`, so the running app's own tab/state is left intact. */
@Composable actual fun rememberOpenUrl(): (url: String) -> Unit = { url -> window.open(url, "_blank", "") }

/** URI scheme of a save target: the web `FileService` turns what is written to it into a download of that name. */
const val WEB_DOWNLOAD_SCHEME: String = "download"

/** A browser has no save dialog everywhere, so the file goes to the downloads folder under [defaultFilename]. */
@Composable
actual fun rememberSaveFileLauncher(
    onUriReceived: (CommonUri) -> Unit,
): (defaultFilename: String, mimeType: String) -> Unit {
    val callback by rememberUpdatedState(onUriReceived)
    return remember {
        { fileName, _ -> callback(CommonUri.parse("$WEB_DOWNLOAD_SCHEME:${CommonUri.encode(fileName)}")) }
    }
}

/** Opens the browser's file picker and hands back a `blob:` URL for the picked file, or null when cancelled. */
@Composable
actual fun rememberOpenFileLauncher(onUriReceived: (CommonUri?) -> Unit): (mimeType: String) -> Unit {
    val callback by rememberUpdatedState(onUriReceived)
    return remember {
        { mimeType ->
            // A generic type would hide files the browser cannot type, such as a .db backup.
            val accept = mimeType.takeUnless { it == GENERIC_MIME_TYPE || it == ANY_MIME_TYPE }.orEmpty()
            pickFile(accept) { url -> callback(url?.let(CommonUri::parse)) }
        }
    }
}

private const val GENERIC_MIME_TYPE = "application/octet-stream"
private const val ANY_MIME_TYPE = "*/*"

private fun pickFile(accept: String, onPicked: (String?) -> Unit): Unit = js(
    """{
        const input = document.createElement("input");
        input.type = "file";
        if (accept) input.accept = accept;
        input.addEventListener("change", () => {
            const file = input.files && input.files[0];
            onPicked(file ? URL.createObjectURL(file) : null);
        });
        input.addEventListener("cancel", () => onPicked(null));
        input.click();
    }""",
)

@Composable actual fun rememberOpenDocumentTreeLauncher(onTreeUriSelect: (CommonUri?) -> Unit): () -> Unit = {}

/**
 * Holds a Screen Wake Lock while [enabled]. A rejected request (unsupported browser, insecure origin, hidden tab) is a
 * silent no-op, and the browser drops the lock when the tab is hidden without this re-requesting it.
 */
@Suppress("TooGenericExceptionCaught")
@Composable
actual fun KeepScreenOn(enabled: Boolean) {
    LaunchedEffect(enabled) {
        if (!enabled) return@LaunchedEffect
        // Request and release share one coroutine, so a fast dispose cannot race an in-flight request and leak it.
        val sentinel =
            try {
                navigator.wakeLock?.request("screen")?.await()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
        try {
            awaitCancellation()
        } finally {
            sentinel?.release()
        }
    }
}

@Composable actual fun rememberOpenLocationSettings(): () -> Unit = {}

@Composable actual fun rememberOpenBluetoothSettings(): () -> Unit = {}

@Composable actual fun rememberOpenWifiSettings(): () -> Unit = {}

actual val bleScanRequiresLocationServices: Boolean = false

@Composable actual fun isGpsDisabled(): Boolean = false

@Composable actual fun isBluetoothSupported(): Boolean = true

@Composable actual fun isBluetoothDisabled(): Boolean = false

@Composable actual fun isWifiUnavailable(): Boolean = false

@Composable actual fun rememberOpenAppSettings(): () -> Unit = {}

@Composable actual fun rememberLocationPermissionState(): PermissionUiState = grantedPermissionUiState()

@Composable actual fun rememberPreciseLocationPermissionState(): PermissionUiState = grantedPermissionUiState()

@Composable actual fun rememberBluetoothPermissionState(): PermissionUiState = grantedPermissionUiState()

@Composable actual fun rememberNotificationPermissionState(): PermissionUiState = grantedPermissionUiState()

@Composable actual fun rememberLocalNetworkPermissionState(): PermissionUiState = grantedPermissionUiState()

@Composable actual fun rememberCameraPermissionState(): PermissionUiState = grantedPermissionUiState()

private external interface JsWakeLockSentinel : JsAny {
    fun release(): Promise<JsAny?>
}

private external interface JsWakeLock : JsAny {
    fun request(type: String): Promise<JsWakeLockSentinel>
}

private external interface JsNavigatorWakeLock : JsAny {
    val wakeLock: JsWakeLock?
}

/** Access to `navigator.wakeLock`, isolated here — see the `KeepScreenOn` kdoc above. */
private external val navigator: JsNavigatorWakeLock
