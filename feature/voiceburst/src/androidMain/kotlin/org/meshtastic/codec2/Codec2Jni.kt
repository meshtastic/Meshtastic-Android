/*
 * Copyright (c) 2026 Chris7X
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, see <https://www.gnu.org/licenses/>.
 */
package org.meshtastic.codec2

/**
 * JNI wrapper for the Codec2 library.
 * This class is the interface between Kotlin/JVM and the C codec logic.
 */
class Codec2Jni {

    /**
     * Encodes 16-bit mono PCM audio (8kHz) into Codec2 compressed frames.
     * @param pcm Input audio data (ShortArray)
     * @return Compressed byte array or null on error
     */
    external fun encode(pcm: ShortArray): ByteArray?

    /**
     * Decodes Codec2 compressed frames back into 16-bit mono PCM audio (8kHz).
     * @param compressed Compressed audio data (ByteArray)
     * @return Decoded ShortArray or null on error
     */
    external fun decode(compressed: ByteArray): ShortArray?

    /**
     * Gets the current Codec2 mode (e.g., 3200, 2400, etc.).
     */
    external fun getMode(): Int

    companion object {
        init {
            try {
                System.loadLibrary("codec2_jni")
            } catch (e: UnsatisfiedLinkError) {
                // Logger not available in this core-module, using println
                println("Critical: Could not load codec2_jni library")
            }
        }
    }
}
