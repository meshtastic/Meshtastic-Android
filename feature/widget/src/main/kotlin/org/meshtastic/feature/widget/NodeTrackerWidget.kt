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

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.components.Scaffold
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.currentState
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import co.touchlab.kermit.Logger
import org.jetbrains.compose.resources.stringResource
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.meshtastic.core.common.util.nowSeconds
import org.meshtastic.core.navigation.DEEP_LINK_BASE_URI
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.getString
import org.meshtastic.core.resources.unknown_error
import org.meshtastic.core.resources.widget_node_tracker_not_found
import org.meshtastic.core.resources.widget_node_tracker_title

/** Tracks one node the user picked: battery, signal, last heard, hops away and distance from our radio. */
class NodeTrackerWidget :
    GlanceAppWidget(),
    KoinComponent {

    override val sizeMode: SizeMode = WidgetSizes.responsive
    override val previewSizeMode: androidx.glance.appwidget.PreviewSizeMode = WidgetSizes.responsive

    private val dataSource: NodeTrackerWidgetDataSource by inject()

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val initialInputs = dataSource.currentInputs()
        provideContent {
            val prefs = currentState<Preferences>()
            val inputs by remember { dataSource.checkedInputs }.collectAsState(initialInputs)
            var rendered by remember { mutableStateOf<NodeTrackerWidgetState.Tracking?>(null) }
            val fromDb = inputs.nodes.isNotEmpty()
            val state =
                nodeTrackerState(prefs[TRACKED_NODE_NUM_KEY], inputs, lastKnown = rendered ?: prefs.lastKnownTracking())
            LaunchedEffect(state, fromDb) {
                val reading = (state as? NodeTrackerWidgetState.Tracking)?.takeIf { fromDb }?.asLastKnown()
                if (reading != null) {
                    rendered = reading
                    if (prefs.lastKnownTracking() != reading) {
                        updateAppWidgetState(context, id) { it.storeLastKnown(reading) }
                    }
                }
            }
            WidgetContent(state, appWidgetId)
        }
    }

    override suspend fun providePreview(context: Context, widgetCategory: Int) {
        provideContent { WidgetContent(createMockTrackerState(), AppWidgetManager.INVALID_APPWIDGET_ID) }
    }

    /** Same last-resort error UI as [LocalStatsWidget.onCompositionError]. */
    override fun onCompositionError(context: Context, glanceId: GlanceId, appWidgetId: Int, throwable: Throwable) {
        Logger.e(throwable) { "NodeTrackerWidget composition failed; rendering error UI" }
        val errorViews =
            RemoteViews(context.packageName, R.layout.widget_composition_error).apply {
                setTextViewText(R.id.widget_composition_error_text, getString(Res.string.unknown_error))
            }
        AppWidgetManager.getInstance(context).updateAppWidget(appWidgetId, errorViews)
    }

    @Composable
    internal fun WidgetContent(state: NodeTrackerWidgetState, appWidgetId: Int) {
        val context = LocalContext.current
        val size = LocalSize.current
        val oneRow = size.height < WidgetSizes.SQUARE.height
        val onClick =
            when (state) {
                is NodeTrackerWidgetState.Tracking -> openNodeDetail(context, state.nodeNum)
                NodeTrackerWidgetState.NodeNotFound -> chooseNode(appWidgetId)
            }
        // Mandatory: without this bridge every stringResource call below throws. See WidgetResourceEnvironment.
        WidgetResourceEnvironment {
            GlanceTheme {
                Scaffold(modifier = GlanceModifier.fillMaxSize().clickable(onClick)) {
                    Column(modifier = GlanceModifier.fillMaxSize().padding(bottom = 4.dp)) {
                        if (!oneRow) {
                            WidgetHeader(
                                title = stringResource(Res.string.widget_node_tracker_title),
                                compact = size.height < WidgetSizes.TALL.height,
                            )
                            Spacer(GlanceModifier.height(4.dp))
                        }
                        when (state) {
                            NodeTrackerWidgetState.NodeNotFound ->
                                StatusBody(
                                    text = stringResource(Res.string.widget_node_tracker_not_found),
                                    connecting = false,
                                    oneRow = oneRow,
                                )

                            is NodeTrackerWidgetState.Tracking -> TrackerContent(state)
                        }
                    }
                }
            }
        }
    }

    companion object {
        internal const val STALE_TEST_TAG = "node_tracker_stale"

        private fun openNodeDetail(context: Context, nodeNum: Int): Action = actionStartActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse("$DEEP_LINK_BASE_URI/nodes/$nodeNum"))
                .setClassName(context.packageName, MAIN_ACTIVITY_CLASS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        )

        private fun chooseNode(appWidgetId: Int): Action = actionStartActivity(
            NodeTrackerConfigActivity::class.java,
            actionParametersOf(ActionParameters.Key<Int>(AppWidgetManager.EXTRA_APPWIDGET_ID) to appWidgetId),
        )

        private const val MAIN_ACTIVITY_CLASS = "org.meshtastic.app.MainActivity"
    }
}

@Suppress("MagicNumber")
internal fun createMockTrackerState() = NodeTrackerWidgetState.Tracking(
    nodeNum = 0x43B2A1,
    shortName = "BCS1",
    longName = "Basecamp Solar",
    colors = 0xFFFFFFFF.toInt() to 0xFF2C6E49.toInt(),
    batteryLevel = 76,
    snr = 9.5f,
    rssi = -85,
    hopsAway = 0,
    lastHeard = (nowSeconds - 480).toInt(),
    distance = "1.2 km",
    isStale = false,
)
