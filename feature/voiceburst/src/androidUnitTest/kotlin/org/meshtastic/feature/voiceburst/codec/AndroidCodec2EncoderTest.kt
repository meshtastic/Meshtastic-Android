/*
 * Copyright (c) 2026 Chris7X
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
package org.meshtastic.feature.voiceburst.codec

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt

import com.geeksville.mesh.voiceburst.Codec2JNI

/**
 * Unit tests for [AndroidCodec2Encoder].
 *
 * In CI/JVM environments (without libcodec2.so) all tests run against the STUB.
 * When JNI is available (device/emulator), [Codec2JNI.isAvailable] = true and
 * the tests verify the real codec.
 *
 * Tests are structured to pass in both modes:
 *   - Stub: verifies sizes and structural properties
 *   - Real JNI: also verifies audio quality (minimum SNR)
 */
class AndroidCodec2EncoderTest {

    // ─── Stub mode tests (always executed) ────────────────────────────────────

    @Test
    fun `encode returns non-null for valid PCM input`() {
        val encoder = AndroidCodec2Encoder()
        val pcm = generateSineWave(freq = 440f, durationSec = 1.0f, sampleRate = 8000)
        val encoded = encoder.encode(pcm)
        assertNotNull("encode() must not return null for valid input", encoded)
    }

    @Test
    fun `encode returns null for empty input`() {
        val encoder = AndroidCodec2Encoder()
        val result = encoder.encode(ShortArray(0))
        assertEquals("encode() must return null for empty input", null, result)
    }

    @Test
    fun `encode output size is within codec2 700B budget`() {
        val encoder = AndroidCodec2Encoder()
        // 1 second @ 8000 Hz = 8000 samples
        val pcm = generateSineWave(freq = 440f, durationSec = 1.0f, sampleRate = 8000)
        val encoded = encoder.encode(pcm)!!

        // Codec2 700B: max 100 bytes per 1 second (25 frames × 4 bytes)
        // Accepting up to 110 bytes to allow for frame rounding tolerance
        assertTrue(
            "Payload too large for LoRa: ${encoded.size} bytes > 110 (MVP budget limit)",
            encoded.size <= 110,
        )
        assertTrue(
            "Payload unexpectedly small: ${encoded.size} bytes",
            encoded.size >= 4,
        )
    }

    @Test
    fun `decode returns non-null for valid codec2 input`() {
        val encoder = AndroidCodec2Encoder()
        val pcm = generateSineWave(freq = 440f, durationSec = 1.0f, sampleRate = 8000)
        val encoded = encoder.encode(pcm)!!
        val decoded = encoder.decode(encoded)
        assertNotNull("decode() must not return null for valid input", decoded)
    }

    @Test
    fun `decode returns null for empty input`() {
        val encoder = AndroidCodec2Encoder()
        val result = encoder.decode(ByteArray(0))
        assertEquals("decode() must return null for empty input", null, result)
    }

    @Test
    fun `encode then decode roundtrip preserves length`() {
        val encoder = AndroidCodec2Encoder()
        val pcm = generateSineWave(freq = 440f, durationSec = 1.0f, sampleRate = 8000)
        val encoded = encoder.encode(pcm)!!
        val decoded = encoder.decode(encoded)!!

        // Decoded length may differ slightly due to frame padding,
        // but must be close to the original length
        val ratio = decoded.size.toDouble() / pcm.size.toDouble()
        assertTrue(
            "Decoded length (${decoded.size}) too far from original (${pcm.size}). Ratio: $ratio",
            ratio in 0.8..1.2,
        )
    }

    @Test
    fun `VoiceBurstPayload encodes and decodes correctly`() {
        val encoder = AndroidCodec2Encoder()
        val pcm = generateSineWave(freq = 440f, durationSec = 1.0f, sampleRate = 8000)
        val codec2Bytes = encoder.encode(pcm)!!

        // Simulate the complete payload serialization cycle
        val payload = org.meshtastic.feature.voiceburst.model.VoiceBurstPayload(
            version = 1,
            codecMode = 0,
            durationMs = 1000,
            audioData = codec2Bytes,
        )
        val wireBytes = payload.encode()
        val decodedPayload = org.meshtastic.feature.voiceburst.model.VoiceBurstPayload.decode(wireBytes)

        assertNotNull("VoiceBurstPayload.decode() must not return null", decodedPayload)
        assertEquals("version", payload.version, decodedPayload!!.version)
        assertEquals("codecMode", payload.codecMode, decodedPayload.codecMode)
        assertEquals("durationMs", payload.durationMs, decodedPayload.durationMs)
        assertArrayEquals("audioData", payload.audioData, decodedPayload.audioData)
    }

