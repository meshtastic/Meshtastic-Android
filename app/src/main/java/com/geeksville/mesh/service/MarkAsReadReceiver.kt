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
package com.geeksville.mesh.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.meshtastic.core.data.repository.PacketRepository
import org.meshtastic.core.model.util.nowMillis
import org.meshtastic.core.service.MeshServiceNotifications
import javax.inject.Inject

/** A [BroadcastReceiver] that handles "Mark as read" actions from notifications. */
@AndroidEntryPoint
class MarkAsReadReceiver : BroadcastReceiver() {
    @Inject lateinit var packetRepository: PacketRepository

    @Inject lateinit var meshServiceNotifications: MeshServiceNotifications

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    companion object {
        const val MARK_AS_READ_ACTION = "com.geeksville.mesh.MARK_AS_READ_ACTION"
        const val CONTACT_KEY = "contactKey"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == MARK_AS_READ_ACTION) {
            val contactKey = intent.getStringExtra(CONTACT_KEY) ?: return
            val pendingResult = goAsync()
            scope.launch {
                try {
                    packetRepository.clearUnreadCount(contactKey, nowMillis)
                    meshServiceNotifications.cancelMessageNotification(contactKey)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
