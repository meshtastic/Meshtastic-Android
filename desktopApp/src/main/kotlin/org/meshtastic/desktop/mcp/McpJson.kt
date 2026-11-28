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
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import org.meshtastic.core.model.NodeAddress

internal fun toolJson(obj: JsonElement) = CallToolResult(content = listOf(TextContent(text = obj.toString())))

internal fun toolError(message: String) = CallToolResult(content = listOf(TextContent(text = message)), isError = true)

internal fun JsonObject?.stringArg(key: String): String? =
    this?.get(key)?.takeUnless { it is JsonNull }?.jsonPrimitive?.contentOrNull

internal fun JsonObject?.intArg(key: String): Int? =
    this?.get(key)?.takeUnless { it is JsonNull }?.jsonPrimitive?.intOrNull

private val NODE_ID = Regex("^![0-9A-Fa-f]{8}$")

/** The node number of a `!aabbccdd` node id, or null for anything else, broadcast included. */
internal fun parseMcpNodeNum(raw: String?): Int? =
    raw?.trim()?.takeIf { NODE_ID.matches(it) }?.let { NodeAddress.idToNum(it) }

/** A set of tools [DesktopMcpServer] adds to every session. */
fun interface McpToolGroup {
    fun register(server: Server)
}
