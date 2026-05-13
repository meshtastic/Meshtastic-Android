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
package org.meshtastic.feature.node.component

import org.meshtastic.core.common.util.DateFormatter
import org.meshtastic.core.ui.component.determineSignalQuality
import org.meshtastic.core.ui.util.formatAgo

private const val MILLIS_PER_SECOND = 1000L
private const val MAX_BATTERY_PERCENT = 100
private const val SNR_UNSET_THRESHOLD = 100f

/** Builds a TalkBack-friendly description aggregating node state. Shared between [NodeItem] and [NodeItemCompact]. */
@Suppress("LongParameterList")
internal fun buildNodeDescription(
    name: String,
    isOnline: Boolean,
    isFavorite: Boolean,
    lastHeard: Int,
    role: String,
    hopsAway: Int,
    batteryLevel: Int?,
    distance: String?,
    snr: Float,
    rssi: Int,
    viaMqtt: Boolean,
    lastHeardIsRelative: Boolean = true,
): String = buildString {
    append(name)
    append(if (isOnline) ", online" else ", offline")
    if (isFavorite) append(", favorite")
    if (lastHeard > 0) {
        val timeText =
            if (lastHeardIsRelative) {
                formatAgo(lastHeard)
            } else {
                DateFormatter.formatDateTime(lastHeard.toLong() * MILLIS_PER_SECOND)
            }
        append(", last heard $timeText")
    }
    append(", role $role")
    if (hopsAway > 0) append(", $hopsAway hops away")
    batteryLevel?.let { if (it in 1..MAX_BATTERY_PERCENT) append(", battery $it%") }
    distance?.let { append(", $it away") }
    if (hopsAway == 0 && !viaMqtt && snr < SNR_UNSET_THRESHOLD && rssi < 0) {
        val quality = determineSignalQuality(snr, rssi)
        append(", signal ${quality.name.lowercase()}")
    }
}
