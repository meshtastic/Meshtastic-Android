package org.meshtastic.core.database.model

import androidx.annotation.StringRes
import org.meshtastic.core.strings.R

enum class NodeSortOption(val sqlValue: String, @StringRes val stringRes: Int) {
    LAST_HEARD("last_heard", R.string.node_sort_last_heard),
    ALPHABETICAL("alpha", R.string.node_sort_alpha),
    DISTANCE("distance", R.string.node_sort_distance),
    HOPS_AWAY("hops_away", R.string.node_sort_hops_away),
    CHANNEL("channel", R.string.node_sort_channel),
    VIA_MQTT("via_mqtt", R.string.node_sort_via_mqtt),
    VIA_FAVORITE("via_favorite", R.string.node_sort_via_favorite),
}