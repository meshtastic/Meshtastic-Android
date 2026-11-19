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
@file:Suppress(
    // The browser surface this file wraps takes one small interop function per JS call.
    "TooManyFunctions",
    // Every js("...") snippet references its Kotlin parameters inside the JS source, which detekt cannot see.
    "UnusedParameter",
)

package org.meshtastic.core.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import org.w3c.notifications.Notification
import org.w3c.notifications.NotificationPermission

/**
 * A browser permission as a [PermissionUiState], kept live by the Permissions API's `change` event. [initialState] is
 * shown until the first query resolves. A denied permission is [PermissionStatus.PERMANENTLY_DENIED] because the
 * browser will not prompt again, and a page cannot open its own site settings, so `openAppSettings` does nothing.
 */
@Composable
internal fun rememberBrowserPermissionState(
    name: String,
    initialState: String,
    request: (onResult: (String) -> Unit) -> Unit,
): PermissionUiState {
    var state by remember { mutableStateOf(initialState) }
    LaunchedEffect(name) { browserPermissionState(name).collect { state = it } }
    return remember(state) {
        PermissionUiState(
            status = browserPermissionStatus(state),
            request = { request { state = it } },
            openAppSettings = {},
        )
    }
}

/** Maps the Permissions API's `granted`/`denied`/`prompt` (and `Notification.permission`'s `default`). */
internal fun browserPermissionStatus(state: String): PermissionStatus = when (state) {
    "granted" -> PermissionStatus.GRANTED
    "denied" -> PermissionStatus.PERMANENTLY_DENIED
    else -> PermissionStatus.NOT_REQUESTED
}

internal fun notificationsSupported(): Boolean = js("typeof Notification !== 'undefined'")

internal fun notificationPermission(): String = Notification.permission.asString()

/** Shows the browser's notification prompt; only reaches the user from inside a click. */
internal fun requestNotificationPermission(onResult: (String) -> Unit) {
    Notification.requestPermission()
        .then(
            {
                onResult(it.asString())
                null
            },
            { null },
        )
}

/** The Notifications API's permission enum is a JS string. */
private fun NotificationPermission.asString(): String = unsafeCast<JsString>().toString()

/** Shows the browser's location prompt by asking for one fix and discarding it. */
internal fun requestGeolocationPermission(onResult: (String) -> Unit) {
    js(
        """navigator.geolocation && navigator.geolocation.getCurrentPosition(
            () => onResult('granted'),
            (e) => { if (e.code === 1) onResult('denied'); })""",
    )
}

/**
 * The Permissions API state of permission [name] (`granted`, `prompt`, `denied`), followed for changes while collected.
 * Where the browser cannot answer for [name] the flow emits [unanswered], or nothing when that is null.
 */
internal fun cameraSupported(): Boolean =
    js("typeof navigator !== 'undefined' && !!navigator.mediaDevices && !!navigator.mediaDevices.getUserMedia")

// The browser prompts on the first getUserMedia call; the stream is closed at once, as only the answer is wanted. A
// refusal re-reads the permission, because dismissing the prompt also rejects but leaves it askable. A failure other
// than a refusal (no camera, camera busy) says nothing about the permission.
internal fun requestCameraPermission(onResult: (String) -> Unit) {
    js(
        """navigator.mediaDevices.getUserMedia({ video: true }).then(
            (s) => { s.getTracks().forEach((t) => t.stop()); onResult('granted'); },
            (e) => {
                if (!e || e.name !== 'NotAllowedError') return;
                if (navigator.permissions && navigator.permissions.query) {
                    navigator.permissions.query({ name: 'camera' }).then((p) => onResult(p.state), () => onResult('denied'));
                } else {
                    onResult('denied');
                }
            })""",
    )
}

fun browserPermissionState(name: String, unanswered: String? = null): Flow<String> = callbackFlow {
    val watch = watchPermission(name, unanswered) { trySend(it) }
    awaitClose { stopWatching(watch) }
}

/** Subscribes [onState] to a permission's state; returns a handle for [stopWatching]. */
private fun watchPermission(name: String, unanswered: String?, onState: (String) -> Unit): JsAny = js(
    """{
    let status = null;
    let stopped = false;
    const fire = () => { if (!stopped && status) onState(status.state); };
    const fallBack = () => { if (!stopped && unanswered !== null) onState(unanswered); };
    if (navigator.permissions && navigator.permissions.query) {
        navigator.permissions.query({ name: name }).then((s) => {
            // Stopped before the query answered: attaching now would leave a listener nothing removes.
            if (stopped) return;
            status = s;
            s.addEventListener('change', fire);
            fire();
        }, fallBack);
    } else {
        fallBack();
    }
    return () => {
        stopped = true;
        if (status) status.removeEventListener('change', fire);
    };
}""",
)

private fun stopWatching(watch: JsAny) {
    js("watch()")
}