    @Test
    fun `payload size fits in single LoRa packet`() {
        val encoder = AndroidCodec2Encoder()
        val pcm = generateSineWave(freq = 440f, durationSec = 1.0f, sampleRate = 8000)
        val codec2Bytes = encoder.encode(pcm)!!

        val payload = org.meshtastic.feature.voiceburst.model.VoiceBurstPayload(
            version = 1,
            codecMode = 0,
            durationMs = 1000,
            audioData = codec2Bytes,
        )
        val wireBytes = payload.encode()

        // LoRa max MTU ~233 bytes. With mesh overhead: safe budget = 200 bytes.
        assertTrue(
            "Payload ${wireBytes.size} bytes exceeds LoRa budget (200 bytes)",
            wireBytes.size <= 200,
        )
    }

    // ─── JNI mode tests (executed only if Codec2Jni.isAvailable) ─────────────

    @Test
    fun `JNI roundtrip SNR above minimum threshold`() {
        Codec2JNI.ensureLoaded()
        if (!Codec2JNI.isAvailable) {
            // Skip gracefully in stub mode
            println("[SKIP] Codec2JNI not available — SNR test skipped (stub mode)")
            return
        }

        val encoder = AndroidCodec2Encoder()
        val original = generateSineWave(freq = 440f, durationSec = 1.0f, sampleRate = 8000)
        val encoded = encoder.encode(original)!!
        val decoded = encoder.decode(encoded)!!

        // Approximate SNR measurement on the reconstructed signal
        val snrDb = computeSnrDb(original, decoded)
        println("SNR Codec2 700B roundtrip: $snrDb dB")

        // Codec2 700B at 440Hz sinusoidal: we expect at least 5 dB SNR
        // (low threshold — Codec2 700B is a voice codec, not hi-fi)
        assertTrue(
            "SNR too low for Codec2 700B: $snrDb dB (minimum expected: 5 dB)",
            snrDb >= 5.0,
        )
    }

    @Test
    fun `JNI handle lifecycle create and destroy`() {
        Codec2JNI.ensureLoaded()
        if (!Codec2JNI.isAvailable) {
            println("[SKIP] Codec2JNI not available")
            return
        }
        val handle = Codec2JNI.create(Codec2JNI.MODE_700C)
        assertTrue("Handle must be != 0", handle != 0L)
        assertEquals("samplesPerFrame must be 320 for 700B", 320, Codec2JNI.getSamplesPerFrame(Codec2JNI.MODE_700C))
        assertTrue("bytesPerFrame must be > 0", Codec2JNI.getBytesPerFrame(Codec2JNI.MODE_700C) > 0)
        Codec2JNI.destroy(handle)
        // If we reach this point without a crash, the lifecycle is correct
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    /**
     * Generates a 16-bit PCM sine wave as a test signal.
     * Amplitude at 70% of Short.MAX_VALUE to simulate normalized voice input.
     */
    private fun generateSineWave(freq: Float, durationSec: Float, sampleRate: Int): ShortArray {
        val numSamples = (sampleRate * durationSec).toInt()
        val amplitude = Short.MAX_VALUE * 0.7
        return ShortArray(numSamples) { i ->
            val angle = 2.0 * PI * freq * i / sampleRate
            (sin(angle) * amplitude).toInt().toShort()
        }
    }

    /**
     * Computes the approximate Signal-to-Noise Ratio between two signals.
     * Signals must have similar lengths — truncates to the minimum.
     */
    private fun computeSnrDb(original: ShortArray, decoded: ShortArray): Double {
        val len = minOf(original.size, decoded.size)
        if (len == 0) return Double.NEGATIVE_INFINITY

        var signalPower = 0.0
        var noisePower = 0.0

        for (i in 0 until len) {
            val s = original[i].toDouble()
            val d = decoded[i].toDouble()
            signalPower += s * s
            noisePower  += (s - d) * (s - d)
        }

        if (noisePower < 1e-10) return Double.POSITIVE_INFINITY  // perfect decode
        return 10.0 * kotlin.math.log10(signalPower / noisePower)
    }
}
