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

/**
 * Platform-agnostic interface for Codec2 encoding/decoding.
 *
 * The Android implementation ([AndroidCodec2Encoder]) uses JNI + libcodec2.
 * If the library is unavailable ([isStub]=true), it falls back to stub mode
 * (440Hz sine wave) to allow development and CI without the .so file.
 *
 * Implements [AutoCloseable]: call [close()] (or use `use {}`) to
 * release the JNI state when the codec is no longer needed.
 */
interface Codec2Encoder : AutoCloseable {

    /**
     * Encodes a 16-bit mono 8kHz PCM buffer into Codec2 700B bytes.
     *
     * @param pcmData PCM short array (16-bit, mono, 8000 Hz)
     * @return compressed Codec2 bytes, or null in case of error
     *
     * Expected dimensions:
     *   input:  8000 samples/s × 1s = 8000 shorts = 16000 bytes PCM
     *   output: ~88 bytes Codec2 700B per 1 second
     */
    fun encode(pcmData: ShortArray): ByteArray?

    /**
     * Decodes Codec2 700B bytes into 16-bit mono 8kHz PCM.
     *
     * @param codec2Data compressed bytes
     * @return PCM short array, or null in case of error
     */
    fun decode(codec2Data: ByteArray): ShortArray?

    /**
     * Indicates whether this implementation is functional (library available)
     * or a stub.
     */
    val isStub: Boolean
}
