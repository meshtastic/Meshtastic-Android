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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import androidx.glance.appwidget.PreviewSizeMode
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.components.Scaffold
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.currentState
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.state.PreferencesGlanceStateDefinition
import co.touchlab.kermit.Logger
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import org.jetbrains.compose.resources.stringResource
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.meshtastic.core.common.util.MeasurementSystem
import org.meshtastic.core.common.util.nowSeconds
import org.meshtastic.core.navigation.DEEP_LINK_BASE_URI
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.getString
import org.meshtastic.core.resources.unknown_error
import org.meshtastic.core.resources.widget_telemetry_not_found
import org.meshtastic.core.resources.widget_telemetry_title
import org.meshtastic.proto.AirQualityMetrics
import org.meshtastic.proto.EnvironmentMetrics
import org.meshtastic.proto.PowerMetrics

/**
 * One node's environment, air quality and power readings as tiles (meshtastic/design#179). It renders only what the app
 * has stored, and sends nothing over the mesh.
 */
class TelemetryWidget :
    GlanceAppWidget(),
    KoinComponent {

    override val sizeMode: SizeMode = WidgetSizes.telemetrySizeMode
    override val previewSizeMode: PreviewSizeMode = WidgetSizes.telemetryPreviewSizeMode

    private val dataSource: TelemetryWidgetDataSource by inject()

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val initialInputs = dataSource.currentInputs()
        val initialPrefs = getAppWidgetState<Preferences>(context, PreferencesGlanceStateDefinition, id)
        // Read once before composing, so the first render already carries the reading's age.
        val initialNode = initialPrefs[TELEMETRY_NODE_NUM_KEY]
        val initialHistory = initialNode?.let { dataSource.currentHistory(it) }.orEmpty()
        provideContent {
            val prefs = currentState<Preferences>()
            val nodeNum = prefs[TELEMETRY_NODE_NUM_KEY]
            val inputs by remember { dataSource.checkedInputs }.collectAsState(initialInputs)
            // Rows are tagged with the node they were read for, so another node's log never counts for this one.
            val logged by
                remember(nodeNum) {
                    nodeNum?.let { num -> dataSource.history(num).map { num to it } } ?: flowOf(null to emptyList())
                }
                    .collectAsState(initialNode to initialHistory)
            val historyLoaded = logged.first == nodeNum
            val history = if (historyLoaded) logged.second else emptyList()
            val seen = prefs.readingSet(TELEMETRY_SEEN_KEY)
            val state =
                telemetryWidgetState(
                    nodeNum = nodeNum,
                    hidden = prefs.readingSet(TELEMETRY_HIDDEN_KEY),
                    seen = seen,
                    inputs = inputs,
                    history = history,
                    lastKnown = prefs.lastKnownTelemetry(),
                )
            val node = nodeNum?.let { inputs.nodes[it] }
            // Remember what was reported and what was shown, so a reading the node stops sending keeps its tile, with
            // a dash, and the widget still shows its node with no radio connected.
            LaunchedEffect(state, node, historyLoaded) {
                if (node == null || state !is TelemetryWidgetState.Showing || !historyLoaded) return@LaunchedEffect
                val reported = everReported(history) + state.snapshot.reported
                val known = TelemetryLastKnown.of(node, state.snapshot)
                if (!seen.containsAll(reported) || prefs.lastKnownTelemetry() != known) {
                    updateAppWidgetState(context, id) { it.rememberReported(node.num, reported, known) }
                }
            }
            WidgetContent(state, prefs[TELEMETRY_PAGE_KEY] ?: 0, appWidgetId)
        }
    }

    override suspend fun providePreview(context: Context, widgetCategory: Int) {
        provideContent {
            CompositionLocalProvider(LocalWidgetPreview provides true) {
                WidgetContent(createMockTelemetryState(), 0, AppWidgetManager.INVALID_APPWIDGET_ID)
            }
        }
    }

    /** Same last-resort error UI as [LocalStatsWidget.onCompositionError]. */
    override fun onCompositionError(context: Context, glanceId: GlanceId, appWidgetId: Int, throwable: Throwable) {
        Logger.e(throwable) { "TelemetryWidget composition failed; rendering error UI" }
        val errorViews =
            RemoteViews(context.packageName, R.layout.widget_composition_error).apply {
                setTextViewText(R.id.widget_composition_error_text, getString(Res.string.unknown_error))
            }
        AppWidgetManager.getInstance(context).updateAppWidget(appWidgetId, errorViews)
    }

    @Composable
    internal fun WidgetContent(state: TelemetryWidgetState, storedPage: Int, appWidgetId: Int) {
        val context = LocalContext.current
        val size = LocalSize.current
        val oneRow = WidgetSizes.isOneRow(size)
        val onClick =
            when (state) {
                is TelemetryWidgetState.Showing -> openMetrics(context, state)
                TelemetryWidgetState.NodeNotFound -> chooseNode(appWidgetId)
            }
        // Mandatory: without this bridge every stringResource call below throws. See WidgetResourceEnvironment.
        WidgetResourceEnvironment {
            GlanceTheme {
                Scaffold(modifier = GlanceModifier.fillMaxSize().clickable(onClick)) {
                    when (state) {
                        TelemetryWidgetState.NodeNotFound ->
                            Column(modifier = GlanceModifier.fillMaxSize().padding(vertical = 6.dp)) {
                                if (!oneRow) {
                                    WidgetHeader(
                                        title = stringResource(Res.string.widget_telemetry_title),
                                        compact = WidgetSizes.isCompact(size),
                                    )
                                    Spacer(GlanceModifier.height(4.dp))
                                }
                                Box(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
                                    StatusBody(
                                        text = stringResource(Res.string.widget_telemetry_not_found),
                                        connecting = false,
                                        oneRow = oneRow,
                                    )
                                }
                            }

                        is TelemetryWidgetState.Showing ->
                            TelemetryContent(state, storedPage, GlanceModifier.fillMaxSize().padding(vertical = 6.dp))
                    }
                }
            }
        }
    }

    companion object {
        internal const val STALE_TEST_TAG = "telemetry_stale"
        internal const val TILE_TEST_TAG = "telemetry_tile"

        /** Opens the node's metrics on the screen [metricsScreen] picks. */
        private fun openMetrics(context: Context, state: TelemetryWidgetState.Showing): Action = actionStartActivity(
            Intent(
                Intent.ACTION_VIEW,
                Uri.parse("$DEEP_LINK_BASE_URI/nodes/${state.nodeNum}/${metricsScreen(state)}"),
            )
                .setClassName(context.packageName, MAIN_ACTIVITY_CLASS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        )

        private fun chooseNode(appWidgetId: Int): Action = actionStartActivity(
            TelemetryConfigActivity::class.java,
            actionParametersOf(ActionParameters.Key<Int>(AppWidgetManager.EXTRA_APPWIDGET_ID) to appWidgetId),
        )

        private const val MAIN_ACTIVITY_CLASS = "org.meshtastic.app.MainActivity"
    }
}

