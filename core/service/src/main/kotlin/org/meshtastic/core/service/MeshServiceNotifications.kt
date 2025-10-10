/*
 * Copyright (c) 2025 Meshtastic LLC
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

package org.meshtastic.core.service

import android.app.Notification
import org.meshtastic.core.database.entity.NodeEntity
import org.meshtastic.proto.MeshProtos
import org.meshtastic.proto.TelemetryProtos

const val SERVICE_NOTIFY_ID = 101

interface MeshServiceNotifications {
    fun clearNotifications()

    fun initChannels()

    fun updateServiceStateNotification(summaryString: String?, telemetry: TelemetryProtos.Telemetry?): Notification

    fun updateMessageNotification(contactKey: String, name: String, message: String, isBroadcast: Boolean)

    fun showAlertNotification(contactKey: String, name: String, alert: String)

    fun showNewNodeSeenNotification(node: NodeEntity)

    fun showOrUpdateLowBatteryNotification(node: NodeEntity, isRemote: Boolean)

    fun showClientNotification(clientNotification: MeshProtos.ClientNotification)

    fun cancelMessageNotification(contactKey: String)

    fun cancelLowBatteryNotification(node: NodeEntity)

    fun clearClientNotification(notification: MeshProtos.ClientNotification)
}
