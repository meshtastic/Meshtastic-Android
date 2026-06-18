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

import okio.Buffer
import okio.ByteString.Companion.toByteString
import org.meshtastic.proto.Data
import org.meshtastic.proto.FromRadio
import org.meshtastic.proto.MeshPacket
import org.meshtastic.proto.NodeInfo
import org.meshtastic.proto.Position
import org.meshtastic.proto.User
import kotlin.random.Random

/**
 * Deterministic fuzz toolkit for the on-device replay ingestion path — the same `FromRadio` entry every BLE / TCP /
 * serial peer feeds (`RadioTransportCallback.handleFromRadio`). It ships with the app's tests (runs under `allTests`),
 * so the parse / decode boundary is continuously fuzzed in CI at zero runtime and zero app-size cost.
 *
 * Untrusted radio input is a real attack surface: a malicious or buggy peer within range controls these bytes. This
 * toolkit drives that surface with three input strategies, each aimed at a different layer:
 * - [randomBytes] — unstructured noise; worst case for the stream/asset parser and the protobuf decoder.
 * - [mutate] — bit-flips / truncations / extensions of a *valid* encoding; near-valid corruption that reaches deep
 *   field-parsing paths a random blob never would.
 * - [adversarialFromRadio] — a structurally **valid** `FromRadio` carrying hostile field values (out-of-range
 *   coordinates, NaN / infinite metrics, oversized / RTL / emoji names, random sub-payloads). This is the corpus for
 *   the layer where the real robustness gap lives: the post-decode handlers, which are not exception-isolated.
 *
 * **Everything is seeded.** A failing case is fully described by its integer seed, so a CI failure reproduces locally
 * by pinning that seed — there is no hidden global RNG. Typical use:
 * ```
 * ReplayFuzz.forSeeds { random, seed ->
 *     val input = ReplayFuzz.mutate(random, validFrame)
 *     val error = runCatching { target(input) }.exceptionOrNull()
 *     assertTrue(error == null || error is IllegalArgumentException) { "seed=$seed -> $error" }
 * }
 * ```
 *
 * Scope: this is a *library-level* fuzzer (parser, decoder, handshake). End-to-end fuzzing of the live stack —
 * framing + UI + ANR — belongs in the capture tool's live `--fuzz` mode against a device; handler-level fuzzing needs
 * the full `MeshMessageProcessor` graph and is a separate integration harness. The [adversarialFromRadio] corpus here
 * is designed to feed that harness when it lands.
 */
@Suppress("MagicNumber")
object ReplayFuzz {

    /** Default seed sweep per invariant. Each seed is microseconds of work, so this stays well within CI budgets. */
    const val DEFAULT_ITERATIONS = 1000

    private const val MAX_RANDOM_LEN = 2048
    private const val MAX_NAME_LEN = 8192
    private const val MAX_PAYLOAD_LEN = 256

    /** Runs [body] over the deterministic seed sweep `0 until [count]`; each seed gets its own pinned [Random]. */
    inline fun forSeeds(count: Int = DEFAULT_ITERATIONS, body: (random: Random, seed: Int) -> Unit) {
        for (seed in 0 until count) body(Random(seed), seed)
    }

    /** Unstructured noise of bounded length (bounded so a fuzz sweep can never OOM the test JVM). */
    fun randomBytes(random: Random, maxLen: Int = MAX_RANDOM_LEN): ByteArray =
        ByteArray(random.nextInt(maxLen + 1)).also(random::nextBytes)

    /**
     * A bit-flipped / truncated / extended copy of [input] — near-valid corruption that exercises field-parsing paths
     * while staying small enough to be safe in CI. Returns fresh noise if [input] is empty.
     */
    fun mutate(random: Random, input: ByteArray): ByteArray {
        if (input.isEmpty()) return randomBytes(random, 16)
        val keep = random.nextInt(input.size + 1) // truncate to a 0..size prefix
        val tail = if (random.nextBoolean()) randomBytes(random, 16) else ByteArray(0) // sometimes extend
        val out = input.copyOf(keep) + tail
        repeat(random.nextInt(1, 4)) {
            // flip 1..3 bits
            if (out.isNotEmpty()) {
                val i = random.nextInt(out.size)
                out[i] = (out[i].toInt() xor (1 shl random.nextInt(8))).toByte()
            }
        }
        return out
    }

    /** A valid `FromRadio` carrying hostile content. Variant chosen by [random]. Corpus for handler/UI fuzzing. */
    fun adversarialFromRadio(random: Random): FromRadio = if (random.nextBoolean()) {
        FromRadio(node_info = adversarialNode(random))
    } else {
        FromRadio(packet = adversarialPacket(random))
    }

    /** A NodeInfo with extreme identity, signal, and location values — the kind a malicious node could advertise. */
    fun adversarialNode(random: Random): NodeInfo = NodeInfo(
        num = random.nextInt(),
        snr = HOSTILE_FLOATS.random(random),
        last_heard = random.nextInt(),
        hops_away = random.nextInt(),
        user =
        User(
            id = "!${random.nextInt().toUInt().toString(16)}",
            long_name = hostileString(random),
            short_name = hostileString(random),
        ),
        // Unconstrained, so trivially outside the valid +/-90 deg / +/-180 deg ranges (stored x 1e7).
        position = Position(latitude_i = random.nextInt(), longitude_i = random.nextInt()),
    )

    /** A MeshPacket with extreme envelope fields and a random (malformed-for-its-portnum) decoded payload. */
    fun adversarialPacket(random: Random): MeshPacket = MeshPacket(
        id = random.nextInt(),
        to = random.nextInt(),
        channel = random.nextInt(),
        rx_time = random.nextInt(),
        hop_limit = random.nextInt(),
        decoded = Data(payload = randomBytes(random, MAX_PAYLOAD_LEN).toByteString()),
    )

    /** Encodes the three replay sections (mirrors `replay_server.py --export` / [ReplayRadioTransport]'s reader). */
    fun asset(config: List<FromRadio>, nodes: List<FromRadio>, packets: List<FromRadio>): ByteArray {
        val buffer = Buffer()
        fun section(frames: List<FromRadio>, counted: Boolean) {
            if (counted) buffer.writeInt(frames.size)
            frames.forEach { frame ->
                val bytes = frame.encode()
                buffer.writeInt(bytes.size)
                buffer.write(bytes)
            }
        }
        section(config, counted = true)
        section(nodes, counted = true)
        section(packets, counted = false)
        return buffer.readByteArray()
    }

    private val HOSTILE_FLOATS =
        listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.MAX_VALUE, -Float.MAX_VALUE)

    // Built from code points (not literals) so the source stays ASCII — no raw bidi control to trip "trojan source"
    // linters or alarm reviewers.
    private fun hostileString(random: Random): String = when (random.nextInt(4)) {
        0 -> RTL_OVERRIDE.repeat(random.nextInt(1, 8))

        // U+202E right-to-left override runs
        1 -> FIRE_EMOJI.repeat(random.nextInt(1, 64))

        // astral emoji (surrogate pairs)
        2 -> "A".repeat(random.nextInt(MAX_NAME_LEN))

        // oversized name
        else -> "" // empty
    }

    private val RTL_OVERRIDE = Char(0x202E).toString()
    private val FIRE_EMOJI = "${Char(0xD83D)}${Char(0xDD25)}" // U+1F525 as a UTF-16 surrogate pair
}
