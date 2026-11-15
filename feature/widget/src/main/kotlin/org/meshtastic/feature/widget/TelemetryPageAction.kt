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

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.state.updateAppWidgetState

/** The Telemetry widget's page button: shows the next four readings, wrapping to the first, without opening the app. */
class TelemetryPageAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val pageCount = parameters[PAGE_COUNT_KEY] ?: return
        updateAppWidgetState(context, glanceId) { it[TELEMETRY_PAGE_KEY] = nextPage(it[TELEMETRY_PAGE_KEY], pageCount) }
        TelemetryWidget().update(context, glanceId)
    }

    companion object {
        internal val PAGE_COUNT_KEY = ActionParameters.Key<Int>("telemetry_page_count")

        /** The page after [stored], counted against the pages there are now, so a stale stored page still wraps. */
        internal fun nextPage(stored: Int?, pageCount: Int): Int =
            if (pageCount <= 1) 0 else (Math.floorMod(stored ?: 0, pageCount) + 1) % pageCount
    }
}
