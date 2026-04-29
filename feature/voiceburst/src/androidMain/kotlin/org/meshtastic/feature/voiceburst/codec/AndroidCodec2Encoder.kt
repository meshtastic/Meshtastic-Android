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

import com.geeksville.mesh.voiceburst.Codec2JNI

import co.touchlab.kermit.Logger
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt

private const val TAG = "AndroidCodec2Encoder"

/**
 * Android implementation of [Codec2Encoder].
 *
 * When [Codec2JNI.isAvailable] is true, uses libcodec2 via JNI (real voice audio).
 * Otherwise falls back to STUB mode (440Hz sine wave) for development/CI/builds without .so.
 *
 * Codec2 700B parameters:
 *   - Sample rate input:  8000 Hz
 *   - Frame:              40ms = 320 samples
 *   - Bytes per frame:    4
 *   - 1 second:           25 frames x 4 bytes = 100 bytes
 *
 * Preprocessing applied before encoding (JNI mode only):
 *   1. Amplitude normalization (brings to 70% of Short.MAX_VALUE)
 *   2. Simple VAD: if RMS < threshold, returns null without encoding
 *
 * JNI Lifecycle:
 *   The Codec2 handle is created in the constructor and destroyed in [close()].
 *   Ensure to use [use { }] or call [close()] explicitly.
 */
class AndroidCodec2Encoder : Codec2Encoder, AutoCloseable {

    private val codec2Handle: Long
    override val isStub: Boolean

    init {
        Codec2JNI.ensureLoaded()
        if (Codec2JNI.isAvailable) {
            val handle = Codec2JNI.create(Codec2JNI.MODE_700C)
            if (handle != 0L) {
                codec2Handle = handle
                isStub = false
                Logger.i(tag = TAG) {
                    "Codec2 JNI OK: samplesPerFrame=${Codec2JNI.getSamplesPerFrame(Codec2JNI.MODE_700C)}" +
                        " bytesPerFrame=${Codec2JNI.getBytesPerFrame(Codec2JNI.MODE_700C)}"
                }
            } else {
                Logger.e(tag = TAG) { "Codec2JNI.create() returned 0 -- falling back to stub mode" }
                codec2Handle = 0L
                isStub = true
            }
        } else {
            codec2Handle = 0L
            isStub = true
            Logger.w(tag = TAG) { "Codec2 JNI not available -- stub mode (440Hz sine wave)" }
        }
    }

    override fun close() {
        if (codec2Handle != 0L) {
            Codec2JNI.destroy(codec2Handle)
            Logger.d(tag = TAG) { "Codec2 handle released" }
        }
    }

    // --- encode -------------------------------------------------------------

    /**
     * Encodes 16-bit mono 8000Hz PCM into Codec2 700B bytes.
     *
     * Accepts an array of any length -- it is split into frames
     * of [SAMPLES_PER_FRAME] samples. The last incomplete frame is
     * padded with zeros (zero-padding).
     *
     * @param pcmData  PCM samples from the microphone (8000 Hz, mono, signed 16-bit)
     * @return         ByteArray with Codec2 bytes, null if input is empty or silence detected
     */
    override fun encode(pcmData: ShortArray): ByteArray? {
        if (pcmData.isEmpty()) return null

        return if (!isStub && codec2Handle != 0L) {
            encodeJni(pcmData)
        } else {
            encodeStub(pcmData)
        }
    }

    private fun encodeJni(pcmData: ShortArray): ByteArray? {
        val samplesPerFrame = Codec2JNI.getSamplesPerFrame(Codec2JNI.MODE_700C)
        val bytesPerFrame   = Codec2JNI.getBytesPerFrame(Codec2JNI.MODE_700C)

        // Preprocessing: normalization
        val normalized = normalize(pcmData)

        // VAD: do not send silence -- return null so the ViewModel skips transmission
        val rms = computeRms(normalized)
        if (rms < SILENCE_RMS_THRESHOLD) {
            Logger.d(tag = TAG) { "VAD: silence detected (RMS=$rms) -- skipping encode" }
            return null
        }

        // Calculate needed frames (round up)
        val frameCount = (normalized.size + samplesPerFrame - 1) / samplesPerFrame
        val output = ByteArray(frameCount * bytesPerFrame)
        var outOffset = 0

        for (frameIdx in 0 until frameCount) {
            val inStart = frameIdx * samplesPerFrame
            val inEnd   = minOf(inStart + samplesPerFrame, normalized.size)

            // Extract frame (with zero-padding if incomplete)
            val frame = if (inEnd - inStart == samplesPerFrame) {
                normalized.copyOfRange(inStart, inEnd)
            } else {
                ShortArray(samplesPerFrame).also {
                    normalized.copyInto(it, 0, inStart, inEnd)
                }
            }

            val encoded = Codec2JNI.encode(codec2Handle, frame)
            if (encoded == null || encoded.size != bytesPerFrame) {
                Logger.e(tag = TAG) { "Encode failed at frame $frameIdx" }
                return null
            }

            encoded.copyInto(output, outOffset)
            outOffset += bytesPerFrame
        }

        Logger.d(tag = TAG) {
            "Encode JNI: ${pcmData.size} samples -> ${output.size} bytes " +
                "($frameCount frames x $bytesPerFrame bytes)"
        }
        return output
    }

    // --- decode -------------------------------------------------------------

