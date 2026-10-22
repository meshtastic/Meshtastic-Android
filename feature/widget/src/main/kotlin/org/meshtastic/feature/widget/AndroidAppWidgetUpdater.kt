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
import androidx.datastore.preferences.core.Preferences
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.updateAll
import androidx.glance.state.PreferencesGlanceStateDefinition
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch
import org.koin.core.annotation.Single
import org.meshtastic.core.repository.AppWidgetUpdater

private const val WIDGET_UPDATE_DEBOUNCE_MS = 500L
private const val TRACKER_UPDATE_SAMPLE_MS = 1_000L

@Single
class AndroidAppWidgetUpdater(
    private val context: Context,
    stateProvider: LocalStatsWidgetStateProvider,
    trackerDataSource: NodeTrackerWidgetDataSource,
) : AppWidgetUpdater {
    @Suppress("InjectDispatcher") // feature:widget does not depend on core:di, where CoroutineDispatchers lives
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
                .collect { if (hasInstances(LocalStatsWidget::class.java)) update(LocalStatsWidget()) }
        }

        // Sampled rather than debounced: on a busy mesh the node DB changes faster than any debounce window, which
        // would starve the trackers. Only the rendered state of the tracked nodes counts as a change.
        @OptIn(FlowPreview::class)
        scope.launch {
            trackerDataSource.checkedInputs
                .sample(TRACKER_UPDATE_SAMPLE_MS)
                .map { inputs -> trackedNodeNums().map { nodeTrackerState(it, inputs) } }
                .distinctUntilChanged()
                .collect { states -> if (states.isNotEmpty()) update(NodeTrackerWidget()) }
        }
    }

    private suspend fun hasInstances(widget: Class<out GlanceAppWidget>): Boolean =
        GlanceAppWidgetManager(context).getGlanceIds(widget).isNotEmpty()

    private suspend fun trackedNodeNums(): List<Int?> =
        GlanceAppWidgetManager(context).getGlanceIds(NodeTrackerWidget::class.java).map { id ->
            getAppWidgetState<Preferences>(context, PreferencesGlanceStateDefinition, id)[TRACKED_NODE_NUM_KEY]
        }

    override suspend fun updateAll() {
        update(LocalStatsWidget())
        update(NodeTrackerWidget())
    }

    private suspend fun update(widget: GlanceAppWidget) {
        @Suppress("TooGenericExceptionCaught")
        try {
            widget.updateAll(context)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.e(e) { "Failed to update ${widget::class.simpleName}" }
        }
    }
}
