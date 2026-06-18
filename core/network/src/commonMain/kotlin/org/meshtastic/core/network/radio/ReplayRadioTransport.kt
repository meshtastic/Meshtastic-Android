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

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import okio.Buffer
import okio.EOFException
import org.meshtastic.core.common.util.handledLaunch
import org.meshtastic.core.repository.HandshakeConstants
import org.meshtastic.core.repository.RadioTransport
import org.meshtastic.core.repository.RadioTransportCallback
import org.meshtastic.proto.FromRadio
import org.meshtastic.proto.ToRadio

/**
 * A [RadioTransport] that replays a pre-captured stream of `FromRadio` frames entirely on-device — no network and no
 * paired radio. It is the deterministic, self-contained traffic source behind the "Demo Mode (Replay)" connection
 * entry, used to drive realistic ~200-node mesh traffic into the app for Macrobenchmark / Baseline Profile journeys and
 * populated-UI (node list / map / message) tests.
 *
 * ### What it exercises
 * Frames are handed verbatim to [RadioTransportCallback.handleFromRadio] — the *same* ingestion entry a live
 * BLE/TCP/Serial radio uses — so everything downstream (protobuf decode, the want_config state machine, node-DB and
 * packet handling, the UI) runs exactly as in production. The only things it skips are the physical link and the stream
 * framing layer: it deals in already-deframed `FromRadio` payloads.
 *
 * ### Asset format (`*.fromradio`)
 * A flat, three-section container. **Every integer is a big-endian (network-order) unsigned 32-bit value:**
 *
 * ```
 *   ┌─ Stage-1: config section ────────────────────────────────────────────┐
 *   │  u32  C                          number of config frames              │
 *   │  C ×  ( u32 len, len bytes )     each blob = one serialized FromRadio │
 *   ├─ Stage-2: node section ──────────────────────────────────────────────┤
 *   │  u32  N                          number of node_info frames           │
 *   │  N ×  ( u32 len, len bytes )     each blob = one serialized FromRadio │
 *   ├─ packet section ─────────────────────────────────────────────────────┤
 *   │       ( u32 len, len bytes ) *   repeated until end-of-buffer         │
 *   └──────────────────────────────────────────────────────────────────────┘
 * ```
 *
 * Each `len`-prefixed blob is a single `FromRadio` message as produced by Wire's `encode()`. By section:
 * - **config** — `my_info`, `metadata`, `config.*`, `moduleConfig.*`, and `channel` frames: everything the app needs to
 *   come up, but **no** `node_info` (the app ignores NodeInfo during Stage 1).
 * - **node** — the connected node first, then the captured node DB (one `node_info` per frame).
 * - **packet** — the captured `MeshPacket` traffic, in capture order; the section has no count and runs to EOF.
 *
 * `config_complete_id` is deliberately **omitted** from the file — this transport injects it per phase (below) with the
 * app's own nonce, exactly as real firmware echoes the request.
 *
 * The file is produced by the burningmesh-replay tool (`replay_server.py --export PATH`, which also sanitizes capture
 * PII). **This class is the authoritative reader of the format; keep the exporter and [ReplayRadioTransportTest]'s
 * `asset()` encoder in sync with the layout above.**
 *
 * ### Handshake & runtime behaviour
 * Driven by the app's two-phase want_config handshake ([HandshakeConstants]):
 * - **Stage 1** ([HandshakeConstants.CONFIG_NONCE]) → emit the config section, then `config_complete_id`.
 * - **Stage 2** ([HandshakeConstants.NODE_INFO_NONCE]) → emit the node section, then `config_complete_id`, then start
 *   streaming the packet section **once** (a re-issued Stage-2 request does not restart it).
 *
 * The packet stream is paced at [packetDelayMs] and does not loop; it stops when [scope] is cancelled. Outbound
 * [ToRadio] traffic other than want_config (heartbeats, app packets) is ignored — the replay is strictly read-only, and
 * frames are held in memory, so [close] has nothing to release.
 *
 * ### Robustness
 * The asset is parsed up front and every length is validated against the bytes actually remaining, so a truncated or
 * corrupt file fails fast with [IllegalArgumentException] rather than a cryptic buffer underflow or an out-of-memory
 * allocation. [handleSendToRadio] likewise tolerates any inbound bytes (undecodable [ToRadio] is ignored, not thrown).
 * The bundled asset is trusted, but this same reader is a convenient injection point for malformed-input / fuzz testing
 * of the ingestion path — see `ReplayFuzz` in the test sources.
 */