    /**
     * Decodes Codec2 700B bytes into 16-bit mono 8000Hz PCM samples.
     *
     * @param codec2Data  ByteArray of Codec2 bytes (multiple of bytesPerFrame)
     * @return            ShortArray of PCM samples, null if input is empty/invalid
     */
    override fun decode(codec2Data: ByteArray): ShortArray? {
        if (codec2Data.isEmpty()) return null

        return if (!isStub && codec2Handle != 0L) {
            decodeJni(codec2Data)
        } else {
            decodeStub(codec2Data)
        }
    }

    private fun decodeJni(codec2Data: ByteArray): ShortArray? {
        val samplesPerFrame = Codec2JNI.getSamplesPerFrame(Codec2JNI.MODE_700C)
        val bytesPerFrame   = Codec2JNI.getBytesPerFrame(Codec2JNI.MODE_700C)

        if (codec2Data.size % bytesPerFrame != 0) {
            Logger.w(tag = TAG) {
                "Decode: input size (${codec2Data.size}) not a multiple of " +
                    "bytesPerFrame ($bytesPerFrame) -- truncating to complete frame"
            }
        }

        val frameCount = codec2Data.size / bytesPerFrame
        if (frameCount == 0) return null

        val output = ShortArray(frameCount * samplesPerFrame)
        var outOffset = 0

        for (frameIdx in 0 until frameCount) {
            val inStart = frameIdx * bytesPerFrame
            val frame   = codec2Data.copyOfRange(inStart, inStart + bytesPerFrame)

            val decoded = Codec2JNI.decode(codec2Handle, frame)
            if (decoded == null || decoded.size != samplesPerFrame) {
                Logger.e(tag = TAG) { "Decode failed at frame $frameIdx" }
                return null
            }

            decoded.copyInto(output, outOffset)
            outOffset += samplesPerFrame
        }

        Logger.d(tag = TAG) {
            "Decode JNI: ${codec2Data.size} bytes -> ${output.size} samples " +
                "($frameCount frames x $samplesPerFrame samples)"
        }
        return output
    }

    // --- Preprocessing helpers ----------------------------------------------

    /**
     * Normalizes the signal amplitude to [TARGET_AMPLITUDE] x Short.MAX_VALUE.
     * Prevents clipping and improves Codec2 quality on low-volume voices.
     */
    private fun normalize(pcm: ShortArray): ShortArray {
        val maxAmp = pcm.maxOfOrNull { abs(it.toInt()) }?.toFloat() ?: return pcm
        if (maxAmp < 1f) return pcm  // absolute silence

        val gain = (TARGET_AMPLITUDE * Short.MAX_VALUE) / maxAmp
        val clampedGain = minOf(gain, MAX_GAIN)

        return ShortArray(pcm.size) { i ->
            (pcm[i] * clampedGain).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
    }

    /**
     * Computes the Root Mean Square of the signal.
     * Used for simple VAD: RMS < [SILENCE_RMS_THRESHOLD] = silence.
     */
    private fun computeRms(pcm: ShortArray): Double {
        if (pcm.isEmpty()) return 0.0
        val sumSquares = pcm.fold(0.0) { acc, s -> acc + (s.toDouble() * s.toDouble()) }
        return sqrt(sumSquares / pcm.size)
    }

    // --- Stub (fallback when JNI is not available) --------------------------

    private fun encodeStub(pcmData: ShortArray): ByteArray {
        val frameCount = (pcmData.size + SAMPLES_PER_FRAME - 1) / SAMPLES_PER_FRAME
        Logger.w(tag = TAG) {
            "Codec2 STUB encode: ${pcmData.size} samples -> ${frameCount * BYTES_PER_FRAME} bytes (zeros)"
        }
        return ByteArray(frameCount * BYTES_PER_FRAME) { 0x00 }
    }

    private fun decodeStub(codec2Data: ByteArray): ShortArray {
        val frameCount = maxOf(1, codec2Data.size / BYTES_PER_FRAME)
        val totalSamples = frameCount * SAMPLES_PER_FRAME

        Logger.w(tag = TAG) {
            "Codec2 STUB decode: ${codec2Data.size} bytes -> $totalSamples samples (440Hz sine wave)"
        }

        // Generate 440Hz sine wave (A4) -- audible and recognizable
        val sampleRate = 8000.0
        val frequency  = 440.0
        val amplitude  = Short.MAX_VALUE * 0.3  // 30% volume

        return ShortArray(totalSamples) { i ->
            val angle = 2.0 * PI * frequency * i / sampleRate
            (sin(angle) * amplitude).toInt().toShort()
        }
    }

    companion object {
        /** Codec2 700B: 320 samples per frame (40ms @ 8000 Hz). */
        const val SAMPLES_PER_FRAME = 320

        /** Codec2 700B: 4 bytes per frame (700 bps rounded). */
        const val BYTES_PER_FRAME = 4

        /** Target amplitude for normalization (70% of Short.MAX_VALUE). */
        private const val TARGET_AMPLITUDE = 0.70f

        /** Maximum gain applied by normalization (10x). */
        private const val MAX_GAIN = 10.0f

        /**
         * RMS threshold below which the frame is considered silence (simple VAD).
         * 200.0 on the 0-32767 scale is approximately -44 dBFS -- normal voice is 2000-8000.
         */
        private const val SILENCE_RMS_THRESHOLD = 200.0
    }
}
