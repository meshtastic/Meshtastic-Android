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
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
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
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.semantics.semantics
import androidx.glance.semantics.testTag
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import co.touchlab.kermit.Logger
import org.jetbrains.compose.resources.stringResource
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.meshtastic.core.common.util.DateFormatter
import org.meshtastic.core.common.util.MetricFormatter
import org.meshtastic.core.common.util.nowSeconds
import org.meshtastic.core.model.util.TimeConstants
import org.meshtastic.core.navigation.DEEP_LINK_BASE_URI
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.battery
import org.meshtastic.core.resources.distance
import org.meshtastic.core.resources.getString
import org.meshtastic.core.resources.hops_away
import org.meshtastic.core.resources.node_sort_last_heard
import org.meshtastic.core.resources.powered
import org.meshtastic.core.resources.rssi
import org.meshtastic.core.resources.snr
import org.meshtastic.core.resources.traceroute_direct
import org.meshtastic.core.resources.unknown
import org.meshtastic.core.resources.unknown_error
import org.meshtastic.core.resources.widget_node_tracker_not_found

/** Tracks one node the user picked: battery, signal, last heard, hops away and distance from our radio. */
class NodeTrackerWidget :
    GlanceAppWidget(),
    KoinComponent {

    override val sizeMode: SizeMode = SizeMode.Responsive(RESPONSIVE_SIZES)
    override val previewSizeMode: androidx.glance.appwidget.PreviewSizeMode = SizeMode.Responsive(RESPONSIVE_SIZES)

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
        val onClick =
            when (state) {
                is NodeTrackerWidgetState.Tracking -> openNodeDetail(context, state.nodeNum)
                NodeTrackerWidgetState.NodeNotFound -> chooseNode(appWidgetId)
            }
        // Mandatory: without this bridge every stringResource call below throws. See WidgetResourceEnvironment.
        WidgetResourceEnvironment {
            GlanceTheme {
                Scaffold(modifier = GlanceModifier.fillMaxSize().clickable(onClick)) {
                    when (state) {
                        NodeTrackerWidgetState.NodeNotFound -> NotFound()
                        is NodeTrackerWidgetState.Tracking -> TrackerContent(state)
                    }
                }
            }
        }
    }

    companion object {
        internal const val STALE_TEST_TAG = "node_tracker_stale"

        private val SMALL_SQUARE = DpSize(110.dp, 110.dp)
        private val HORIZONTAL_RECTANGLE = DpSize(250.dp, 110.dp)
        private val RESPONSIVE_SIZES = setOf(SMALL_SQUARE, HORIZONTAL_RECTANGLE)

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

private const val MAX_BATTERY_PERCENT = 100
private val WIDE_MIN_WIDTH = 200.dp

@Composable
private fun TrackerContent(state: NodeTrackerWidgetState.Tracking) {
    if (LocalSize.current.width >= WIDE_MIN_WIDTH) Wide(state) else Small(state)
}

@Composable
private fun Small(state: NodeTrackerWidgetState.Tracking) {
    Column(modifier = GlanceModifier.fillMaxSize().padding(vertical = 8.dp).staleTag(state)) {
        Identity(state)
        Spacer(GlanceModifier.defaultWeight())
        BatteryLine(state)
        LastHeardLine(state)
    }
}

@Composable
private fun Wide(state: NodeTrackerWidgetState.Tracking) {
    Row(modifier = GlanceModifier.fillMaxSize().padding(vertical = 8.dp).staleTag(state)) {
        Column(modifier = GlanceModifier.defaultWeight().fillMaxHeight()) {
            Identity(state)
            Spacer(GlanceModifier.defaultWeight())
            LastHeardLine(state)
        }
        Spacer(GlanceModifier.width(12.dp))
        Column(modifier = GlanceModifier.defaultWeight()) {
            BatteryLine(state)
            state.snr?.let { StatLine(stringResource(Res.string.snr), MetricFormatter.snr(it), state.isStale) }
            state.rssi?.let { StatLine(stringResource(Res.string.rssi), MetricFormatter.rssi(it), state.isStale) }
            state.hopsAway?.let { hops ->
                val value = if (hops == 0) stringResource(Res.string.traceroute_direct) else hops.toString()
                StatLine(stringResource(Res.string.hops_away), value, state.isStale)
            }
            state.distance?.let { StatLine(stringResource(Res.string.distance), it, state.isStale) }
        }
    }
}

@Composable
private fun Identity(state: NodeTrackerWidgetState.Tracking) {
    Column {
        WidgetNodeChip(shortName = state.shortName, colors = state.colors, dimmed = state.isStale)
        Spacer(GlanceModifier.height(4.dp))
        Text(
            text = state.longName,
            maxLines = 1,
            style = TextStyle(color = valueColor(state.isStale), fontSize = 13.sp, fontWeight = FontWeight.Bold),
        )
    }
}

@Composable
private fun BatteryLine(state: NodeTrackerWidgetState.Tracking) {
    val level = state.batteryLevel ?: return
    val value = if (level > MAX_BATTERY_PERCENT) stringResource(Res.string.powered) else MetricFormatter.percent(level)
    StatLine(stringResource(Res.string.battery), value, state.isStale)
}

@Composable
private fun LastHeardLine(state: NodeTrackerWidgetState.Tracking) {
    // An absolute time, because a relative "5 min ago" would freeze between widget updates and go on reading fresh.
    val value =
        if (state.lastHeard > 0) {
            DateFormatter.formatShortDate(state.lastHeard.toLong() * TimeConstants.MS_PER_SEC)
        } else {
            stringResource(Res.string.unknown)
        }
    StatLine(stringResource(Res.string.node_sort_last_heard), value, state.isStale)
}

@Composable
private fun StatLine(label: String, value: String, isStale: Boolean) {
    Row(
        modifier = GlanceModifier.fillMaxWidth().padding(vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            maxLines = 1,
            style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 10.sp),
            modifier = GlanceModifier.defaultWeight(),
        )
        Text(
            text = value,
            maxLines = 1,
            style = TextStyle(color = valueColor(isStale), fontSize = 11.sp, fontWeight = FontWeight.Medium),
        )
    }
}

@Composable
private fun NotFound() {
    Column(
        modifier = GlanceModifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            provider = ImageProvider(R.drawable.widget_app_icon),
            contentDescription = null,
            modifier = GlanceModifier.size(32.dp),
        )
        Spacer(GlanceModifier.height(6.dp))
        Text(
            text = stringResource(Res.string.widget_node_tracker_not_found),
            style =
            TextStyle(
                color = GlanceTheme.colors.onSurfaceVariant,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
            ),
        )
    }
}

@Composable
@ReadOnlyComposable
private fun valueColor(isStale: Boolean): ColorProvider =
    if (isStale) GlanceTheme.colors.outline else GlanceTheme.colors.onSurface

private fun GlanceModifier.staleTag(state: NodeTrackerWidgetState.Tracking): GlanceModifier =
    if (state.isStale) semantics { testTag = NodeTrackerWidget.STALE_TEST_TAG } else this

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
