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

import android.text.Spannable
import android.text.SpannableString
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.CarColor
import androidx.car.app.model.CarIcon
import androidx.car.app.model.CarText
import androidx.car.app.model.ForegroundCarColorSpan
import androidx.car.app.model.Header
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.core.graphics.drawable.IconCompat
import org.meshtastic.core.common.util.DateFormatter
import org.meshtastic.core.model.nodeColorsFromNum
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
        val listBuilder = ItemList.Builder()

        // Nodes already sorted by CarStateCoordinator (online-first, then by lastHeard)
        state.nodes.forEach { node ->
            val (_, nodeColor) = nodeColorsFromNum(node.nodeNum)
            val tintedIcon = CarIcon.Builder(baseIcon).setTint(CarColor.createCustom(nodeColor, nodeColor)).build()
            listBuilder.addItem(
                Row.Builder()
                    .setTitle(node.longName)
                    .addText(formatNodeSubtitle(node))
                    .setImage(tintedIcon, Row.IMAGE_TYPE_ICON)
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

    private fun formatNodeSubtitle(node: NodeUi): CarText {
        val signalLabel = signalLabel(node.signalQuality)
        val battery = node.batteryPercent?.let { " • $it%" } ?: ""
        val lastHeard =
            if (node.lastHeard != 0L) {
                " • ${DateFormatter.formatRelativeTime(node.lastHeard)}"
            } else {
                ""
            }
        val status = if (!node.isOnline) " • ${carContext.getString(R.string.car_status_offline)}" else ""
        val full = "$signalLabel$battery$lastHeard$status"

        val spannable = SpannableString(full)
        val signalColor = signalColor(node.signalQuality)
        spannable.setSpan(
            ForegroundCarColorSpan.create(signalColor),
            0,
            signalLabel.length,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
        )
        return CarText.Builder(spannable).build()
    }

    private fun signalLabel(quality: SignalQuality): String = when (quality) {
        SignalQuality.EXCELLENT -> carContext.getString(R.string.car_signal_excellent)
        SignalQuality.GOOD -> carContext.getString(R.string.car_signal_good)
        SignalQuality.FAIR -> carContext.getString(R.string.car_signal_fair)
        SignalQuality.BAD -> carContext.getString(R.string.car_signal_bad)
        SignalQuality.NONE -> carContext.getString(R.string.car_signal_none)
    }

    private fun signalColor(quality: SignalQuality): CarColor = when (quality) {
        SignalQuality.EXCELLENT -> CarColor.GREEN
        SignalQuality.GOOD -> CarColor.GREEN
        SignalQuality.FAIR -> CarColor.YELLOW
        SignalQuality.BAD -> CarColor.RED
        SignalQuality.NONE -> CarColor.SECONDARY
    }
}
