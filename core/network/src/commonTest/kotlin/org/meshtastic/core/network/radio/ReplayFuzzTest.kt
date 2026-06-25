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

import kotlinx.coroutines.test.runTest
import org.meshtastic.core.repository.HandshakeConstants
import org.meshtastic.core.repository.RadioTransportCallback
import org.meshtastic.core.repository.TransportDisconnectReason
import org.meshtastic.proto.FromRadio
import org.meshtastic.proto.MeshPacket
import org.meshtastic.proto.MyNodeInfo
import org.meshtastic.proto.NodeInfo
import org.meshtastic.proto.ToRadio
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Seeded fuzz suite for the replay ingestion path, built on [ReplayFuzz]. Each test states an invariant the
 * untrusted-input boundary must hold and sweeps it over [ReplayFuzz.DEFAULT_ITERATIONS] deterministic seeds; a failure
 * message carries the seed so it reproduces locally. These run in `allTests`, so the parse/decode boundary is fuzzed on
 * every CI run at no app-size cost.
 *
 * Layers covered here are the self-contained ones with clean pass/fail oracles: the asset parser, the handshake input,
 * and the protobuf decoder. The post-decode handler chain (the layer that is *not* exception-isolated) needs the full
 * `MeshMessageProcessor` graph and a richer oracle — the [ReplayFuzz.adversarialFromRadio] corpus is built to feed that
 * integration harness when it lands.
 */
class ReplayFuzzTest {

    /** Collects relayed frames without decoding, so the oracle is the transport's behaviour, not the callback's. */
    private class Sink : RadioTransportCallback {
        val frames = mutableListOf<ByteArray>()

        override fun onConnect() = Unit

        override fun onDisconnect(isPermanent: Boolean, errorMessage: String?, reason: TransportDisconnectReason?) =
            Unit

        override fun handleFromRadio(bytes: ByteArray) {
            frames += bytes
        }
    }

    private val sampleConfig = listOf(FromRadio(my_info = MyNodeInfo(my_node_num = 1)))
    private val sampleNodes = listOf(FromRadio(node_info = NodeInfo(num = 1)))
    private val samplePackets = listOf(FromRadio(packet = MeshPacket(id = 1)))

    private fun validAsset() = ReplayFuzz.asset(sampleConfig, sampleNodes, samplePackets)

    /**
     * A corrupt asset must fail fast and *only* as [IllegalArgumentException] — never OOM, hang, or leak okio errors.
     */
    @Test
    fun `corrupt assets fail only with IllegalArgumentException`() = runTest {
        ReplayFuzz.forSeeds { random, seed ->
            val input = if (seed % 2 == 0) ReplayFuzz.mutate(random, validAsset()) else ReplayFuzz.randomBytes(random)
            val error =
                runCatching { ReplayRadioTransport(Sink(), this, address = "", frames = input, packetDelayMs = 0) }
                    .exceptionOrNull()
            assertTrue(
                error == null || error is IllegalArgumentException,
                "seed=$seed: parser raised ${error?.let { it::class.simpleName }}: ${error?.message}",
            )
        }
    }

    /** The transport must absorb any outbound bytes — undecodable [ToRadio] is dropped, not thrown. */
    @Test
    fun `handleSendToRadio tolerates arbitrary outbound bytes`() = runTest {
        val transport = ReplayRadioTransport(Sink(), this, address = "", frames = validAsset(), packetDelayMs = 0)
        ReplayFuzz.forSeeds { random, seed ->
            val error =
                runCatching {
                    transport.handleSendToRadio(ReplayFuzz.mutate(random, ToRadio(want_config_id = 1).encode()))
                    transport.handleSendToRadio(ReplayFuzz.randomBytes(random))
                }
                    .exceptionOrNull()
            assertTrue(error == null, "seed=$seed: handleSendToRadio threw $error")
        }
        testScheduler.advanceUntilIdle()
    }

    /**
     * Decoding a bit-flipped frame may throw, but only a catchable [Exception] — an [Error] (OOM/SOE) would be a DoS.
     */
    @Test
    fun `decode of bit-flipped frames never raises an Error`() = runTest {
        val seedFrame = FromRadio(node_info = NodeInfo(num = 7)).encode()
        ReplayFuzz.forSeeds { random, seed ->
            val mutated = ReplayFuzz.mutate(random, seedFrame)
            val error = runCatching { FromRadio.ADAPTER.decode(mutated) }.exceptionOrNull()
            assertTrue(error == null || error is Exception, "seed=$seed: decode raised $error")
        }
    }

    /** Hostile-but-valid content must replay through the transport intact (every relayed frame still decodes). */
    @Test
    fun `adversarial frames replay through the transport without crashing it`() = runTest {
        val sink = Sink()
        val nodes = (0 until 25).map { FromRadio(node_info = ReplayFuzz.adversarialNode(Random(it))) }
        val packets = (0 until 75).map { FromRadio(packet = ReplayFuzz.adversarialPacket(Random(it))) }
        val transport =
            ReplayRadioTransport(
                callback = sink,
                scope = this,
                address = "",
                frames = ReplayFuzz.asset(sampleConfig, nodes, packets),
                packetDelayMs = 0,
            )

        transport.start()
        transport.handleSendToRadio(ToRadio(want_config_id = HandshakeConstants.NODE_INFO_NONCE).encode())
        testScheduler.advanceUntilIdle()

        assertTrue(sink.frames.isNotEmpty())
        sink.frames.forEach { FromRadio.ADAPTER.decode(it) } // round-trip held under hostile field values
    }
}
