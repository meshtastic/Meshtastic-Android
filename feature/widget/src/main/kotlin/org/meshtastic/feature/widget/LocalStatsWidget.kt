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
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.components.CircleIconButton
import androidx.glance.appwidget.components.Scaffold
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import co.touchlab.kermit.Logger
import org.jetbrains.compose.resources.stringResource
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.connecting
import org.meshtastic.core.resources.device_sleeping
import org.meshtastic.core.resources.disconnected
import org.meshtastic.core.resources.getString
import org.meshtastic.core.resources.meshtastic_app_name
import org.meshtastic.core.resources.refresh
import org.meshtastic.core.resources.unknown_error

class LocalStatsWidget :
    GlanceAppWidget(),
    KoinComponent {

    override val sizeMode: SizeMode = WidgetSizes.responsive
    override val previewSizeMode: androidx.glance.appwidget.PreviewSizeMode = WidgetSizes.responsive

    private val stateProvider: LocalStatsWidgetStateProvider by inject()

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val state by stateProvider.state.collectAsState()
            WidgetContent(state)
        }
    }

    override suspend fun providePreview(context: Context, widgetCategory: Int) {
        val currentState = stateProvider.state.value

        val stateToRender =
            if (currentState.showContent && currentState.nodeShortName != null) {
                currentState
            } else {
                createMockWidgetState()
            }
        provideContent { WidgetContent(stateToRender) }
    }

    /**
     * Renders our own last-resort UI when the Glance composition throws, replacing Glance's default "Can't show
     * content" layout, and logs the failure so a broken widget stops being silent.
     *
     * Overriding this is the only option available: Glance's `errorUiLayout` constructor parameter is `internal` in
     * 1.2.0-rc01, so the approach shown in the official error-handling guide does not compile from outside the library.
     * Note that overriding this method means `errorUiLayout` is never read at all.
     *
     * The message comes from the Crowdin-managed Compose-resources catalog via the project's blocking [getString], so
     * it stays translated even though a `RemoteViews` layout cannot reference Compose resources itself.
     */
    override fun onCompositionError(context: Context, glanceId: GlanceId, appWidgetId: Int, throwable: Throwable) {
        Logger.e(throwable) { "LocalStatsWidget composition failed; rendering error UI" }
        val errorViews =
            RemoteViews(context.packageName, R.layout.widget_composition_error).apply {
                setTextViewText(R.id.widget_composition_error_text, getString(Res.string.unknown_error))
            }
        AppWidgetManager.getInstance(context).updateAppWidget(appWidgetId, errorViews)
    }

    @Composable
    internal fun WidgetContent(state: LocalStatsWidgetUiState) {
        val context = LocalContext.current
        val size = LocalSize.current
        val oneRow = size.height < WidgetSizes.SQUARE.height
        val compact = size.height < WidgetSizes.TALL.height
        val wide = size.width >= WidgetSizes.WIDE.width
        // Mandatory: without this bridge every stringResource call below throws. See WidgetResourceEnvironment.
        WidgetResourceEnvironment {
            GlanceTheme {
                Scaffold(
                    modifier =
                    GlanceModifier.fillMaxSize()
                        .clickable(
                            actionStartActivity(
                                context.packageManager.getLaunchIntentForPackage(context.packageName)
                                    ?: Intent().setClassName(context.packageName, "org.meshtastic.app.MainActivity"),
                            ),
                        ),
                ) {
                    Column(modifier = GlanceModifier.fillMaxSize().padding(bottom = 4.dp)) {
                        if (!oneRow) {
                            // Refresh needs the width of two-plus cells beside the title; the whole widget opens the
                            // app.
                            WidgetHeader(
                                title = stringResource(Res.string.meshtastic_app_name),
                                compact = compact,
                                action = if (wide && !compact) ({ RefreshButton() }) else null,
                            )
                            Spacer(GlanceModifier.height(4.dp))
                        }
                        when {
                            !state.showContent -> StatusBody(statusText(state), state.isConnecting, oneRow)
                            oneRow -> StatsRow(state, wide)
                            compact -> StatsSquare(state, wide)
                            else -> StatsTall(state, wide, extra = size.height >= WidgetSizes.EXTRA_TALL.height)
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun RefreshButton() {
        CircleIconButton(
            imageProvider = ImageProvider(R.drawable.widget_ic_refresh),
            contentDescription = stringResource(Res.string.refresh),
            onClick = actionRunCallback<RefreshLocalStatsAction>(),
            backgroundColor = null,
        )
    }

    @Composable
    private fun statusText(state: LocalStatsWidgetUiState): String = when (state.connectionState) {
        is ConnectionState.Disconnected -> stringResource(Res.string.disconnected)
        is ConnectionState.Connecting -> stringResource(Res.string.connecting)
        is ConnectionState.DeviceSleep -> stringResource(Res.string.device_sleeping)
        is ConnectionState.Connected -> ""
    }
}

internal fun createMockWidgetState() = LocalStatsWidgetUiState(
    connectionState = ConnectionState.Connected,
    showContent = true,
    nodeShortName = "ME",
    nodeColors = 0xFFFFFFFF.toInt() to 0xFF000000.toInt(),
    batteryLevel = 85,
    hasBattery = true,
    channelUtilization = 18.5f,
    channelUtilizationProgress = 0.185f,
    airUtilization = 3.2f,
    airUtilizationProgress = 0.032f,
    hasStats = true,
    numPacketsTx = 145,
    numPacketsRx = 892,
    totalNodes = 3,
    onlineNodes = 2,
    uptimeSecs = 172800L,
    updateTimeMillis = System.currentTimeMillis() - 300000L,
)
