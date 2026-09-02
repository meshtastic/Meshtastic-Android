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
package org.meshtastic.feature.map.maplibre

/**
 * The JNI shim MapLibre Compose loads before its first frame.
 *
 * Taken from the `jni/` listing of `org.maplibre.nativeffi:maplibre-native-ffi-android` and from the crash in #7001,
 * which names this exact file. It is the shim rather than the 13 MB engine because the shim's `DT_NEEDED` pulls the
 * engine in behind it, so one load answers for both. **Re-check this name when maplibre-compose is bumped** — a rename
 * upstream would make the probe fail closed and hide the map on every device.
 */
private const val MAPLIBRE_JNI_LIBRARY = "jniMaplibreNativeC"

private val runtimeAvailable: Boolean by lazy {
    // runCatching, not catch (Exception): a missing library raises UnsatisfiedLinkError, which is an Error.
    // System.loadLibrary is process-wide and idempotent, so MapLibre's own load later finds this one already done.
    runCatching { System.loadLibrary(MAPLIBRE_JNI_LIBRARY) }.isSuccess
}

internal actual fun isMapLibreRuntimeAvailable(): Boolean = runtimeAvailable
