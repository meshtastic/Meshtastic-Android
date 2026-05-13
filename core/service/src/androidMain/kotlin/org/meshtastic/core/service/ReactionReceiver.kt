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
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.model.service.ServiceAction
import org.meshtastic.core.repository.ServiceRepository

/**
 * Handles inline emoji reaction actions from message notifications.
 *
 * Uses [goAsync] to keep the process alive while the coroutine dispatches the reaction through [ServiceRepository],
 * matching the pattern used by [ReplyReceiver] and [MarkAsReadReceiver].
 */
class ReactionReceiver :
    BroadcastReceiver(),
    KoinComponent {

    private val serviceRepository: ServiceRepository by inject()

    private val dispatchers: CoroutineDispatchers by inject()

    private val scope by lazy { CoroutineScope(SupervisorJob() + dispatchers.io) }

    @Suppress("TooGenericExceptionCaught", "ReturnCount")
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != REACT_ACTION) return

        val contactKey = intent.getStringExtra(EXTRA_CONTACT_KEY) ?: return
        val reaction = intent.getStringExtra(EXTRA_EMOJI) ?: intent.getStringExtra(EXTRA_REACTION) ?: return
        val replyId = intent.getIntExtra(EXTRA_REPLY_ID, intent.getIntExtra(EXTRA_PACKET_ID, 0))

        val pendingResult = goAsync()
        scope.launch {
            try {
                serviceRepository.onServiceAction(ServiceAction.Reaction(reaction, replyId, contactKey))
            } catch (e: Exception) {
                Logger.e(e) { "Error sending reaction" }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val REACT_ACTION = "org.meshtastic.app.REACT_ACTION"
        const val EXTRA_CONTACT_KEY = "extra_contact_key"
        const val EXTRA_REACTION = "extra_reaction"
        const val EXTRA_REPLY_ID = "extra_reply_id"
        const val EXTRA_PACKET_ID = "extra_packet_id"
        const val EXTRA_TO_ID = "extra_to_id"
        const val EXTRA_CHANNEL_INDEX = "extra_channel_index"
        const val EXTRA_EMOJI = "extra_emoji"
    }
}
