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
package com.geeksville.mesh.widget

import android.content.Context
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import org.meshtastic.core.data.repository.NodeRepository
import org.meshtastic.core.database.entity.MyNodeEntity
import org.meshtastic.core.database.model.Node
import org.meshtastic.core.model.util.formatUptime
import org.meshtastic.core.model.util.onlineTimeThreshold
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.air_utilization
import org.meshtastic.core.resources.battery
import org.meshtastic.core.resources.channel_utilization
import org.meshtastic.core.resources.getString
import org.meshtastic.core.resources.meshtastic_app_name
import org.meshtastic.core.resources.uptime
import java.util.Locale

class LocalStatsWidget : GlanceAppWidget() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface LocalStatsWidgetEntryPoint {
        fun nodeRepository(): NodeRepository
    }

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entryPoint =
            EntryPointAccessors.fromApplication(context.applicationContext, LocalStatsWidgetEntryPoint::class.java)
        val nodeRepository = entryPoint.nodeRepository()

        provideContent {
            val myNodeInfo by nodeRepository.myNodeInfo.collectAsState(null)
            val nodes by nodeRepository.nodeDBbyNum.collectAsState(emptyMap())

            GlanceTheme { WidgetContent(myNodeInfo, nodes) }
        }
    }

    @Suppress("LongMethod")
    @androidx.compose.runtime.Composable
    private fun WidgetContent(myNodeInfo: MyNodeEntity?, nodes: Map<Int, Node>) {
        val localNode = myNodeInfo?.let { nodes[it.myNodeNum] }
        val metrics = localNode?.deviceMetrics

        val batteryLevel = metrics?.battery_level ?: 0
        val channelUtil = metrics?.channel_utilization ?: 0f
        val airUtilTx = metrics?.air_util_tx ?: 0f
        val onlineNodes = nodes.values.count { it.lastHeard > onlineTimeThreshold() }
        val totalNodes = nodes.size
        val uptimeSecs = metrics?.uptime_seconds ?: 0L

        Column(
            modifier = GlanceModifier.fillMaxSize().background(GlanceTheme.colors.surface).padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalAlignment = Alignment.Top,
        ) {
            Text(
                text = getString(Res.string.meshtastic_app_name),
                style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Bold),
            )

            Spacer(GlanceModifier.height(8.dp))

            // Battery
            StatRow(label = getString(Res.string.battery), value = "$batteryLevel%", progress = batteryLevel / 100f)

            Spacer(GlanceModifier.height(4.dp))

            // Channel Utilization
            StatRow(
                label = getString(Res.string.channel_utilization),
                value = String.format(Locale.ROOT, "%.1f%%", channelUtil),
                progress = (channelUtil / 100f).coerceIn(0f, 1f),
            )

            Spacer(GlanceModifier.height(4.dp))

            // Air Utilization TX
            StatRow(
                label = getString(Res.string.air_utilization),
                value = String.format(Locale.ROOT, "%.1f%%", airUtilTx),
                progress = (airUtilTx / 100f).coerceIn(0f, 1f),
            )

            Spacer(GlanceModifier.height(8.dp))

            Row(modifier = GlanceModifier.fillMaxWidth()) {
                Column(modifier = GlanceModifier.defaultWeight()) {
                    Text(
                        text = "Nodes",
                        style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 10.sp),
                    )
                    Text(
                        text = "$onlineNodes / $totalNodes",
                        style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 12.sp),
                    )
                }
                Column(modifier = GlanceModifier.defaultWeight(), horizontalAlignment = Alignment.End) {
                    Text(
                        text = getString(Res.string.uptime),
                        style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 10.sp),
                    )
                    Text(
                        text = formatUptime(uptimeSecs.toInt()),
                        style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 12.sp),
                    )
                }
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun StatRow(label: String, value: String, progress: Float) {
        Column(modifier = GlanceModifier.fillMaxWidth()) {
            Row(modifier = GlanceModifier.fillMaxWidth()) {
                Text(
                    text = label,
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 10.sp),
                    modifier = GlanceModifier.defaultWeight(),
                )
                Text(text = value, style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 10.sp))
            }
            Spacer(GlanceModifier.height(2.dp))
            LinearProgressIndicator(
                progress = progress,
                modifier = GlanceModifier.fillMaxWidth().height(4.dp),
                color = GlanceTheme.colors.primary,
                backgroundColor = GlanceTheme.colors.surfaceVariant,
            )
        }
    }
}
