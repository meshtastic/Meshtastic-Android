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
@file:Suppress("MatchingDeclarationName") // File groups the classifier function and its result type.

package org.meshtastic.core.ble

/**
 * wasmJs counterpart to `nonWebMain`'s [BleExceptionInfo]/`classifyBleException` — independent declarations in disjoint
 * compilations, not `expect`/`actual` (same shape as `BleServiceExtensions.kt`), since Kable does not exist on this
 * target at all and there is nothing to share.
 */
data class BleExceptionInfo(val isPermanent: Boolean, val gattStatus: Int? = null, val message: String)

/**
 * There is no Kable on wasmJs — Web Bluetooth throws its own exception types (`DOMException` and friends), never
 * Kable's — so nothing here is ever a "known Kable exception". Mirrors the nonWebMain classifier's own `else -> null`
 * branch for every throwable; this is the honest answer for this platform, not a stub that lies.
 */
fun Throwable.classifyBleException(): BleExceptionInfo? = null
