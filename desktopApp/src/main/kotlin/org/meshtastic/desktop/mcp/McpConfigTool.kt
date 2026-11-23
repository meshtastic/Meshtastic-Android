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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.meshtastic.core.domain.usecase.settings.RadioConfigUseCase
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
import org.meshtastic.sdk.ConnectionState
import org.meshtastic.sdk.getOrNull
import org.meshtastic.sdk.map
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * `set_config`: writes `section.field` values one section at a time through [RadioConfigUseCase], the call the settings
 * screens make. A local write waits for the app's config flows to hold the values; a remote write reads them back.
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
            "Write config through the settings screens' path. values maps section.field to a value, e.g. " +
                "{\"lora.region\": \"US\", \"owner.long_name\": \"Bench\"}; sections are owner, the Config sections " +
                "(lora, device, ...) and the module sections (mqtt, telemetry, ...). Each section is sent whole. " +
                "Without dest, or with the local node's id, it goes to the local node with the rest taken from the " +
                "config the app holds, then waits up to wait_seconds for the app's config to hold the values. With " +
                "dest (\"!aabbccdd\") it goes to that remote node through remote admin: each section is read from " +
                "the node first, and read back for up to wait_seconds. Reports each AdminResult.",
            inputSchema =
            ToolSchema(
                properties =
                buildJsonObject {
                    putJsonObject("values") { put("type", "object") }
                    putJsonObject("dest") { put("type", "string") }
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
        val request =
            try {
                val values =
                    (args?.get("values") as? JsonObject)?.takeIf { it.isNotEmpty() }
                        ?: throw McpFieldException("values must be a non-empty object")
                val sections = groupMcpConfigValues(values)
                Result.success(sections to myNum?.let { parseMcpConfigDest(args.stringArg("dest"), it) })
            } catch (e: McpFieldException) {
                Result.failure(e)
            }
        val wait = (args.intArg("wait_seconds") ?: DEFAULT_WAIT_S).seconds
        val (sections, dest) = request.getOrNull() ?: (null to null)
        return when {
            myNum == null || !connected -> toolError("not connected")
            sections == null -> toolError(request.exceptionOrNull()?.message.orEmpty())
            dest is McpConfigDest.Remote -> setRemoteConfig(sections, dest.num, wait)
            else -> setLocalConfig(sections, myNum, wait)
        }
    }

    private suspend fun setLocalConfig(sections: McpConfigValues, before: Int, wait: Duration): CallToolResult {
        val writes =
            try {
                planMcpConfigWrites(sections, heldConfig().first())
            } catch (e: McpFieldException) {
                return toolError(e.message.orEmpty())
            }
        return toolJson(apply(writes, before, wait))
    }

    private suspend fun sendAll(writes: List<McpConfigWrite>, destFor: () -> Int): Pair<JsonArray, Boolean> {
        var allAccepted = true
        val results = buildJsonArray {
            for (write in writes) {
                val dest = destFor()
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
        return results to allAccepted
    }

    private suspend fun apply(writes: List<McpConfigWrite>, before: Int, wait: Duration): JsonObject {
        val started = TimeSource.Monotonic.markNow()
        // A first region set renumbers the node, so each section goes to the number the app holds now.
        val (results, allAccepted) = sendAll(writes) { nodeRepository.myNodeInfo.value?.myNodeNum ?: before }
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
            put("held", heldJson(writes, now))
            put("state", serviceRepository.connectionState.value.toString())
            put("elapsed_ms", started.elapsedNow().inWholeMilliseconds)
        }
    }

    /**
     * Remote admin, as the settings screens do it for a remote node: read each section from [dest], write it back whole
     * with the requested fields changed, then read it back. The SDK supplies the node's session passkey.
     */
    private suspend fun setRemoteConfig(sections: McpConfigValues, dest: Int, wait: Duration): CallToolResult {
        val started = TimeSource.Monotonic.markNow()
        val plan =
            try {
                planRemote(sections, dest)
            } catch (e: McpFieldException) {
                return toolError(e.message.orEmpty())
            }
        val (kinds, before, writes) = plan
        val (results, allAccepted) = sendAll(writes) { dest }
        val held =
            if (allAccepted) {
                withTimeoutOrNull(wait - started.elapsedNow()) {
                    var readBack = readRemote(dest, kinds).getOrNull()
                    while (readBack == null || !writes.all { it.heldBy(readBack) }) {
                        delay(REMOTE_RECHECK)
                        readBack = readRemote(dest, kinds).getOrNull()
                    }
                    readBack
                }
            } else {
                null
            }
        return toolJson(
            buildJsonObject {
                put("dest", dest.toMcpNodeId())
                put("writes", results)
                put("matched", held != null)
                put("held", heldJson(writes, held ?: before))
                put("state", serviceRepository.connectionState.value.toString())
                put("elapsed_ms", started.elapsedNow().inWholeMilliseconds)
            },
        )
    }

    private data class RemotePlan(
        val kinds: Map<String, McpSectionKind>,
        val before: McpHeldConfig,
        val writes: List<McpConfigWrite>,
    )

    private suspend fun planRemote(sections: McpConfigValues, dest: Int): RemotePlan {
        val kinds = sections.keys.associateWith { mcpSectionKind(it) ?: throw McpFieldException("unknown section $it") }
        val read = readRemote(dest, kinds)
        val before =
            read.getOrNull()
                ?: throw McpFieldException("reading ${dest.toMcpNodeId()} failed: ${read::class.simpleName}")
        return RemotePlan(kinds, before, planMcpConfigWrites(sections, before))
    }

    /** Reads each of [kinds]' sections from [dest] into one [McpHeldConfig]; the first failed read ends it. */
    private suspend fun readRemote(dest: Int, kinds: Map<String, McpSectionKind>): AdminResult<McpHeldConfig> {
        val config = LocalConfig.Builder()
        val modules = LocalModuleConfig.Builder()
        var owner: User? = null
        for ((section, kind) in kinds) {
            val read: AdminResult<Message<*, *>> =
                when (kind) {
                    McpSectionKind.OWNER -> radioConfigUseCase.getOwner(dest)
                    McpSectionKind.CONFIG -> radioConfigUseCase.getConfig(dest, mcpSectionType(kind, section))
                    McpSectionKind.MODULE -> radioConfigUseCase.getModuleConfig(dest, mcpSectionType(kind, section))
                }
            val value =
                when (read) {
                    is AdminResult.Success -> read.value
                    else -> return read.map { error("a failed read has no value") }
                }
            when (kind) {
                McpSectionKind.OWNER -> owner = value as User
                McpSectionKind.CONFIG -> config.javaClass.getField(section).set(config, readMcpField(value, section))
                McpSectionKind.MODULE -> modules.javaClass.getField(section).set(modules, readMcpField(value, section))
            }
        }
        return AdminResult.Success(McpHeldConfig(config.build(), modules.build(), owner))
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
        val REMOTE_RECHECK = 2.seconds
    }
}

private fun heldJson(writes: List<McpConfigWrite>, held: McpHeldConfig) = buildJsonObject {
    writes.forEach { write ->
        write.requested.keys.forEach { field ->
            put("${write.section}.$field", mcpJson(held.valueOf(write.section, field)))
        }
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

/** `section.field` keys and their values, by section in the order given. */
internal typealias McpConfigValues = Map<String, Map<String, JsonPrimitive>>

/** Groups `section.field` [values] by section in the order given. Every key is checked before anything is sent. */
internal fun groupMcpConfigValues(values: JsonObject): McpConfigValues {
    val bySection = LinkedHashMap<String, MutableMap<String, JsonPrimitive>>()
    values.forEach { (key, value) ->
        val parts = key.split('.')
        val primitive = value as? JsonPrimitive
        if (parts.size != 2 || parts.any { it.isBlank() } || primitive == null || primitive is JsonNull) {
            throw McpFieldException("$key: keys are section.field and values are strings, numbers or booleans")
        }
        bySection.getOrPut(parts[0]) { LinkedHashMap() }[parts[1]] = primitive
    }
    return bySection
}

/** Plans `section.field` [values] over [held]; see the [McpConfigValues] overload. */
internal fun planMcpConfigWrites(values: JsonObject, held: McpHeldConfig): List<McpConfigWrite> =
    planMcpConfigWrites(groupMcpConfigValues(values), held)

/** Writes each section's requested fields over the section [held] has, so a section goes out whole. */
internal fun planMcpConfigWrites(bySection: McpConfigValues, held: McpHeldConfig): List<McpConfigWrite> =
    bySection.map { (section, fields) ->
        val (kind, current) = held.locate(section) ?: throw McpFieldException("unknown section $section")
        val requested = fields.mapValues { (name, raw) -> coerceMcpField(current.javaClass, name, raw) }
        McpConfigWrite(kind, section, requested, withMcpFields(current, requested))
    }

private fun McpHeldConfig.locate(section: String): Pair<McpSectionKind, Message<*, *>>? =
    if (section == OWNER_SECTION) {
        McpSectionKind.OWNER to (owner ?: throw McpFieldException("the app holds no owner yet"))
    } else {
        sectionOf(config, section)?.let { McpSectionKind.CONFIG to it }
            ?: sectionOf(modules, section)?.let { McpSectionKind.MODULE to it }
    }

/** Section [name] of [holder], or an empty one when the app has none yet; null when [holder] has no such section. */
internal fun sectionOf(holder: Message<*, *>, name: String): Message<*, *>? {
    val field = holder.javaClass.fields.firstOrNull { it.name == name && Message::class.java.isAssignableFrom(it.type) }
    return field?.let { it.get(holder) as Message<*, *>? ?: emptyMessage(it.type) }
}

private fun emptyMessage(type: Class<*>): Message<*, *> {
    val builder = type.declaredClasses.first { it.simpleName == "Builder" }.getDeclaredConstructor().newInstance()
    return (builder as Message.Builder<*, *>).build()
}
