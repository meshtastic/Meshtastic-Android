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
import androidx.car.app.model.Header
import androidx.car.app.model.Pane
import androidx.car.app.model.PaneTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import org.meshtastic.core.common.util.DateFormatter
import org.meshtastic.feature.car.R
import org.meshtastic.feature.car.model.NodeUi
import org.meshtastic.feature.car.model.SignalQuality

class NodeDetailScreen(
    carContext: CarContext,
    private val nodeProvider: () -> NodeUi?,
    private val onMessageClick: (Int) -> Unit,
) : Screen(carContext) {

    override fun onGetTemplate(): Template {
        val node = nodeProvider() ?: return buildErrorTemplate()

        val paneBuilder = Pane.Builder()

        paneBuilder.addRow(
            Row.Builder()
                .setTitle(carContext.getString(R.string.car_status_signal))
                .addText(formatSignal(node.signalQuality))
                .build(),
        )

        node.batteryPercent?.let { battery ->
            paneBuilder.addRow(
                Row.Builder().setTitle(carContext.getString(R.string.car_status_battery)).addText("$battery%").build(),
            )
        }

        paneBuilder.addRow(
            Row.Builder()
                .setTitle(carContext.getString(R.string.car_status_last_heard))
                .addText(formatLastHeard(node.lastHeard))
                .build(),
        )

        paneBuilder.addRow(
            Row.Builder()
                .setTitle(carContext.getString(R.string.car_status_status))
                .addText(
                    if (node.isOnline) {
                        carContext.getString(R.string.car_status_online)
                    } else {
                        carContext.getString(R.string.car_status_offline)
                    },
                )
                .build(),
        )

        paneBuilder.addAction(
            Action.Builder()
                .setTitle(carContext.getString(R.string.car_message_node))
                .setOnClickListener { onMessageClick(node.nodeNum) }
                .build(),
        )

        return PaneTemplate.Builder(paneBuilder.build())
            .setHeader(Header.Builder().setTitle(node.longName).setStartHeaderAction(Action.BACK).build())
            .build()
    }

    private fun buildErrorTemplate(): Template = PaneTemplate.Builder(
        Pane.Builder()
            .addRow(Row.Builder().setTitle(carContext.getString(R.string.car_node_not_found)).build())
            .build(),
    )
        .setHeader(
            Header.Builder()
                .setTitle(carContext.getString(R.string.car_error))
                .setStartHeaderAction(Action.BACK)
                .build(),
        )
        .build()

    private fun formatSignal(quality: SignalQuality): String = when (quality) {
        SignalQuality.EXCELLENT -> carContext.getString(R.string.car_signal_excellent)
        SignalQuality.GOOD -> carContext.getString(R.string.car_signal_good)
        SignalQuality.FAIR -> carContext.getString(R.string.car_signal_fair)
        SignalQuality.POOR -> carContext.getString(R.string.car_signal_poor)
        SignalQuality.UNKNOWN -> carContext.getString(R.string.car_signal_unknown)
    }

    private fun formatLastHeard(epochMillis: Long): String {
        if (epochMillis == 0L) return carContext.getString(R.string.car_time_never)
        return DateFormatter.formatRelativeTime(epochMillis)
    }
}
