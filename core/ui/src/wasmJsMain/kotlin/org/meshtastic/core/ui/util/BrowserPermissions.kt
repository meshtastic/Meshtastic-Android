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
    // Every js("...") snippet references its Kotlin parameters inside the JS source, which detekt cannot see.
    "UnusedParameter",
)

package org.meshtastic.core.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

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
    DisposableEffect(name) {
        val watch = watchPermission(name) { state = it }
        onDispose { stopWatching(watch) }
    }
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

internal fun notificationPermission(): String = js("Notification.permission")

/** Shows the browser's notification prompt; only reaches the user from inside a click. */
internal fun requestNotificationPermission(onResult: (String) -> Unit) {
    js("Notification.requestPermission().then((p) => onResult(p), () => {})")
}

/** Shows the browser's location prompt by asking for one fix and discarding it. */
internal fun requestGeolocationPermission(onResult: (String) -> Unit) {
    js(
        """navigator.geolocation && navigator.geolocation.getCurrentPosition(
            () => onResult('granted'),
            (e) => { if (e.code === 1) onResult('denied'); })""",
    )
}

/** Subscribes [onState] to a permission's state; returns a handle for [stopWatching]. */
private fun watchPermission(name: String, onState: (String) -> Unit): JsAny = js(
    """{
    let status = null;
    let stopped = false;
    const fire = () => { if (!stopped && status) onState(status.state); };
    if (navigator.permissions && navigator.permissions.query) {
        navigator.permissions.query({ name: name }).then((s) => {
            status = s;
            s.addEventListener('change', fire);
            fire();
        }, () => {});
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
