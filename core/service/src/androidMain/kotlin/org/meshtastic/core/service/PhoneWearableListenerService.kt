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

import android.content.Intent
import android.net.Uri
import co.touchlab.kermit.Logger
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.koin.android.ext.android.inject
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.model.RadioController
import org.meshtastic.core.model.WearableReply

/**
 * Listener that receives commands from the watch via the Wearable Message API.
 */
class PhoneWearableListenerService : WearableListenerService() {

    private val syncService: WearableSyncService by inject()
    private val radioController: RadioController by inject()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json

    override fun onMessageReceived(messageEvent: MessageEvent) {
        Logger.d { "onMessageReceived: ${messageEvent.path} from ${messageEvent.sourceNodeId}" }
        when (messageEvent.path) {
            "/request_sync" -> {
                syncService.forceSync(messageEvent.sourceNodeId)
            }
            "/send_message" -> {
                handleSendMessage(messageEvent.data)
            }
            "/open_on_phone" -> {
                handleOpenOnPhone(String(messageEvent.data))
            }
        }
    }

    private fun handleSendMessage(data: ByteArray) {
        scope.launch {
            try {
                val reply = json.decodeFromString<WearableReply>(String(data))
                val packet = DataPacket(
                    to = reply.address ?: DataPacket.ID_BROADCAST,
                    channel = reply.channelIndex,
                    text = reply.text
                )
                Logger.d { "Sending wearable reply to ${packet.to} on channel ${packet.channel}: ${packet.text}" }
                radioController.sendMessage(packet)
            } catch (e: Exception) {
                Logger.e(e) { "Failed to handle wearable message" }
            }
        }
    }

    private fun handleOpenOnPhone(contactKey: String) {
        try {
            val uri = Uri.parse("https://meshtastic.org/messages/$contactKey")
            val intent = Intent(Intent.ACTION_VIEW, uri)
            intent.setPackage(packageName) // Use setPackage instead of .`package`
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            startActivity(intent)
            Logger.d { "Opening $contactKey on phone in app $packageName" }
        } catch (e: Exception) {
            Logger.e(e) { "Failed to open on phone" }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // scope.cancel()
    }
}
