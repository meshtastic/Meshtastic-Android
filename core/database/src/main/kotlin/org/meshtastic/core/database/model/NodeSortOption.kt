/*
 * Copyright (c) 2025 Meshtastic LLC
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

package org.meshtastic.core.database.model

import androidx.annotation.StringRes
import org.meshtastic.core.strings.R as Res

enum class NodeSortOption(val sqlValue: String, @StringRes val stringRes: Int) {
    LAST_HEARD("last_heard", Res.string.node_sort_last_heard),
    ALPHABETICAL("alpha", Res.string.node_sort_alpha),
    DISTANCE("distance", Res.string.node_sort_distance),
    HOPS_AWAY("hops_away", Res.string.node_sort_hops_away),
    CHANNEL("channel", Res.string.node_sort_channel),
    VIA_MQTT("via_mqtt", Res.string.node_sort_via_mqtt),
    VIA_FAVORITE("via_favorite", Res.string.node_sort_via_favorite),
}
