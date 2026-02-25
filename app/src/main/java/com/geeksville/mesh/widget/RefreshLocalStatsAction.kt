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
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import com.geeksville.mesh.service.MeshCommandSender
import com.geeksville.mesh.service.MeshNodeManager
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import org.meshtastic.core.model.TelemetryType

class RefreshLocalStatsAction : ActionCallback {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface RefreshLocalStatsEntryPoint {
        fun commandSender(): MeshCommandSender

        fun nodeManager(): MeshNodeManager
    }

    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val entryPoint =
            EntryPointAccessors.fromApplication(context.applicationContext, RefreshLocalStatsEntryPoint::class.java)
        val commandSender = entryPoint.commandSender()
        val nodeManager = entryPoint.nodeManager()

        val myNodeNum = nodeManager.myNodeNum ?: return

        commandSender.requestTelemetry(commandSender.generatePacketId(), myNodeNum, TelemetryType.LOCAL_STATS.ordinal)
        commandSender.requestTelemetry(commandSender.generatePacketId(), myNodeNum, TelemetryType.DEVICE.ordinal)
    }
}
