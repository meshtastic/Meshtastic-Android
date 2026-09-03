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
package org.meshtastic.core.ui.util

import androidx.compose.runtime.compositionLocalOf

/**
 * The connected device's own most recently reported noise floor, in dBm (from its `LocalStats` telemetry), provided
 * once at the app root (see MeshtasticAppShell) so signal quality can be rated against it without threading it through
 * every node/message composable. This is always *our* receiving radio's noise floor — the connected node is the one
 * that heard any remote node's packets being rated, regardless of which remote node's signal is displayed.
 *
 * Null before a device connects, in previews/tests, and when the connected node has not yet reported a reading
 * (`LocalStats.noise_floor == 0`, the firmware's "no reading yet" sentinel — normalized to null at the source so
 * callers never need to know about it).
 */
@Suppress("CompositionLocalAllowlist")
val LocalNoiseFloor = compositionLocalOf<Int?> { null }
