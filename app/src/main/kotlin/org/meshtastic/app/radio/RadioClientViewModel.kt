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
package org.meshtastic.app.radio

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import org.koin.core.annotation.KoinViewModel
import org.meshtastic.sdk.ConnectionState
import org.meshtastic.sdk.MeshEvent

/**
 * POC ViewModel that exposes the SDK [RadioClient] connection lifecycle to the UI.
 *
 * **Connection state:** Uses `flatMapLatest` on the `StateFlow<RadioClient?>` so that any screen collecting
 * [sdkConnectionState] automatically switches to the new client's connection flow when
 * [RadioClientProvider.rebuildAndConnect] replaces the active client. [SharingStarted.WhileSubscribed] with a 5 s
 * timeout keeps the upstream active briefly after the last subscriber leaves (e.g., configuration change) so the next
 * subscriber doesn't miss a fast `Connected` event.
 *
 * **Events:** Collected with [SharingStarted.Eagerly] so that [MeshEvent]s (device rebooted, storage degraded, security
 * warnings) are never dropped while navigating between screens. The collection is launched in [viewModelScope] which is
 * tied to the application lifecycle via Koin's `@KoinViewModel` singleton scope — not to any individual screen.
 *
 * SDK gaps surfaced here:
 * - [ConnectionState.Configuring] has no counterpart in the legacy [org.meshtastic.core.model.ConnectionState]
 * - [ConnectionState.Reconnecting] has no counterpart in the legacy model
 */
@KoinViewModel
class RadioClientViewModel(private val provider: RadioClientProvider) : ViewModel() {

    /** Live SDK connection state; `null` if no client is active (no radio configured). */
    val sdkConnectionState: StateFlow<ConnectionState?> =
        provider.client
            .flatMapLatest { client -> client?.connection ?: flowOf(null) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * Human-readable label for the SDK connection state. Useful as a debug overlay in POC builds to see SDK state
     * alongside the legacy state.
     */
    val sdkConnectionLabel: StateFlow<String> =
        sdkConnectionState.map { it.toLabel() }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "SDK: —")

    init {
        // Collect events eagerly so none are dropped during navigation.
        // This scope lives as long as the ViewModel (application lifetime via Koin singleton).
        provider.client
            .flatMapLatest { client -> client?.events ?: emptyFlow() }
            .onEach { event ->
                when (event) {
                    is MeshEvent.StorageDegraded -> Logger.w { "[SDK] StorageDegraded: ${event.reason}" }
                    is MeshEvent.DeviceRebooted -> Logger.i { "[SDK] DeviceRebooted" }
                    is MeshEvent.SecurityWarning -> Logger.w { "[SDK] SecurityWarning: $event" }
                    else -> Logger.d { "[SDK] Event: $event" }
                }
            }
            .launchIn(viewModelScope)
    }

    /** Kick off a (re)connect using the current saved radio address. */
    fun connect() = provider.rebuildAndConnectAsync()

    /** Disconnect the active client. */
    fun disconnect() = provider.disconnect()
}

private const val PERCENT = 100

private fun ConnectionState?.toLabel(): String = when (this) {
    null -> "SDK: no client"
    ConnectionState.Disconnected -> "SDK: Disconnected"
    is ConnectionState.Connecting -> "SDK: Connecting (#$attempt)"
    is ConnectionState.Configuring -> "SDK: Configuring — ${phase.name} (${(progress * PERCENT).toInt()}%)"
    ConnectionState.Connected -> "SDK: Connected ✓"
    is ConnectionState.Reconnecting -> "SDK: Reconnecting (#$attempt) — $cause"
}
