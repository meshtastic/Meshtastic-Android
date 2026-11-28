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
package org.meshtastic.desktop.mcp

import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.ToolAnnotations
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.meshtastic.core.model.Message
import org.meshtastic.core.model.NodeAddress
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.core.repository.PacketRepository
import org.meshtastic.proto.Routing

/** `messages`: reads the app's message history from Room through [PacketRepository], newest first. */
internal class McpMessagesTool(
    private val packetRepository: PacketRepository,
    private val nodeRepository: NodeRepository,
) : McpToolGroup {

    override fun register(server: Server) {
        server.addTool(
            name = "messages",
            description =
            "Messages in the app's history, newest first, with their delivery status. contact is a node id " +
                "(!1a2b3c4d, every channel's conversation with it), ^all (every channel's broadcast) or a full " +
                "contact key like 0^all; without it, every conversation.",
            inputSchema =
            ToolSchema(
                properties =
                buildJsonObject {
                    putJsonObject("contact") { put("type", "string") }
                    putJsonObject("limit") { put("type", "integer") }
                },
            ),
            toolAnnotations = ToolAnnotations(readOnlyHint = true),
        ) { request ->
            messages(request.arguments)
        }
    }

    private suspend fun messages(args: JsonObject?): CallToolResult {
        val contact = args.stringArg("contact")?.trim()?.takeIf { it.isNotEmpty() }
        val limit = (args.intArg("limit") ?: DEFAULT_LIMIT).coerceIn(1, MAX_LIMIT)
        val keys = packetRepository.getContacts().first().keys
        val contactKeys = if (contact == null) keys.sorted() else matchMcpContactKeys(keys, contact)
        val myId = nodeRepository.ourNodeInfo.value?.user?.id
        val rows =
            contactKeys
                .flatMap { key ->
                    packetRepository
                        .getMessagesFrom(key, limit = limit) { nodeRepository.getNode(it ?: NodeAddress.ID_BROADCAST) }
                        .first()
                        .map { key to it }
                }
                .sortedByDescending { (_, message) -> message.receivedTime }
                .take(limit)
        return toolJson(
            buildJsonObject {
                putJsonArray("contact_keys") { contactKeys.forEach { add(it) } }
                putJsonArray("messages") { rows.forEach { (key, message) -> add(messageJson(key, message, myId)) } }
            },
        )
    }

    private fun messageJson(contactKey: String, message: Message, myId: String?) = buildJsonObject {
        put("uuid", message.uuid)
        put("packet_id", message.packetId.toUInt().toLong())
        put("contact_key", contactKey)
        put("from", if (message.fromLocal) myId ?: NodeAddress.ID_LOCAL else message.node.user.id)
        put("to", mcpMessageTo(contactKey, message.fromLocal, myId))
        put("status", message.status?.name)
        put("routing_error", message.routingError.takeIf { it > 0 }?.let { Routing.Error.fromValue(it)?.name ?: "$it" })
        put("time", message.receivedTime)
        put("mesh_time", message.meshTime.takeIf { it > 0 })
        put("text", message.text)
    }

    private companion object {
        const val DEFAULT_LIMIT = 20
        const val MAX_LIMIT = 500
    }
}

/**
 * The stored contact keys [contact] names: itself when it is a full key, else the `<channel><id>` key on every channel,
 * so a node id finds both its channel and its PKC conversation.
 */
internal fun matchMcpContactKeys(keys: Collection<String>, contact: String): List<String> = keys
    .filter { key ->
        key == contact || (key.length == contact.length + 1 && key.first().isDigit() && key.endsWith(contact))
    }
    .sorted()

/** Who a message in [contactKey]'s conversation went to: the broadcast, the contact we sent to, or us. */
internal fun mcpMessageTo(contactKey: String, fromLocal: Boolean, myId: String?): String? = when {
    contactKey.endsWith(NodeAddress.ID_BROADCAST) -> NodeAddress.ID_BROADCAST
    fromLocal -> contactKey.drop(1)
    else -> myId
}