/** The metrics screen a tap opens: environment (which carries air quality), or power when only power readings show. */
internal fun metricsScreen(state: TelemetryWidgetState.Showing): String {
    val onlyPower =
        state.readings.isNotEmpty() &&
            state.readings.all { it is TelemetryReading.PowerVoltage || it is TelemetryReading.PowerCurrent }
    return if (onlyPower) "power" else "environment"
}

@Suppress("MagicNumber")
internal fun createMockTelemetryState(now: Long = nowSeconds) = TelemetryWidgetState.Showing(
    nodeNum = 0x43B2A1,
    shortName = "BCS1",
    colors = 0xFFFFFFFF.toInt() to 0xFF2C6E49.toInt(),
    channelLabels = listOf("Solar"),
    snapshot =
    TelemetrySnapshot(
        environment =
        EnvironmentMetrics.Builder()
            .also {
                it.temperature = 21.4f
                it.relative_humidity = 48f
                it.barometric_pressure = 1016.2f
                it.iaq = 42
            }
            .build(),
        airQuality =
        AirQualityMetrics.Builder()
            .also {
                it.pm25_standard = 6
                it.co2 = 640
            }
            .build(),
        power =
        PowerMetrics.Builder()
            .also {
                it.ch1_voltage = 13.12f
                it.ch1_current = 412.5f
            }
            .build(),
        time = (now - 720).toInt(),
    ),
    readings =
    listOf(
        TelemetryReading.Temperature,
        TelemetryReading.Humidity,
        TelemetryReading.Pressure,
        TelemetryReading.DewPoint,
        TelemetryReading.Iaq,
        TelemetryReading.Pm25,
        TelemetryReading.Co2,
        TelemetryReading.PowerVoltage(0),
        TelemetryReading.PowerCurrent(0),
    ),
    updatedAt = (now - 720).toInt(),
    isStale = false,
    units = MeasurementSystem.METRIC,
    isFahrenheit = false,
)
