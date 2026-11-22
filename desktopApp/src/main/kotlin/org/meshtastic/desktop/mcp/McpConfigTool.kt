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

import co.touchlab.kermit.Logger
import com.squareup.wire.Message
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.meshtastic.core.domain.usecase.settings.RadioConfigUseCase
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.core.repository.PacketQueueRejectedException
import org.meshtastic.core.repository.RadioConfigRepository
import org.meshtastic.core.repository.ServiceRepository
import org.meshtastic.proto.Config
import org.meshtastic.proto.LocalConfig
import org.meshtastic.proto.LocalModuleConfig
import org.meshtastic.proto.ModuleConfig
import org.meshtastic.proto.User
import org.meshtastic.sdk.AdminResult
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * `set_config`: writes `section.field` values to the local node one section at a time through [RadioConfigUseCase], the
 * call the settings screens make, then waits for the app's config flows to hold them.
 */
internal class McpConfigTool(
    private val radioConfigUseCase: RadioConfigUseCase,
    private val radioConfigRepository: RadioConfigRepository,
    private val nodeRepository: NodeRepository,
    private val serviceRepository: ServiceRepository,
) : McpToolGroup {

    override fun register(server: Server) {
        server.addTool(
            name = "set_config",
            description =
            "Write config to the local node through the settings screens' path. values maps section.field to a " +
                "value, e.g. {\"lora.region\": \"US\", \"owner.long_name\": \"Bench\"}; sections are owner, the " +
                "Config sections (lora, device, ...) and the module sections (mqtt, telemetry, ...). Each section is " +
                "sent whole, the rest taken from the config the app holds. Reports each AdminResult, then waits up " +
                "to wait_seconds for the app's config to hold the values.",
            inputSchema =
            ToolSchema(
                properties =
                buildJsonObject {
                    putJsonObject("values") { put("type", "object") }
                    putJsonObject("wait_seconds") { put("type", "integer") }
                },
                required = listOf("values"),
            ),
        ) { request ->
            setConfig(request.arguments)
        }
    }

    private suspend fun setConfig(args: JsonObject?): CallToolResult {
        val myNum = nodeRepository.myNodeInfo.value?.myNodeNum
        val connected = serviceRepository.connectionState.value == ConnectionState.Connected
        val plan =
            try {
                val values =
                    (args?.get("values") as? JsonObject)?.takeIf { it.isNotEmpty() }
                        ?: throw McpFieldException("values must be a non-empty object")
                Result.success(planMcpConfigWrites(values, heldConfig().first()))
            } catch (e: McpFieldException) {
                Result.failure(e)
            }
        val wait = (args.intArg("wait_seconds") ?: DEFAULT_WAIT_S).seconds
        return when {
            myNum == null || !connected -> toolError("not connected")
            plan.isFailure -> toolError(plan.exceptionOrNull()?.message.orEmpty())
            else -> toolJson(apply(plan.getOrThrow(), myNum, wait))
        }
    }

    private suspend fun apply(writes: List<McpConfigWrite>, before: Int, wait: Duration): JsonObject {
        val started = TimeSource.Monotonic.markNow()
        var allAccepted = true
        val results = buildJsonArray {
            for (write in writes) {
                // A first region set renumbers the node, so each section goes to the number the app holds now.
                val dest = nodeRepository.myNodeInfo.value?.myNodeNum ?: before
                val result =
                    try {
                        send(write, dest)
                    } catch (e: PacketQueueRejectedException) {
                        Logger.w(e) { "MCP set_config ${write.section} rejected" }
                        null
                    }
                add(adminResultJson(write.section, dest, result))
                if (result !is AdminResult.Success) {
                    allAccepted = false
                    break
                }
            }
        }
        val held =
            if (allAccepted) {
                withTimeoutOrNull(wait - started.elapsedNow()) {
                    heldConfig().first { held -> writes.all { it.heldBy(held) } }
                }
            } else {
                null
            }
        val now = held ?: heldConfig().first()
        return buildJsonObject {
            put("my_node_num_before", before.toUInt().toLong())
            put("my_node_num_after", nodeRepository.myNodeInfo.value?.myNodeNum?.toUInt()?.toLong())
            put("writes", results)
            put("matched", held != null)
            putJsonObject("held") {
                writes.forEach { write ->
                    write.requested.keys.forEach { field ->
                        put("${write.section}.$field", mcpJson(now.valueOf(write.section, field)))
                    }
                }
            }
            put("state", serviceRepository.connectionState.value.toString())
            put("elapsed_ms", started.elapsedNow().inWholeMilliseconds)
        }
    }

    private suspend fun send(write: McpConfigWrite, dest: Int): AdminResult<Unit> = when (write.kind) {
        McpSectionKind.OWNER -> radioConfigUseCase.setOwner(dest, write.updated as User)
        McpSectionKind.CONFIG -> radioConfigUseCase.setConfig(dest, write.toConfig())
        McpSectionKind.MODULE -> radioConfigUseCase.setModuleConfig(dest, write.toModuleConfig())
    }

    private fun heldConfig(): Flow<McpHeldConfig> = combine(
        radioConfigRepository.localConfigFlow,
        radioConfigRepository.moduleConfigFlow,
        nodeRepository.ourNodeInfo,
    ) { config, modules, ours ->
        McpHeldConfig(config, modules, ours?.user)
    }

    private companion object {
        const val DEFAULT_WAIT_S = 30
    }
}

