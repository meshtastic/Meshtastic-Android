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
package org.meshtastic.core.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.common.util.DateFormatter
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.a11y_node_battery
import org.meshtastic.core.resources.a11y_node_distance_away
import org.meshtastic.core.resources.a11y_node_favorite
import org.meshtastic.core.resources.a11y_node_hops_away
import org.meshtastic.core.resources.a11y_node_last_heard
import org.meshtastic.core.resources.a11y_node_offline
import org.meshtastic.core.resources.a11y_node_online
import org.meshtastic.core.resources.a11y_node_role
import org.meshtastic.core.resources.a11y_node_signal
import org.meshtastic.core.ui.util.formatAgo

private const val MILLIS_PER_SECOND = 1000L
private const val MAX_BATTERY_PERCENT = 100
private const val SNR_UNSET_THRESHOLD = 100f

/** Pre-resolved localized strings for TalkBack node descriptions. */
@Immutable
internal data class NodeDescriptionStrings(
    val online: String,
    val offline: String,
    val favorite: String,
    val lastHeard: String,
    val role: String,
    val hopsAway: String,
    val battery: String,
    val distanceAway: String,
    val signal: String,
)

/** Resolves [NodeDescriptionStrings] from Compose string resources. */
@Composable
internal fun rememberNodeDescriptionStrings(): NodeDescriptionStrings = NodeDescriptionStrings(
    online = stringResource(Res.string.a11y_node_online),
    offline = stringResource(Res.string.a11y_node_offline),
    favorite = stringResource(Res.string.a11y_node_favorite),
    lastHeard = stringResource(Res.string.a11y_node_last_heard, "%s"),
    role = stringResource(Res.string.a11y_node_role, "%s"),
    hopsAway = stringResource(Res.string.a11y_node_hops_away, 0),
    battery = stringResource(Res.string.a11y_node_battery, 0),
    distanceAway = stringResource(Res.string.a11y_node_distance_away, "%s"),
    signal = stringResource(Res.string.a11y_node_signal, "%s"),
)

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
    strings: NodeDescriptionStrings,
    lastHeardIsRelative: Boolean = true,
): String = buildString {
    append(name)
    append(", ")
    append(if (isOnline) strings.online else strings.offline)
    if (isFavorite) {
        append(", ")
        append(strings.favorite)
    }
    if (lastHeard > 0) {
        val timeText =
            if (lastHeardIsRelative) {
                formatAgo(lastHeard)
            } else {
                DateFormatter.formatDateTime(lastHeard.toLong() * MILLIS_PER_SECOND)
            }
        append(", ")
        append(strings.lastHeard.replace("%s", timeText))
    }
    append(", ")
    append(strings.role.replace("%s", role))
    if (hopsAway > 0) {
        append(", ")
        append(strings.hopsAway.replace("0", hopsAway.toString()))
    }
    batteryLevel?.let {
        if (it in 1..MAX_BATTERY_PERCENT) {
            append(", ")
            append(strings.battery.replace("0", it.toString()))
        }
    }
    distance?.let {
        append(", ")
        append(strings.distanceAway.replace("%s", it))
    }
    if (hopsAway == 0 && !viaMqtt && snr < SNR_UNSET_THRESHOLD && rssi < 0) {
        val quality = determineSignalQuality(snr, rssi)
        append(", ")
        append(strings.signal.replace("%s", quality.name.lowercase()))
    }
}
