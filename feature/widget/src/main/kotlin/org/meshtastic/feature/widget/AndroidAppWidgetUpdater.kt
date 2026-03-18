/*
 * Copyright (c) 2025-2026 Meshtastic LLC
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

import android.content.Context
import androidx.glance.appwidget.updateAll
import co.touchlab.kermit.Logger
import org.koin.core.annotation.Single
import org.meshtastic.feature.widget.LocalStatsWidget
import org.meshtastic.core.repository.AppWidgetUpdater

@Single
class AndroidAppWidgetUpdater(private val context: Context) : AppWidgetUpdater {
    override suspend fun updateAll() {
        // Kickstart the widget composition.
        // The widget internally uses collectAsState() and its own sampled StateFlow
        // to drive updates automatically without excessive IPC and recreation.
        @Suppress("TooGenericExceptionCaught")
        try {
            LocalStatsWidget().updateAll(context)
        } catch (e: Exception) {
            co.touchlab.kermit.Logger.e(e) { "Failed to update widgets" }
        }
    }
}
