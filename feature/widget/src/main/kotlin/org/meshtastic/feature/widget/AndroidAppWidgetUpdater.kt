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
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.updateAll
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.koin.core.annotation.Single
import org.meshtastic.core.repository.AppWidgetUpdater

private const val WIDGET_UPDATE_DEBOUNCE_MS = 500L

@Single
class AndroidAppWidgetUpdater(private val context: Context, stateProvider: LocalStatsWidgetStateProvider) :
    AppWidgetUpdater {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    init {
        // Observe state changes and trigger a widget re-render whenever the data changes.
        // Glance compositions are ephemeral — the widget cannot self-update via collectAsState()
        // alone, so we must call updateAll() externally to drive re-renders.
        @OptIn(FlowPreview::class)
        scope.launch {
            stateProvider.state
                .debounce(WIDGET_UPDATE_DEBOUNCE_MS)
                .distinctUntilChanged { old, new -> old.copy(updateTimeMillis = 0) == new.copy(updateTimeMillis = 0) }
                .collect { if (hasWidgetInstances()) updateAll() }
        }
    }

    private suspend fun hasWidgetInstances(): Boolean =
        GlanceAppWidgetManager(context).getGlanceIds(LocalStatsWidget::class.java).isNotEmpty()

    override suspend fun updateAll() {
        @Suppress("TooGenericExceptionCaught")
        try {
            LocalStatsWidget().updateAll(context)
        } catch (e: Exception) {
            Logger.e(e) { "Failed to update widgets" }
        }
    }
}
