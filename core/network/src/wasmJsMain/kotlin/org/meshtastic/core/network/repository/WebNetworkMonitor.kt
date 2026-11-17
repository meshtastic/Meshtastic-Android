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
package org.meshtastic.core.network.repository

import kotlinx.browser.window
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import org.koin.core.annotation.Single
import org.w3c.dom.events.Event

/**
 * Backed by the real, documented `navigator.onLine` property plus the `window` `online`/`offline` events (MDN:
 * https://developer.mozilla.org/en-US/docs/Web/API/Navigator/onLine) — both provided directly by `kotlinx-browser`,
 * with no custom `external`/`js()` interop needed.
 *
 * The listeners are stored in `val`s and the same references are passed to both `addEventListener` and
 * `removeEventListener` — the same identity-preserving idiom `WebBluetoothApi.kt`'s `addListener` helper already relies
 * on in this module's `core:ble` dependency.
 */
@Single(binds = [NetworkMonitor::class])
class WebNetworkMonitor : NetworkMonitor {
    override val networkAvailable: Flow<Boolean> = callbackFlow {
        trySend(window.navigator.onLine)
        val onOnline: (Event) -> Unit = { trySend(true) }
        val onOffline: (Event) -> Unit = { trySend(false) }
        window.addEventListener("online", onOnline)
        window.addEventListener("offline", onOffline)
        awaitClose {
            window.removeEventListener("online", onOnline)
            window.removeEventListener("offline", onOffline)
        }
    }
}
