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
package org.meshtastic.core.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.meshtastic.core.common.util.nowMillis
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.repository.MeshServiceNotifications
import org.meshtastic.core.repository.PacketRepository

/** A [BroadcastReceiver] that handles "Mark as read" actions from notifications. */
class MarkAsReadReceiver :
    BroadcastReceiver(),
    KoinComponent {

    private val packetRepository: PacketRepository by inject()

    private val serviceNotifications: MeshServiceNotifications by inject()

    private val dispatchers: CoroutineDispatchers by inject()

    private val scope by lazy { CoroutineScope(dispatchers.io + SupervisorJob()) }

    companion object {
        const val MARK_AS_READ_ACTION = "com.geeksville.mesh.MARK_AS_READ"
        const val CONTACT_KEY = "contact_key"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == MARK_AS_READ_ACTION) {
            val contactKey = intent.getStringExtra(CONTACT_KEY) ?: return
            val pendingResult = goAsync()

            scope.launch {
                try {
                    packetRepository.clearUnreadCount(contactKey, nowMillis)
                    serviceNotifications.cancelMessageNotification(contactKey)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
