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
package org.meshtastic.app.ai.appfunctions

import androidx.appfunctions.AppFunctionContext
import androidx.appfunctions.AppFunctionInvalidArgumentException
import androidx.appfunctions.service.AppFunction
import kotlinx.coroutines.TimeoutCancellationException
import org.meshtastic.core.data.ai.AiFunctionProvider
import org.meshtastic.core.data.ai.SendMessageResult

/**
 * Exposes Meshtastic mesh networking capabilities to system AI assistants via the Android App Functions API. Functions
 * declared here are discoverable by the system and can be invoked by AI agents such as Gemini.
 */
class MeshtasticAppFunctions(private val provider: AiFunctionProvider) {

    /**
     * Send a text message over the Meshtastic mesh radio network.
     *
     * Messages are transmitted to nearby mesh nodes using LoRa radio. The mesh network is ideal for off-grid
     * communications where cellular service is unavailable.
     *
     * @param context The app function invocation context provided by the system.
     * @param text The message text to send (max 237 bytes).
     * @param recipientName Optional name of a specific node to send a direct message to. If omitted, the message is
     *   broadcast to all nodes on the specified channel.
     * @param channelName Optional channel name to broadcast on. If omitted, uses the primary channel. Ignored when
     *   recipientName is specified.
     * @return A [SendMessageResponse] with the message ID, channel, and timestamp.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun sendMessage(
        context: AppFunctionContext,
        text: String,
        recipientName: String? = null,
        channelName: String? = null,
    ): SendMessageResponse {
        val result =
            try {
                provider.sendMessage(text, recipientName, channelName)
            } catch (_: TimeoutCancellationException) {
                throw AppFunctionInvalidArgumentException(
                    "Request timed out. Ensure the mesh is connected and try again.",
                )
            }

        return when (result) {
            is SendMessageResult.Success ->
                SendMessageResponse(
                    messageId = result.messageId,
                    channel = result.channel,
                    timestamp = result.timestamp,
                )

            is SendMessageResult.NotConnected -> throw AppFunctionInvalidArgumentException(result.message)

            is SendMessageResult.AmbiguousName -> {
                val names = result.candidates.joinToString()
                throw AppFunctionInvalidArgumentException(
                    "Multiple nodes match that name: $names. Please be more specific.",
                )
            }

            is SendMessageResult.InvalidArgument -> throw AppFunctionInvalidArgumentException(result.reason)

            is SendMessageResult.RateLimited ->
                throw AppFunctionInvalidArgumentException(
                    "Rate limit exceeded. Try again in ${result.retryAfterSeconds} seconds.",
                )
        }
    }

    /**
     * Get the current status of the Meshtastic mesh network.
     *
     * Returns connection state, number of online nodes, total known nodes, the connected device's battery level, and
     * the local node name.
     *
     * @param context The app function invocation context provided by the system.
     * @return A [MeshStatusResponse] with the current mesh network status.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun getMeshStatus(context: AppFunctionContext): MeshStatusResponse {
        val status =
            try {
                provider.getMeshStatus()
            } catch (_: TimeoutCancellationException) {
                throw AppFunctionInvalidArgumentException(
                    "Request timed out. Ensure the mesh is connected and try again.",
                )
            }

        return MeshStatusResponse(
            connectionState = status.connectionState,
            onlineNodeCount = status.onlineNodeCount,
            totalNodeCount = status.totalNodeCount,
            localBatteryLevel = status.localBatteryLevel,
            localNodeName = status.localNodeName,
        )
    }
}
