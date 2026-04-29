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

package com.geeksville.mesh.voiceburst

import android.util.Log

/**
 * JNI binding to a prebuilt libcodec2 library.
 * Both shared objects (libcodec2.so + libcodec2_jni.so) must be present in jniLibs/.
 */
internal object Codec2JNI {

    private const val TAG = "Codec2JNI"
    private var loaded = false

    fun ensureLoaded() {
        if (!loaded) {
            try {
                System.loadLibrary("codec2")
                Log.i(TAG, "libcodec2.so loaded OK")
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "Failed to load libcodec2.so: ${e.message}")
                return
            }
            try {
                System.loadLibrary("codec2_jni")
                Log.i(TAG, "libcodec2_jni.so loaded OK — JNI active")
                loaded = true
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "Failed to load libcodec2_jni.so: ${e.message}")
                // loaded remains false -> fallback to stub
            }
        }
    }

    val isAvailable: Boolean
        get() = loaded

    // Codec2 operating modes
    const val MODE_3200 = 0
    const val MODE_2400 = 1
    const val MODE_1600 = 2
    const val MODE_1400 = 3
    const val MODE_1300 = 4
    const val MODE_1200 = 5
    const val MODE_700C = 8
    const val MODE_450  = 10

    @JvmStatic external fun getSamplesPerFrame(mode: Int): Int
    @JvmStatic external fun getBytesPerFrame(mode: Int): Int
    @JvmStatic external fun create(mode: Int): Long
    @JvmStatic external fun encode(ptr: Long, pcm: ShortArray): ByteArray
    @JvmStatic external fun decode(ptr: Long, frame: ByteArray): ShortArray
    @JvmStatic external fun destroy(ptr: Long)
}
