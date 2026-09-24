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

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.appfunctions.AppFunction
import androidx.appfunctions.AppFunctionIntValueConstraint
import androidx.appfunctions.AppFunctionService
import androidx.appfunctions.AppFunctionServiceEntryPoint
import org.koin.android.ext.android.inject
import org.meshtastic.core.data.ai.AiFunctionProvider

/**
 * Exposes Meshtastic mesh networking capabilities to system AI assistants via the Android App Functions API. Functions
 * declared here are discoverable by the system and can be invoked by AI agents such as Gemini.
 *
 * KSP generates the concrete [MeshtasticAppFunctionService] and its `meshtastic_app_function_service.xml` asset; the
 * KDoc on each function is the description agents read.
 */
@RequiresApi(Build.VERSION_CODES.BAKLAVA)
@AppFunctionServiceEntryPoint(
    serviceName = "MeshtasticAppFunctionService",
    appFunctionXmlFileName = "meshtastic_app_function_service",
)
abstract class BaseMeshtasticAppFunctionService : AppFunctionService() {

    private val appFunctions: MeshtasticAppFunctions by inject()

    /**
     * Send a text message over the Meshtastic mesh radio network.
     *
     * Messages are transmitted to nearby mesh nodes using LoRa radio. The mesh network is ideal for off-grid
     * communications where cellular service is unavailable.
     *
     * @param text The message text to send (max 228 UTF-8 bytes — the mesh payload left after protobuf framing).
     * @param recipientName Optional name of a specific node to send a direct message to. If omitted, the message is
     *   broadcast to all nodes on the specified channel.
     * @param channelName Optional channel name to broadcast on. If omitted, uses the primary channel. Ignored when
     *   recipientName is specified.
     * @return A [SendMessageResponse] with the message ID, channel, and timestamp.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun sendMessage(
        text: String,
        recipientName: String? = null,
        channelName: String? = null,
    ): SendMessageResponse = appFunctions.sendMessage(text, recipientName, channelName)

    /**
     * Get the current status of the Meshtastic mesh network.
     *
     * Returns connection state, number of online nodes, total known nodes, the connected device's battery level, and
     * the local node name.
     *
     * @return A [MeshStatusResponse] with the current mesh network status.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun getMeshStatus(): MeshStatusResponse = appFunctions.getMeshStatus()

    /**
     * List all nodes currently visible on the Meshtastic mesh network.
     *
     * Returns detailed information about each node including name, battery level, and last heard time. Nodes are sorted
     * by most recently heard first.
     *
     * @return A list of nodes with their current status and metrics.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun getNodeList(): GetNodeListResponse = appFunctions.getNodeList()

    /**
     * List all available Meshtastic mesh channels and their configurations.
     *
     * Returns details about each channel including name, index, primary status, and uplink/downlink settings.
     *
     * @return A list of channels with their current configuration.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun getChannelInfo(): GetChannelInfoResponse = appFunctions.getChannelInfo()

    /**
     * Get the status and metrics of the local Meshtastic radio device.
     *
     * Returns hardware model, firmware version, battery level, charging status, and current radio state.
     *
     * @return Device status with current metrics and configuration.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun getDeviceStatus(): GetDeviceStatusResponse = appFunctions.getDeviceStatus()

    /**
     * Retrieve detailed telemetry and status for a specific mesh node.
     *
     * Returns per-node metrics including battery level, signal strength, hardware model, and location data.
     *
     * @param nodeId The target node ID (e.g., '!abc12345' or user ID).
     * @return A [GetNodeDetailsResponse] with detailed node information.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun getNodeDetails(nodeId: String): GetNodeDetailsResponse = appFunctions.getNodeDetails(nodeId)

    /**
     * Retrieve aggregate network metrics and statistics for the entire mesh.
     *
     * Returns mesh-wide analytics including total node count, online nodes, average battery level, and health score.
     *
     * @return A [GetMeshMetricsResponse] with mesh-wide statistics.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun getMeshMetrics(): GetMeshMetricsResponse = appFunctions.getMeshMetrics()

    /**
     * Retrieve recent messages received over the Meshtastic mesh radio network.
     *
     * Returns a list of recent messages from the local message history. Messages are stored locally and do not require
     * an active mesh connection. Useful for catching up on conversations or reviewing recent communications.
     *
     * @param contactName Optional name of a node or channel to filter messages from. If omitted, returns messages from
     *   all contacts sorted by most recent.
     * @param limit Maximum number of messages to return (1–50). Defaults to 20.
     * @return A [GetRecentMessagesResponse] containing the list of recent messages.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun getRecentMessages(
        contactName: String? = null,
        @AppFunctionIntValueConstraint(enumValues = [1, 5, 10, 20, 50])
        limit: Int = AiFunctionProvider.DEFAULT_MESSAGE_LIMIT,
    ): GetRecentMessagesResponse = appFunctions.getRecentMessages(contactName, limit)

    /**
     * Get a summary of unread messages across all Meshtastic mesh contacts.
     *
     * Returns the total unread count and a per-contact breakdown showing who sent unread messages, how many are unread,
     * and a preview of the last message. Muted contacts are excluded. Does not require an active mesh connection.
     *
     * @return A [GetUnreadSummaryResponse] with the total unread count and per-contact details.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun getUnreadSummary(): GetUnreadSummaryResponse = appFunctions.getUnreadSummary()
}