class ReplayRadioTransport(
    private val callback: RadioTransportCallback,
    private val scope: CoroutineScope,
    val address: String,
    frames: ByteArray,
    private val packetDelayMs: Long = DEFAULT_PACKET_DELAY_MS,
) : RadioTransport {

    private val configFrames: List<ByteArray>
    private val nodeFrames: List<ByteArray>
    private val packetFrames: List<ByteArray>

    init {
        val buffer = Buffer().write(frames)
        configFrames = buffer.readSection(counted = true, label = "config")
        nodeFrames = buffer.readSection(counted = true, label = "node")
        packetFrames = buffer.readSection(counted = false, label = "packet")
    }

    private var packetsStarted = false

    override fun start() {
        Logger.i {
            "Starting replay transport: ${configFrames.size} config, ${nodeFrames.size} node, " +
                "${packetFrames.size} packet frames"
        }
        callback.onConnect()
    }

    override fun handleSendToRadio(p: ByteArray) {
        // Undecodable ToRadio is ignored rather than thrown: the replay must tolerate any bytes the app — or a fuzz
        // harness — hands it, exactly as it tolerates a malformed asset.
        val wantConfigId = runCatching { ToRadio.ADAPTER.decode(p).want_config_id }.getOrNull()
        when (wantConfigId) {
            HandshakeConstants.CONFIG_NONCE ->
                scope.handledLaunch {
                    emit(configFrames)
                    complete(HandshakeConstants.CONFIG_NONCE)
                }

            HandshakeConstants.NODE_INFO_NONCE ->
                scope.handledLaunch {
                    emit(nodeFrames)
                    complete(HandshakeConstants.NODE_INFO_NONCE)
                    if (!packetsStarted) {
                        packetsStarted = true
                        streamPackets()
                    }
                }
            // All other ToRadio traffic (heartbeats, outbound packets) is ignored — this is a read-only replay.
        }
    }

    private fun emit(frames: List<ByteArray>) = frames.forEach { callback.handleFromRadio(it) }

    private fun complete(nonce: Int) = callback.handleFromRadio(FromRadio(config_complete_id = nonce).encode())

    private suspend fun streamPackets() {
        Logger.d { "Replay streaming ${packetFrames.size} packets at ${packetDelayMs}ms spacing" }
        for (frame in packetFrames) {
            callback.handleFromRadio(frame)
            if (packetDelayMs > 0) delay(packetDelayMs)
        }
        Logger.i { "Replay finished (${packetFrames.size} packets)" }
    }

    override suspend fun close() {
        // Frames live in memory; the streaming coroutine is cancelled with the scope.
    }

    /**
     * Reads one section of the asset. A [counted] section is prefixed with a `u32` frame count; the final (packet)
     * section is uncounted and runs to end-of-buffer. Every frame length is checked against the bytes still available,
     * so a malformed/truncated asset throws [IllegalArgumentException] up front instead of underflowing the buffer or
     * allocating a multi-gigabyte array from a bogus length.
     */
    private fun Buffer.readSection(counted: Boolean, label: String): List<ByteArray> {
        val expected = if (counted) readUInt32("$label frame count") else Int.MAX_VALUE
        val frames = ArrayList<ByteArray>(if (counted) expected.coerceAtMost(MAX_PREALLOC) else 0)
        while (frames.size < expected && !exhausted()) {
            val len = readUInt32("$label[${frames.size}] length")
            require(len.toLong() <= size) {
                "Malformed replay asset: $label frame ${frames.size} declares $len bytes, only $size remain"
            }
            frames += readByteArray(len.toLong())
        }
        require(!counted || frames.size == expected) {
            "Malformed replay asset: $label section truncated — read ${frames.size} of $expected frames"
        }
        return frames
    }

    /** Reads a big-endian `u32` as a non-negative [Int], rejecting truncation and the >2 GiB high-bit range. */
    private fun Buffer.readUInt32(label: String): Int {
        if (size < INT_BYTES) throw IllegalArgumentException("Malformed replay asset: truncated $label")
        val value =
            try {
                readInt()
            } catch (e: EOFException) {
                throw IllegalArgumentException("Malformed replay asset: truncated $label", e)
            }
        require(value >= 0) { "Malformed replay asset: implausible $label ($value)" }
        return value
    }

    companion object {
        /** ~10 packets/sec, matching the burningmesh-replay steady-rate rig used for ingestion perf work. */
        const val DEFAULT_PACKET_DELAY_MS = 100L

        private const val INT_BYTES = 4

        /** Cap the up-front list capacity from a (validated but still attacker-influenced) count to avoid OOM. */
        private const val MAX_PREALLOC = 4096
    }
}
