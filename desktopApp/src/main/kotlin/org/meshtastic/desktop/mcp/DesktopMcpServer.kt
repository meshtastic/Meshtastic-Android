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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import co.touchlab.kermit.Logger
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.mcpStreamableHttp
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolAnnotations
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.koin.compose.koinInject
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.model.InterfaceId
import org.meshtastic.core.model.MessageStatus
import org.meshtastic.core.model.NodeAddress
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.core.repository.PacketRepository
import org.meshtastic.core.repository.RadioController
import org.meshtastic.core.repository.RadioInterfaceService
import org.meshtastic.core.repository.ServiceRepository
import org.meshtastic.core.repository.usecase.SendMessageOutcome
import org.meshtastic.core.repository.usecase.SendMessageUseCase
import org.meshtastic.desktop.DesktopBuildConfig
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/** Runs [DesktopMcpServer] for the life of the composition in a debug build with a configured port. */
@Composable
fun DesktopMcpLifecycle() {
    val port = remember { DesktopMcpServer.configuredPort()?.takeIf { DesktopBuildConfig.IS_DEBUG } } ?: return
    val serviceRepository = koinInject<ServiceRepository>()
    val radioController = koinInject<RadioController>()
    val radioInterfaceService = koinInject<RadioInterfaceService>()
    val nodeRepository = koinInject<NodeRepository>()
    val packetRepository = koinInject<PacketRepository>()
    val sendMessageUseCase = koinInject<SendMessageUseCase>()
    val server = remember {
        DesktopMcpServer(
            serviceRepository,
            radioController,
            radioInterfaceService,
            nodeRepository,
            packetRepository,
            sendMessageUseCase,
        )
    }
    DisposableEffect(port) {
        runCatching { server.start(port) }.onFailure { Logger.e(it) { "MCP server failed to start on $port" } }
        onDispose { server.stop() }
    }
}

/**
 * Opt-in MCP server for agents, wired to the app's radio layer rather than its UI. Starts only when [PORT_ENV] or
 * [PORT_PROPERTY] names a port, and binds loopback only.
 */
