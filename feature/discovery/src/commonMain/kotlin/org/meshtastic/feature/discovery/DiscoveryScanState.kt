/*
 * Copyright (c) 2025-2026 Meshtastic LLC
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
package org.meshtastic.feature.discovery

/**
 * State machine for a discovery scan lifecycle.
 *
 * ```
 * Idle → Shifting → [Reconnecting] → Dwell → Shifting (loop) → Analysis → Complete
 * Any scanning → Restoring → Idle
 * Reconnecting timeout → Paused
 * ```
 */
sealed interface DiscoveryScanState {
    /** No scan is active. */
    data object Idle : DiscoveryScanState

    /** Radio is switching to a new LoRa preset. */
    data class Shifting(val presetName: String) : DiscoveryScanState

    /** Waiting for the radio to reconnect after a preset change. */
    data class Reconnecting(val presetName: String) : DiscoveryScanState

    /** Listening on a preset and counting down the dwell timer. */
    data class Dwell(val presetName: String, val remainingSeconds: Long, val totalSeconds: Long) : DiscoveryScanState

    /** All presets scanned; aggregating results. */
    data object Analysis : DiscoveryScanState

    /** Scan finished and results are persisted. */
    data object Complete : DiscoveryScanState

    /** Scan paused due to an unrecoverable transient condition (e.g. reconnect timeout). */
    data class Paused(val reason: String) : DiscoveryScanState

    /** Restoring the home preset after scan stop or completion. */
    data object Restoring : DiscoveryScanState
}
