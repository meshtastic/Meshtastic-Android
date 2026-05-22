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
package org.meshtastic.feature.car.screens

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.CarColor
import androidx.car.app.model.CarIcon
import androidx.car.app.model.Header
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.core.graphics.drawable.IconCompat
import org.meshtastic.core.common.util.DateFormatter
import org.meshtastic.feature.car.R
import org.meshtastic.feature.car.model.NodeDashboardUiState
import org.meshtastic.feature.car.model.NodeUi
import org.meshtastic.feature.car.model.SignalQuality

class NodeDashboardScreen(
    carContext: CarContext,
    private val stateProvider: () -> NodeDashboardUiState,
    private val onNodeClick: (Int) -> Unit,
) : Screen(carContext) {

    override fun onGetTemplate(): Template {
        val state = stateProvider()

        if (state.nodes.isEmpty()) {
            return ListTemplate.Builder()
                .setLoading(false)
                .setSingleList(
                    ItemList.Builder().setNoItemsMessage(carContext.getString(R.string.car_no_nodes)).build(),
                )
                .setHeader(
                    Header.Builder()
                        .setTitle(carContext.getString(R.string.car_tab_nodes))
                        .setStartHeaderAction(Action.BACK)
                        .build(),
                )
                .build()
        }

        val header = state.topologyHeader
        val headerTitle = carContext.getString(R.string.car_nodes_online, header.onlineNodes)

        val baseIcon = IconCompat.createWithResource(carContext, R.drawable.ic_car_nodes)
        val onlineIcon = CarIcon.Builder(baseIcon).setTint(CarColor.GREEN).build()
        val offlineIcon = CarIcon.Builder(baseIcon).build()
        val listBuilder = ItemList.Builder()

        // Nodes already sorted by CarStateCoordinator (online-first, then by lastHeard)
        state.nodes.forEach { node ->
            listBuilder.addItem(
                Row.Builder()
                    .setTitle(node.longName)
                    .addText(formatNodeSubtitle(node))
                    .setImage(if (node.isOnline) onlineIcon else offlineIcon, Row.IMAGE_TYPE_ICON)
                    .setBrowsable(true)
                    .setOnClickListener { onNodeClick(node.nodeNum) }
                    .build(),
            )
        }

        return ListTemplate.Builder()
            .setSingleList(listBuilder.build())
            .setHeader(Header.Builder().setTitle(headerTitle).setStartHeaderAction(Action.BACK).build())
            .build()
    }

    private fun formatNodeSubtitle(node: NodeUi): String {
        val signal =
            when (node.signalQuality) {
                SignalQuality.EXCELLENT -> carContext.getString(R.string.car_signal_excellent)
                SignalQuality.GOOD -> carContext.getString(R.string.car_signal_good)
                SignalQuality.FAIR -> carContext.getString(R.string.car_signal_fair)
                SignalQuality.BAD -> carContext.getString(R.string.car_signal_bad)
                SignalQuality.NONE -> carContext.getString(R.string.car_signal_none)
            }
        val battery = node.batteryPercent?.let { " • $it%" } ?: ""
        val lastHeard =
            if (node.lastHeard != 0L) {
                " • ${DateFormatter.formatRelativeTime(node.lastHeard)}"
            } else {
                ""
            }
        val status = if (!node.isOnline) " • ${carContext.getString(R.string.car_status_offline)}" else ""
        return "$signal$battery$lastHeard$status"
    }
}