class DesktopMcpServer(
    private val serviceRepository: ServiceRepository,
    private val radioController: RadioController,
    private val radioInterfaceService: RadioInterfaceService,
    private val nodeRepository: NodeRepository,
    private val packetRepository: PacketRepository,
    private val sendMessageUseCase: SendMessageUseCase,
) {
    private var engine: EmbeddedServer<*, *>? = null

    fun start(port: Int) {
        engine =
            embeddedServer(CIO, host = LOOPBACK, port = port) { mcpStreamableHttp(path = "/mcp") { newSession() } }
                .start(wait = false)
        Logger.i { "MCP server listening on http://$LOOPBACK:$port/mcp" }
    }

    fun stop() {
        engine?.stop(gracePeriodMillis = 0, timeoutMillis = STOP_TIMEOUT_MS)
        engine = null
    }

    private fun newSession(): Server = Server(
        serverInfo = Implementation(name = "meshtastic-desktop", version = DesktopBuildConfig.VERSION_NAME),
        options = ServerOptions(capabilities = ServerCapabilities(tools = ServerCapabilities.Tools())),
    ) {
        addConnectionStateTool()
        addConnectTool()
        addNodesTool()
        addSendTextTool()
    }

    private fun Server.addConnectionStateTool() {
        addTool(
            name = "connection_state",
            description = "The app's radio connection state, selected device address and local node.",
            toolAnnotations = ToolAnnotations(readOnlyHint = true),
        ) {
            json(connectionStateJson())
        }
    }

    private fun Server.addConnectTool() {
        addTool(
            name = "connect",
            description =
            "Select and connect to a radio. address is a BLE MAC (28:84:85:78:4E:ED), or a prefixed " +
                "app address (x<mac>, t<host>, s<port>). Waits up to wait_seconds for Connected.",
            inputSchema =
            ToolSchema(
                properties =
                buildJsonObject {
                    putJsonObject("address") { put("type", "string") }
                    putJsonObject("wait_seconds") { put("type", "integer") }
                },
                required = listOf("address"),
            ),
        ) { request ->
            val raw = request.arguments.string("address") ?: return@addTool error("address is required")
            val address = normalizeMcpAddress(raw)
            val wait = (request.arguments.int("wait_seconds") ?: DEFAULT_CONNECT_WAIT_S).seconds
            val started = TimeSource.Monotonic.markNow()
            radioController.setDeviceAddress(address)
            val reached =
                withTimeoutOrNull(wait) {
                    serviceRepository.connectionState.first { it == ConnectionState.Connected }
                }
            json(
                buildJsonObject {
                    put("requested", address)
                    put("connected", reached != null)
                    put("elapsed_ms", started.elapsedNow().inWholeMilliseconds)
                    put("state", connectionStateJson())
                },
            )
        }
    }

    private fun Server.addNodesTool() {
        addTool(
            name = "nodes",
            description = "Nodes in the app's node DB, most recently heard first. No positions.",
            inputSchema =
            ToolSchema(properties = buildJsonObject { putJsonObject("limit") { put("type", "integer") } }),
            toolAnnotations = ToolAnnotations(readOnlyHint = true),
        ) { request ->
            val limit = request.arguments.int("limit") ?: DEFAULT_NODE_LIMIT
            val myNum = nodeRepository.myNodeInfo.value?.myNodeNum
            val all = nodeRepository.nodeDBbyNum.value.values.sortedByDescending { it.lastHeard }
            json(
                buildJsonObject {
                    put("total", all.size)
                    put(
                        "nodes",
                        buildJsonArray {
                            all.take(limit).forEach { node ->
                                add(
                                    buildJsonObject {
                                        put("num", node.num.toUInt().toLong())
                                        put("id", node.user.id)
                                        put("long", node.user.long_name)
                                        put("short", node.user.short_name)
                                        put("hw", node.user.hw_model.name)
                                        put("last_heard", node.lastHeard)
                                        put("snr", node.snr.takeIf { it != Float.MAX_VALUE })
                                        put("rssi", node.rssi.takeIf { it != Int.MAX_VALUE })
                                        put("hops", node.hopsAway.takeIf { it >= 0 })
                                        put("mqtt", node.viaMqtt)
                                        put("local", node.num == myNum)
                                    },
                                )
                            }
                        },
                    )
                },
            )
        }
    }

    private fun Server.addSendTextTool() {
        addTool(
            name = "send_text",
            description =
            "Send a text message through the app's send path (history + queue). Without to, broadcasts " +
                "on channel (default 0). to is a node id like !1a2b3c4d. Waits up to wait_seconds for an " +
                "ACK or NAK and reports the message status.",
            inputSchema =
            ToolSchema(
                properties =
                buildJsonObject {
                    putJsonObject("text") { put("type", "string") }
                    putJsonObject("to") { put("type", "string") }
                    putJsonObject("channel") { put("type", "integer") }
                    putJsonObject("wait_seconds") { put("type", "integer") }
                },
                required = listOf("text"),
            ),
        ) { request ->
            val text = request.arguments.string("text") ?: return@addTool error("text is required")
            if (serviceRepository.connectionState.value != ConnectionState.Connected) {
                return@addTool error("not connected")
            }
            val channel = request.arguments.int("channel") ?: 0
            // A contact key carries the channel as one digit, so 8 or more would be read as a different channel.
            val maxChannel = NodeAddress.PKC_CHANNEL_INDEX - 1
            if (channel !in 0..maxChannel) return@addTool error("channel must be 0..$maxChannel")
            val contactKey = contactKeyFor(request.arguments.string("to"), channel)
            val device = radioInterfaceService.getDeviceAddress()
            when (val outcome = sendMessageUseCase(text = text, contactKey = contactKey)) {
                SendMessageOutcome.Refused -> error("refused for $contactKey")

                is SendMessageOutcome.Queued -> {
                    val wait = (request.arguments.int("wait_seconds") ?: DEFAULT_SEND_WAIT_S).seconds
                    json(awaitStatus(outcome.packetId, contactKey, device, wait))
                }
            }
        }
    }

    private suspend fun awaitStatus(
        packetId: Int,
        contactKey: String,
        device: String?,
        wait: kotlin.time.Duration,
    ): JsonObject {
        val started = TimeSource.Monotonic.markNow()
        val seen = mutableListOf<String>()
        var status: MessageStatus? = null
        var lastName: String? = null
        // Packet ids are per radio: once another radio is selected, a lookup by id reads that radio's history.
        while (started.elapsedNow() < wait && radioInterfaceService.getDeviceAddress() == device) {
            status = packetRepository.getPacketByPacketId(packetId)?.status
            val name = status?.name ?: "MISSING"
            if (lastName != name) seen += "$name@${started.elapsedNow().inWholeMilliseconds}ms"
            lastName = name
            if (status in TERMINAL) break
            delay(POLL_INTERVAL)
        }
        return buildJsonObject {
            put("packet_id", packetId.toUInt().toLong())
            put("contact_key", contactKey)
            put("status", status?.name)
            put("device_changed", radioInterfaceService.getDeviceAddress() != device)
            put("transitions", buildJsonArray { seen.forEach { add(JsonPrimitive(it)) } })
        }
    }

    private fun contactKeyFor(to: String?, channel: Int): String {
        if (to.isNullOrBlank() || to == NodeAddress.ID_BROADCAST) return "$channel${NodeAddress.ID_BROADCAST}"
        val node = nodeRepository.getNode(to)
        val ourPkc = nodeRepository.ourNodeInfo.value?.hasPKC == true
        val ch = if (ourPkc && node.hasPKC) NodeAddress.PKC_CHANNEL_INDEX else node.channel
        return "$ch$to"
    }

    private fun connectionStateJson(): JsonObject {
        val my = nodeRepository.myNodeInfo.value
        val ours = nodeRepository.ourNodeInfo.value
        return buildJsonObject {
            put("state", serviceRepository.connectionState.value.toString())
            put("address", radioInterfaceService.getDeviceAddress())
            put("progress", serviceRepository.connectionProgress.value)
            put("my_node_num", my?.myNodeNum?.toUInt()?.toLong())
            put("my_id", ours?.user?.id)
            put("my_long", ours?.user?.long_name)
            put("firmware", my?.firmwareVersion)
            put("model", my?.model)
            put("node_count", nodeRepository.nodeDBbyNum.value.size)
        }
    }

    companion object {
        const val PORT_ENV = "MESHTASTIC_MCP_PORT"
        const val PORT_PROPERTY = "meshtastic.mcp.port"

        private const val LOOPBACK = "127.0.0.1"
        private const val STOP_TIMEOUT_MS = 2_000L
        private const val DEFAULT_CONNECT_WAIT_S = 30
        private const val DEFAULT_SEND_WAIT_S = 30
        private const val DEFAULT_NODE_LIMIT = 50
        private val POLL_INTERVAL = 250.milliseconds
        private val TERMINAL =
            setOf(MessageStatus.DELIVERED, MessageStatus.ERROR, MessageStatus.SFPP_CONFIRMED, MessageStatus.RECEIVED)

        /** The configured port, or null when the server stays off. */
        fun configuredPort(): Int? = parseMcpPort(System.getProperty(PORT_PROPERTY) ?: System.getenv(PORT_ENV))
    }
}

private const val MAX_PORT = 65_535

private fun json(obj: JsonElement) = CallToolResult(content = listOf(TextContent(text = obj.toString())))

private fun error(message: String) = CallToolResult(content = listOf(TextContent(text = message)), isError = true)

private fun JsonObject?.string(key: String): String? =
    this?.get(key)?.takeUnless { it is JsonNull }?.jsonPrimitive?.contentOrNull

private fun JsonObject?.int(key: String): Int? = this?.get(key)?.takeUnless { it is JsonNull }?.jsonPrimitive?.intOrNull

/** A TCP port from [value], or null when it is absent, not a number or out of range. */
internal fun parseMcpPort(value: String?): Int? = value?.trim()?.toIntOrNull()?.takeIf { it in 1..MAX_PORT }

private val MAC = Regex("^[0-9A-Fa-f]{2}(:[0-9A-Fa-f]{2}){5}$")

/** A bare BLE MAC gets the app's Bluetooth prefix; an already-prefixed app address passes through. */
internal fun normalizeMcpAddress(raw: String): String {
    val trimmed = raw.trim()
    return if (MAC.matches(trimmed)) "${InterfaceId.BLUETOOTH.id}${trimmed.uppercase()}" else trimmed
}