private fun adminResultJson(section: String, dest: Int, result: AdminResult<Unit>?) = buildJsonObject {
    put("section", section)
    put("dest", dest.toUInt().toLong())
    put("result", result?.let { it::class.simpleName } ?: "QueueRejected")
    put("routing_error", (result as? AdminResult.Failed)?.routingError?.name)
}

internal const val OWNER_SECTION = "owner"

internal enum class McpSectionKind {
    OWNER,
    CONFIG,
    MODULE,
}

/** The config the app holds: its local config and module config flows, and the owner from its node DB. */
internal data class McpHeldConfig(val config: LocalConfig, val modules: LocalModuleConfig, val owner: User?)

/** One section to send: [updated] is the held section with [requested] written over it. */
internal class McpConfigWrite(
    val kind: McpSectionKind,
    val section: String,
    val requested: Map<String, Any>,
    val updated: Message<*, *>,
) {
    fun toConfig(): Config = Config.Builder().also { it.javaClass.getField(section).set(it, updated) }.build()

    fun toModuleConfig(): ModuleConfig =
        ModuleConfig.Builder().also { it.javaClass.getField(section).set(it, updated) }.build()

    fun heldBy(held: McpHeldConfig): Boolean = requested.all { (field, value) -> held.valueOf(section, field) == value }
}

internal fun McpHeldConfig.valueOf(section: String, field: String): Any? =
    locate(section)?.second?.let { readMcpField(it, field) }

/**
 * Groups `section.field` [values] by section in the order given and writes each over the section [held] has, so a
 * section goes out whole. Every key is checked before anything is sent.
 */
internal fun planMcpConfigWrites(values: JsonObject, held: McpHeldConfig): List<McpConfigWrite> {
    val bySection = LinkedHashMap<String, MutableMap<String, JsonPrimitive>>()
    values.forEach { (key, value) ->
        val parts = key.split('.')
        val primitive = value as? JsonPrimitive
        if (parts.size != 2 || parts.any { it.isBlank() } || primitive == null || primitive is JsonNull) {
            throw McpFieldException("$key: keys are section.field and values are strings, numbers or booleans")
        }
        bySection.getOrPut(parts[0]) { LinkedHashMap() }[parts[1]] = primitive
    }
    return bySection.map { (section, fields) ->
        val (kind, current) = held.locate(section) ?: throw McpFieldException("unknown section $section")
        val requested = fields.mapValues { (name, raw) -> coerceMcpField(current.javaClass, name, raw) }
        McpConfigWrite(kind, section, requested, withMcpFields(current, requested))
    }
}

private fun McpHeldConfig.locate(section: String): Pair<McpSectionKind, Message<*, *>>? =
    if (section == OWNER_SECTION) {
        McpSectionKind.OWNER to (owner ?: throw McpFieldException("the app holds no owner yet"))
    } else {
        sectionOf(config, section)?.let { McpSectionKind.CONFIG to it }
            ?: sectionOf(modules, section)?.let { McpSectionKind.MODULE to it }
    }

/** Section [name] of [holder], or an empty one when the app has none yet; null when [holder] has no such section. */
private fun sectionOf(holder: Message<*, *>, name: String): Message<*, *>? {
    val field = holder.javaClass.fields.firstOrNull { it.name == name && Message::class.java.isAssignableFrom(it.type) }
    return field?.let { it.get(holder) as Message<*, *>? ?: emptyMessage(it.type) }
}

private fun emptyMessage(type: Class<*>): Message<*, *> {
    val builder = type.declaredClasses.first { it.simpleName == "Builder" }.getDeclaredConstructor().newInstance()
    return (builder as Message.Builder<*, *>).build()
}
