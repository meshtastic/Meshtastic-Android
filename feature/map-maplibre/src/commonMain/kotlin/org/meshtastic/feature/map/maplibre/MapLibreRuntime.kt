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

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Whether MapLibre's native rendering engine can actually load on this device.
 *
 * MapLibre Compose reaches its C++ engine through a JNI shim, and an app can be built for an architecture that shim was
 * never published for. maplibre-native-ffi 0.202608.3 shipped arm64-v8a and x86_64 only, so an armeabi-v7a build
 * carried no engine and the first frame of a map died with `UnsatisfiedLinkError` — see #7001 and #7005. A missing
 * renderer is not worth a crash when every other screen still works, so the map screens ask first and say so instead.
 *
 * maplibre-compose 0.16.0 closed that gap: its FFI ships armeabi-v7a too, so every ABI the app builds now carries an
 * engine and this answers `true` everywhere. The probe stays as the cheap defence it always was — it is one lazy
 * `System.loadLibrary` and it fails soft — but nothing is expected to reach the unavailable path any more.
 *
 * Off Android there is no such gap, and the actuals there answer `true` unconditionally.
 */
internal expect fun isMapLibreRuntimeAvailable(): Boolean

/**
 * Whether [load] brought the engine in. Wraps the call rather than catching a type: a missing library raises
 * `UnsatisfiedLinkError`, which is an `Error`, not an `Exception` — a `catch (e: Exception)` would let the crash
 * straight through, which is the bug this guards against.
 */
internal fun probeNativeRuntime(load: () -> Unit): Boolean = runCatching(load).isSuccess

/**
 * The probe the map screens consult, as a composition local so a test can drive the unavailable path without a device
 * that lacks the engine. Defaults to the real [isMapLibreRuntimeAvailable].
 */
@Suppress("CompositionLocalAllowlist")
internal val LocalMapLibreRuntimeProbe = staticCompositionLocalOf<() -> Boolean> { ::isMapLibreRuntimeAvailable }
