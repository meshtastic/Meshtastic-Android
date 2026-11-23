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
package org.meshtastic.core.data.manager

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.meshtastic.core.data.radio.BaseRadioLinks
import org.meshtastic.core.data.radio.RadioSessionPipeline
import org.meshtastic.core.model.DeviceType
import org.meshtastic.core.repository.RadioLink
import org.meshtastic.core.repository.TransportDisconnectReason
import org.meshtastic.core.testing.FakeBluetoothRepository
import org.meshtastic.sdk.ConnectionState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

/** How the app names the end of a WebSocket link the SDK reports, against a peer scripted as firmware. */
class SdkWebSocketSessionTest {

    @Test
    fun `a radio that gives its WebSocket to another client stops the link with that reason`() = runBlocking {
        // Firmware's WS_CLOSE_REPLACED.
        val (state, reason) = endOf(PeerScript.CloseWith(4001)) { it is ConnectionState.Failed }
        assertEquals(TransportDisconnectReason.ReplacedByAnotherClient, reason, "$state")
    }

    @Test
    fun `a radio without the WebSocket phone API is named as such and retried`() = runBlocking {
        val (state, reason) = endOf(PeerScript.RefuseUpgrade(404)) { it is ConnectionState.Reconnecting }
        assertEquals(TransportDisconnectReason.WebSocketNotSupported, reason, "$state")
    }

    /** The first state matching [end] once [script] ends the link, and the reason the link gives its cause. */
    private suspend fun endOf(
        script: PeerScript,
        end: (ConnectionState) -> Boolean,
    ): Pair<ConnectionState, TransportDisconnectReason?> = ScriptedWebSocketPeer(script).use { peer ->
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val links =
            object :
                BaseRadioLinks(
                    FakeBluetoothRepository(),
                    RadioSessionPipeline(unused(), unused(), unused()),
                ) {
                override val supportedDeviceTypes = listOf(DeviceType.TCP)
                override val mockTransportEnabled = MutableStateFlow(false)
                override val isReplayTransportAvailable = false

                override fun openBle(address: String, scope: CoroutineScope): RadioLink? = null
            }
        val link = checkNotNull(links.open("wws://127.0.0.1:${peer.port}/api/v1/ws", scope))
        try {
            scope.launch { runCatching { link.client.connect() } }
            val state = withTimeout(20.seconds) { link.client.connection.first(end) }
            val cause =
                when (state) {
                    is ConnectionState.Failed -> state.cause
                    is ConnectionState.Reconnecting -> state.cause
                    else -> error("No cause in $state")
                }
            state to link.failureReason(cause)
        } finally {
            link.client.disconnect()
            scope.cancel()
        }
    }
}

/** A pipeline dependency these tests never reach: no link they open runs its sessions. */
private fun <T> unused(): Lazy<T> = lazy { error("not used by this test") }
