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
package org.meshtastic.feature.widget

import org.meshtastic.core.model.Node
import org.meshtastic.proto.DeviceMetrics
import org.meshtastic.proto.Position
import org.meshtastic.proto.User

@Suppress("LongParameterList")
internal fun testNode(
    num: Int,
    shortName: String = "N$num",
    longName: String = "Node $num",
    lastHeard: Int = 0,
    hopsAway: Int = 0,
    isFavorite: Boolean = false,
    isIgnored: Boolean = false,
    battery: Int? = null,
    latitudeI: Int? = null,
    longitudeI: Int? = null,
    snr: Float = Node.SNR_UNSET,
    rssi: Int = Node.RSSI_UNSET,
    viaMqtt: Boolean = false,
) = Node(
    num = num,
    user = User.Builder().id("!%08x".format(num)).short_name(shortName).long_name(longName).build(),
    position = Position.Builder().latitude_i(latitudeI).longitude_i(longitudeI).build(),
    deviceMetrics = DeviceMetrics.Builder().battery_level(battery).build(),
    lastHeard = lastHeard,
    hopsAway = hopsAway,
    isFavorite = isFavorite,
    isIgnored = isIgnored,
    snr = snr,
    rssi = rssi,
    viaMqtt = viaMqtt,
)
