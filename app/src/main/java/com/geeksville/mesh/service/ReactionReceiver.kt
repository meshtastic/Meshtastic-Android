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
import co.touchlab.kermit.Logger
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.meshtastic.core.service.ServiceAction
import org.meshtastic.core.service.ServiceRepository
import javax.inject.Inject

@AndroidEntryPoint
class ReactionReceiver : BroadcastReceiver() {

    @Inject lateinit var serviceRepository: ServiceRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Suppress("TooGenericExceptionCaught", "ReturnCount")
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != REACT_ACTION) return

        val contactKey = intent.getStringExtra(EXTRA_CONTACT_KEY) ?: return
        val reaction = intent.getStringExtra(EXTRA_EMOJI) ?: intent.getStringExtra(EXTRA_REACTION) ?: return
        val replyId = intent.getIntExtra(EXTRA_REPLY_ID, intent.getIntExtra(EXTRA_PACKET_ID, 0))

        scope.launch {
            try {
                serviceRepository.onServiceAction(ServiceAction.Reaction(reaction, replyId, contactKey))
            } catch (e: Exception) {
                Logger.e(e) { "Error sending reaction" }
            }
        }
    }

    companion object {
        const val REACT_ACTION = "com.geeksville.mesh.REACT_ACTION"
        const val EXTRA_CONTACT_KEY = "extra_contact_key"
        const val EXTRA_REACTION = "extra_reaction"
        const val EXTRA_REPLY_ID = "extra_reply_id"
        const val EXTRA_PACKET_ID = "extra_packet_id"
        const val EXTRA_TO_ID = "extra_to_id"
        const val EXTRA_CHANNEL_INDEX = "extra_channel_index"
        const val EXTRA_EMOJI = "extra_emoji"
    }
}
