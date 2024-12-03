/*
 * Copyright (c) 2024 Meshtastic LLC
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

package com.geeksville.mesh.model

import androidx.annotation.StringRes
import com.geeksville.mesh.R

enum class NodeSortOption(val sqlValue: String, @StringRes val stringRes: Int) {
    LAST_HEARD("last_heard", R.string.node_sort_last_heard),
    ALPHABETICAL("alpha", R.string.node_sort_alpha),
    DISTANCE("distance", R.string.node_sort_distance),
    HOPS_AWAY("hops_away", R.string.node_sort_hops_away),
    CHANNEL("channel", R.string.node_sort_channel),
    VIA_MQTT("via_mqtt", R.string.node_sort_via_mqtt),
}
