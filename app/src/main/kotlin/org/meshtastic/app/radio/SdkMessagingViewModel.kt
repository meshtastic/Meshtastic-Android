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
package org.meshtastic.app.radio

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import org.koin.core.annotation.KoinViewModel
import org.meshtastic.sdk.ChannelIndex
import org.meshtastic.sdk.MessageHandle
import org.meshtastic.sdk.NodeId
import org.meshtastic.sdk.SendState
import org.meshtastic.sdk.asText

/** Stable Compose model for a received text message. */
@Immutable
data class IncomingTextMessage(
    val fromNodeNum: Int,
    val channelIndex: Int,
    val text: String,
    val rxTimeSeconds: Int,
)

/** Stable Compose model for an outbound message's delivery status. */
@Immutable
data class OutboundStatus(
    val messageId: Long,
    val state: SendState,
)

/**
 * POC ViewModel that wires text messaging to the SDK's [RadioClient].
 *
 * **Inbound:** Filters [RadioClient.packets] for TEXT_MESSAGE_APP packets using the SDK's
 * [org.meshtastic.sdk.asText] extension. Accumulated in [incomingMessages] (capped at 200 for
 * the POC to avoid unbounded memory growth).
 *
 * **Outbound:** [sendText] calls [RadioClient.sendText] synchronously (non-suspending), receives
 * a [MessageHandle], and tracks [SendState] transitions in [outboundStatuses].
 *
 * **SDK Gap B surfaced:** [RadioClient] has [org.meshtastic.sdk.asText] as a packet-level
 * extension, but no reactive `RadioClient.textMessages: Flow<IncomingTextMessage>` convenience.
 * Callers must filter `packets` themselves. Log as Gap B for SDK fix.
 *
 * Note: Inbound packet collection uses `SharingStarted.Eagerly` (via [launchIn]) so messages are
 * never dropped while navigating between screens.
 */
@KoinViewModel
class SdkMessagingViewModel(
    private val provider: RadioClientProvider,
) : ViewModel() {

    private val _incomingMessages = MutableStateFlow<List<IncomingTextMessage>>(emptyList())
    val incomingMessages: StateFlow<List<IncomingTextMessage>> = _incomingMessages.asStateFlow()

    private val _outboundStatuses = MutableStateFlow<List<OutboundStatus>>(emptyList())
    val outboundStatuses: StateFlow<List<OutboundStatus>> = _outboundStatuses.asStateFlow()

    init {
        // Eagerly collect inbound text packets — must not drop while navigating.
        // Gap B: no RadioClient.textMessages flow; manually filter packets.
        provider.client
            .flatMapLatest { client -> client?.packets ?: emptyFlow() }
            .mapNotNull { packet ->
                val text = packet.asText() ?: return@mapNotNull null
                IncomingTextMessage(
                    fromNodeNum = packet.from,
                    channelIndex = packet.channel,
                    text = text,
                    rxTimeSeconds = packet.rx_time,
                )
            }
            .onEach { msg ->
                Logger.d { "[SDK] Received text from ${msg.fromNodeNum} ch${msg.channelIndex}: ${msg.text}" }
                _incomingMessages.update { prev ->
                    (prev + msg).takeLast(MAX_MESSAGES)
                }
            }
            .launchIn(viewModelScope)
    }

    /**
     * Send a text message via the SDK.
     *
     * @param text the message text
     * @param channelIndex 0–7; defaults to primary channel (0)
     * @param toNodeNum destination node num; 0xFFFFFFFF (default) = broadcast
     */
    fun sendText(
        text: String,
        channelIndex: Int = 0,
        toNodeNum: Int = BROADCAST_NODE_NUM,
    ) {
        val client = provider.client.value ?: run {
            Logger.w { "[SDK] sendText: no active client" }
            return
        }
        val handle: MessageHandle = client.sendText(
            text = text,
            channel = ChannelIndex(channelIndex),
            to = NodeId(toNodeNum),
        )

        // Track delivery state for this outbound message
        handle.state
            .onEach { state ->
                Logger.d { "[SDK] Message ${handle.id} → $state" }
                _outboundStatuses.update { prev ->
                    val updated = OutboundStatus(
                        messageId = handle.id.raw.toLong(),
                        state = state,
                    )
                    val existing = prev.indexOfFirst { it.messageId == updated.messageId }
                    if (existing >= 0) prev.toMutableList().also { it[existing] = updated }
                    else (prev + updated).takeLast(MAX_MESSAGES)
                }
            }
            .launchIn(viewModelScope)
    }

    companion object {
        private const val MAX_MESSAGES = 200
        private const val BROADCAST_NODE_NUM = -1 // 0xFFFFFFFF as signed Int
    }
}
