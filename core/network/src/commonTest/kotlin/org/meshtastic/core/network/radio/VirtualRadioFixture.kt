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
package org.meshtastic.core.network.radio

import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import org.meshtastic.sdk.TransportState
import org.meshtastic.sdk.WireFraming

/** Records the SDK transport’s frames for assertions about the simulated firmware. */
internal class VirtualRadioFixture(
    private val test: TestScope,
    private val link: VirtualRadioLink,
    private val recorder: VirtualRadioRecorder,
    private val connectOnFirstSend: Boolean,
) {
    private var collector: Job? = null
    private var closed = false

    suspend fun start() {
        val wasConnected = link.state.value == TransportState.Connected
        link.connect()
        if (!wasConnected && link.state.value == TransportState.Connected) {
            recorder.onConnect()
            collector =
                test.backgroundScope.launch {
                    link.frames().collect {
                        recorder.handleFromRadio(it.bytes.substring(WireFraming.HEADER_SIZE).toByteArray())
                    }
                }
        }
        test.testScheduler.runCurrent()
    }

    suspend fun handleSendToRadio(payload: ByteArray): Boolean {
        if (connectOnFirstSend && collector == null && !closed) start()
        if (link.state.value != TransportState.Connected) return false
        link.send(virtualPhoneApiFrame(payload))
        test.testScheduler.runCurrent()
        return true
    }

    suspend fun close() {
        closed = true
        link.disconnect()
        collector?.cancel()
    }
}

/** What a [VirtualRadioFixture] reports: the link coming up and each deframed `FromRadio` payload. */
internal interface VirtualRadioRecorder {
    fun onConnect()

    fun handleFromRadio(bytes: ByteArray)
}
