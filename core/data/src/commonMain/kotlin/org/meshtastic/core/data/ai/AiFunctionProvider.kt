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
package org.meshtastic.core.data.ai

/**
 * Platform-agnostic contract defining operations that AI systems can invoke.
 *
 * This interface abstracts the app capabilities exposed to system AI assistants. On Android, the implementation is
 * wired to AppFunctions. On other platforms, equivalent mechanisms (App Intents on iOS, MCP on Desktop) can implement
 * this.
 */
interface AiFunctionProvider {

    /**
     * Send a text message over the mesh network.
     *
     * The destination is resolved by name using fuzzy matching — either a node name for direct messages or a channel
     * name for broadcast. If both are null, the message is broadcast on the primary channel.
     *
     * @param text The message text to send.
     * @param recipientName Optional node name for direct messages.
     * @param channelName Optional channel name. Defaults to primary channel if omitted.
     * @return Result indicating success or a typed failure reason.
     */
    suspend fun sendMessage(text: String, recipientName: String? = null, channelName: String? = null): SendMessageResult

    /**
     * Get the current mesh network status summary.
     *
     * @return Current connection state, node counts, and local device info.
     */
    suspend fun getMeshStatus(): MeshStatusResult

    /**
     * List all nodes currently visible on the mesh network.
     *
     * @return Success with list of nodes, or failure if not connected.
     */
    suspend fun getNodeList(): GetNodeListResult

    /**
     * List all available mesh channels and their configurations.
     *
     * @return Success with list of channels, or failure if not connected.
     */
    suspend fun getChannelInfo(): GetChannelInfoResult

    /**
     * Get status and metrics of the local mesh radio device.
     *
     * @return Success with device status, or failure if device unavailable.
     */
    suspend fun getDeviceStatus(): GetDeviceStatusResult

    /**
     * Get detailed telemetry and status for a specific mesh node.
     *
     * @param nodeId The target node ID (in Meshtastic format: "!hex" or user ID).
     * @return Success with node details, or failure if not connected or node not found.
     */
    suspend fun getNodeDetails(nodeId: String): GetNodeDetailsResult

    /**
     * Get aggregate network metrics and statistics for the entire mesh.
     *
     * @return Success with mesh metrics, or failure if not connected.
     */
    suspend fun getMeshMetrics(): GetMeshMetricsResult

    /**
     * Get recent messages from the mesh network.
     *
     * Messages are returned from the local cache — an active radio connection is not required.
     *
     * @param contactName Optional contact/channel name to filter by. Uses fuzzy matching.
     * @param limit Maximum number of messages to return (default 20, max 50).
     * @return Success with list of messages, or failure if contact not found.
     */
    suspend fun getRecentMessages(
        contactName: String? = null,
        limit: Int = DEFAULT_MESSAGE_LIMIT,
    ): GetRecentMessagesResult

    /**
     * Get a summary of unread messages grouped by contact.
     *
     * Returns the total unread count and a per-contact breakdown with the last message preview. Muted contacts are
     * excluded.
     *
     * @return Unread summary with per-contact breakdown.
     */
    suspend fun getUnreadSummary(): GetUnreadSummaryResult

    companion object {
        const val DEFAULT_MESSAGE_LIMIT = 20
        const val MAX_MESSAGE_LIMIT = 50
    }
}
