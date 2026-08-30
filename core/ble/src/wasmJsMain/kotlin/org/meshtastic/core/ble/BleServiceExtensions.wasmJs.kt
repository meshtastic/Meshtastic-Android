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
package org.meshtastic.core.ble

/**
 * wasmJs counterpart of `nonWebMain`'s `BleServiceExtensions.kt` — same function name and signature, backed by
 * [WebMeshtasticRadioProfile] instead of the Kable-based `KableMeshtasticRadioProfile`.
 *
 * This isn't an `expect`/`actual` pair with the `nonWebMain` version: `BleService.toMeshtasticRadioProfile()` in
 * `nonWebMain` is an ordinary top-level extension function (not `expect`), so both source sets simply declare the same
 * signature independently — each visible only to its own compilation, exactly like the two `BleService` implementations
 * ([KableBleService][com.juul.kable] vs [WebBleService]) they wrap.
 */
fun BleService.toMeshtasticRadioProfile(): MeshtasticRadioProfile = WebMeshtasticRadioProfile(this)
