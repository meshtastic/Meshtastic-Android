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

import androidx.car.app.model.ItemList
import androidx.car.app.model.Row
import org.meshtastic.feature.car.model.EmergencyAlert

/**
 * Builds a spotlight section for active emergency alerts.
 * Intended to be added at the top of the messaging screen's item list.
 */
object EmergencySpotlightBuilder {

    fun buildEmergencyRows(
        alerts: List<EmergencyAlert>,
        onAlertClick: (EmergencyAlert) -> Unit,
    ): ItemList {
        val builder = ItemList.Builder()
        alerts.filter { it.isActive }.forEach { alert ->
            builder.addItem(
                Row.Builder()
                    .setTitle("⚠️ ${alert.nodeName}")
                    .addText(alert.message)
                    .setBrowsable(true)
                    .setOnClickListener { onAlertClick(alert) }
                    .build()
            )
        }
        return builder.build()
    }
}
