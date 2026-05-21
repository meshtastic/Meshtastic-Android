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

/** Result of a [AiFunctionProvider.sendMessage] invocation. */
sealed class SendMessageResult {
    /** Message was successfully queued for transmission. */
    data class Success(val messageId: Int, val channel: String, val timestamp: Long) : SendMessageResult()

    /** Device is not connected to a Meshtastic radio. */
    data class NotConnected(val message: String) : SendMessageResult()

    /** The provided name matched multiple candidates. */
    data class AmbiguousName(val candidates: List<String>) : SendMessageResult()

    /** An argument was invalid (e.g., message too long, name not found). */
    data class InvalidArgument(val reason: String) : SendMessageResult()

    /** Rate limit exceeded — too many AI-triggered sends in the time window. */
    data class RateLimited(val retryAfterSeconds: Int) : SendMessageResult()
}

/** Result of a [AiFunctionProvider.getMeshStatus] invocation. */
data class MeshStatusResult(
    /** Current connection state (e.g., "CONNECTED", "DISCONNECTED"). */
    val connectionState: String,
    /** Number of nodes heard within the online threshold. */
    val onlineNodeCount: Int,
    /** Total number of nodes in the local database. */
    val totalNodeCount: Int,
    /** Local device battery level (0-100), or null if unavailable. */
    val localBatteryLevel: Int?,
    /** Display name of the local node, or null if not yet configured. */
    val localNodeName: String?,
)
