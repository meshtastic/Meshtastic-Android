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

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.meshtastic.core.repository.RadioTransportCallback
import org.meshtastic.core.repository.TransportDisconnectReason
import org.meshtastic.node.BytesStore
import org.meshtastic.node.host.BearerFactory
import org.meshtastic.node.host.HostState
import org.meshtastic.node.testing.FakeBearerFactory
import org.meshtastic.proto.FromRadio
import org.meshtastic.proto.ToRadio
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Drives [NodeRadioTransport] over a real [org.meshtastic.node.host.EmbeddedNode] on fake bearers, the way the service
 * drives any transport: start, handshake, send, close. The node's own behavior is the library's to test; this pins the
 * seam.
 */
class NodeRadioTransportTest {

    private class RecordingCallback : RadioTransportCallback {
        var connects = 0
        val disconnects = mutableListOf<Boolean>()
        val frames = mutableListOf<ByteArray>()
        val received: List<FromRadio>
            get() = frames.map { FromRadio.ADAPTER.decode(it) }

        override fun onConnect() {
            connects++
        }

        override fun onDisconnect(isPermanent: Boolean, errorMessage: String?, reason: TransportDisconnectReason?) {
            disconnects += isPermanent
        }

        override fun handleFromRadio(bytes: ByteArray) {
            frames += bytes
        }
    }

    private class FakeNodeBearers(private val dir: String) : NodeBearers {
        val factory = FakeBearerFactory()
        override val hostSignals: StateFlow<HostState> = MutableStateFlow(HostState())

        override fun factory(signals: StateFlow<HostState>): BearerFactory = factory

        override fun storesDir(): String = dir

        override fun hostBearerStore(): BytesStore = BytesStore.inMemory()
    }

    // The node's loops never complete, so the transport gets its own cancellable scope on the test scheduler.
    private fun TestScope.transportScope() = CoroutineScope(StandardTestDispatcher(testScheduler))

    private fun tempDir(): String = Files.createTempDirectory("node-transport").toString()

    private fun configRequest(nonce: Int): ByteArray = ToRadio.Builder().want_config_id(nonce).build().encode()

    @Test
    fun `start connects once the node runs and answers the config dump within the frame cap`() = runTest {
        val callback = RecordingCallback()
        val scope = transportScope()
        val repository = NodeStatusRepositoryImpl()
        try {
            val transport = NodeRadioTransport(callback, scope, FakeNodeBearers(tempDir()), repository, isDebug = false)
            transport.start()
            advanceUntilIdle()

            assertEquals(1, callback.connects)
            assertNotNull(repository.status.value, "the repository mirrors the running node")

            assertTrue(transport.handleSendToRadio(configRequest(CONFIG_NONCE)))
            advanceUntilIdle()

            assertTrue(callback.received.any { it.config_complete_id == CONFIG_NONCE })
            assertTrue(callback.frames.all { it.size <= MAX_FRAME_BYTES }, "every FromRadio fits the client cap")

            transport.close()
            assertNull(repository.status.value, "close detaches the repository")
            assertTrue(callback.disconnects.isEmpty(), "the transport's own close reports no disconnect")
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `a full inbound buffer refuses the frame instead of suspending`() = runTest {
        val callback = RecordingCallback()
        val scope = transportScope()
        val repository = NodeStatusRepositoryImpl()
        try {
            val transport = NodeRadioTransport(callback, scope, FakeNodeBearers(tempDir()), repository, isDebug = false)
            // Before start() nothing drains the channel, so the buffer fills and the contract holds: false, no suspend.
            val accepted = (0..INBOUND_PROBE).map { transport.handleSendToRadio(configRequest(it)) }
            assertTrue(accepted.first())
            assertFalse(accepted.last())
            transport.close()
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `close is idempotent`() = runTest {
        val callback = RecordingCallback()
        val scope = transportScope()
        val repository = NodeStatusRepositoryImpl()
        try {
            val transport = NodeRadioTransport(callback, scope, FakeNodeBearers(tempDir()), repository, isDebug = false)
            transport.start()
            advanceUntilIdle()
            transport.close()
            transport.close()
            assertNull(repository.status.value)
        } finally {
            scope.cancel()
        }
    }

    private companion object {
        const val CONFIG_NONCE = 69420
        const val MAX_FRAME_BYTES = 512
        const val INBOUND_PROBE = 70
    }
}
