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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.browser.window
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.await
import kotlinx.coroutines.awaitCancellation
import org.jetbrains.compose.resources.StringResource
import org.meshtastic.core.common.util.CommonUri
import kotlin.js.Promise

// Everything below except `rememberOpenUrl` and `KeepScreenOn` mirrors iosMain/NoopStubs.kt's exact choices — same
// reasoning as iOS: these are Android-system-settings concepts (NFC/Bluetooth/Wi-Fi/location/app settings, ahead-of-
// time permission-state checks) with no browser equivalent, or platform features (toasts, native file/document
// pickers) whose nearest browser analog (a File System Access API picker, gated behind a user gesture and Chromium-
// only) isn't a drop-in replacement for the contract these expects describe. `bleScanRequiresLocationServices` and the
// four `is*Disabled`/`is*Unavailable` checks are `false` for the same reason they are on iOS: the concept of an ahead-
// of-time "is Bluetooth/GPS/Wi-Fi disabled at the system level" query doesn't exist for a sandboxed browser tab.

@Composable actual fun rememberOpenNfcSettings(): () -> Unit = {}

@Composable actual fun rememberShowToast(): suspend (String) -> Unit = { _ -> }

@Composable actual fun rememberShowToastResource(): suspend (StringResource) -> Unit = { _ -> }

@Composable actual fun rememberOpenMap(): (latitude: Double, longitude: Double, label: String) -> Unit = { _, _, _ -> }

/** Web — opens the URL in a new browser tab via `window.open`, so the running app's own tab/state is left intact. */
@Composable actual fun rememberOpenUrl(): (url: String) -> Unit = { url -> window.open(url, "_blank", "") }

@Composable
actual fun rememberSaveFileLauncher(
    onUriReceived: (CommonUri) -> Unit,
): (defaultFilename: String, mimeType: String) -> Unit = { _, _ -> }

@Composable
actual fun rememberOpenFileLauncher(onUriReceived: (CommonUri?) -> Unit): (mimeType: String) -> Unit = { _ -> }

@Composable actual fun rememberOpenDocumentTreeLauncher(onTreeUriSelect: (CommonUri?) -> Unit): () -> Unit = {}

@Composable actual fun rememberReadTextFromUri(): suspend (uri: CommonUri, maxChars: Int) -> String? = { _, _ -> null }

/**
 * Web — backed by the real, documented Screen Wake Lock API
 * (https://www.w3.org/TR/screen-wake-lock/#the-wakelock-interface). `kotlinx-browser` has no binding for it (checked:
 * absent from its generated `org.w3c.dom` sources), so it's declared fresh below, following the same `external
 * interface : JsAny` + private `external val navigator` idiom `core:ble`'s `WebBluetoothApi.kt` established for
 * `navigator.bluetooth`.
 *
 * Requesting the lock requires a secure context (HTTPS) and a visible document; a request that is rejected for any
 * reason (unsupported browser, insecure origin, tab not visible yet) is swallowed here — the screen simply behaves as
 * if `KeepScreenOn` had never been called, the same no-op fallback posture every platform gap in this file takes.
 *
 * Known gap, not fixed here: per spec the browser releases the lock automatically the instant the document is hidden
 * (tab backgrounded/minimized), and this does not re-request it when the tab regains visibility — a deliberate scope
 * cut for this pass (re-acquiring correctly needs a `visibilitychange` listener, which `kotlinx-browser` also doesn't
 * bind), not an oversight. Android's own `view.keepScreenOn` is similarly revocable by the OS outside this code's
 * control, so this is a difference of degree, not of kind.
 */
@Suppress("TooGenericExceptionCaught")
@Composable
actual fun KeepScreenOn(enabled: Boolean) {
    LaunchedEffect(enabled) {
        if (!enabled) return@LaunchedEffect
        // The request and the eventual release live in the same coroutine (rather than a DisposableEffect firing a
        // separate `launch` for the request) so a fast dispose can never race ahead of an in-flight `request()` and
        // leak the lock — `awaitCancellation()` blocks this coroutine open until the effect leaves composition or
        // `enabled` flips, and `finally` then runs release synchronously on that same cancellation, never orphaned.
        val sentinel =
            try {
                navigator.wakeLock?.request("screen")?.await()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // See kdoc: unsupported/insecure-context/not-yet-visible rejection is an expected, silent no-op.
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

@Composable actual fun isBluetoothDisabled(): Boolean = false

@Composable actual fun isWifiUnavailable(): Boolean = false

@Composable actual fun rememberOpenAppSettings(): () -> Unit = {}

@Composable actual fun rememberLocationPermissionState(): PermissionUiState = grantedPermissionUiState()

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
