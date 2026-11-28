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

import com.squareup.wire.Message
import com.squareup.wire.WireEnum
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import okio.ByteString
import java.lang.reflect.Field
import java.lang.reflect.Modifier

private const val UINT32_MAX = 0xFFFF_FFFFL

/** The public instance field [name] of a Wire message or builder, which Wire generates for every proto field. */
private fun Class<*>.protoField(name: String): Field? = runCatching {
    getField(name)
}
    .getOrNull()
    ?.takeUnless { Modifier.isStatic(it.modifiers) }

/** Returns [message] with each of [values] written into the proto field of that name. */
internal fun withMcpFields(message: Message<*, *>, values: Map<String, Any>): Message<*, *> {
    val builder = message.newBuilder()
    values.forEach { (name, value) ->
        val field = builder.javaClass.protoField(name) ?: throw McpFieldException("unknown field $name")
        field.set(builder, value)
    }
    return builder.build()
}

/** The value of proto field [name] in [message]. */
internal fun readMcpField(message: Message<*, *>, name: String): Any? = message.javaClass.protoField(name)?.get(message)

/**
 * [raw] converted to the JVM type of proto field [name] on [type]. Byte strings, repeated and nested message fields are
 * refused, so a key or a list cannot be written by accident.
 */
internal fun coerceMcpField(type: Class<*>, name: String, raw: JsonPrimitive): Any {
    val field = type.protoField(name) ?: throw McpFieldException("unknown field $name")
    return coerceMcpValue(field.type, raw)
        ?: throw McpFieldException("$name: ${raw.content} is not a ${field.type.simpleName}")
}

private fun coerceMcpValue(type: Class<*>, raw: JsonPrimitive): Any? = when (type) {
    Int::class.javaPrimitiveType,
    Int::class.javaObjectType,
    -> raw.longOrNull?.takeIf { it in Int.MIN_VALUE..UINT32_MAX }?.toInt()

    Long::class.javaPrimitiveType,
    Long::class.javaObjectType,
    -> raw.longOrNull

    Boolean::class.javaPrimitiveType,
    Boolean::class.javaObjectType,
    -> raw.booleanOrNull

    Float::class.javaPrimitiveType,
    Float::class.javaObjectType,
    -> raw.floatOrNull?.takeIf { it.isFinite() }

    Double::class.javaPrimitiveType,
    Double::class.javaObjectType,
    -> raw.doubleOrNull?.takeIf { it.isFinite() }

    String::class.java -> raw.content.takeIf { raw.isString }

    else -> if (type.isEnum) enumConstant(type, raw) else null
}

/** The constant named by [raw], case-insensitively, or the one whose wire value [raw] is. */
private fun enumConstant(type: Class<*>, raw: JsonPrimitive): Any? {
    val constants = type.enumConstants.orEmpty()
    return constants.firstOrNull { (it as Enum<*>).name.equals(raw.content, ignoreCase = true) }
        ?: raw.takeUnless { it.isString }
            ?.intOrNull
            ?.let { v -> constants.firstOrNull { (it as? WireEnum)?.value == v } }
}

/** [value] as JSON: enums by name, messages by field, byte strings as their length only, never their content. */
internal fun mcpJson(value: Any?): JsonElement = when (value) {
    null -> JsonNull

    is Boolean -> JsonPrimitive(value)

    is Number -> JsonPrimitive(value)

    is String -> JsonPrimitive(value)

    is Enum<*> -> JsonPrimitive(value.name)

    is ByteString -> JsonPrimitive("<${value.size} bytes>")

    is List<*> -> JsonArray(value.map(::mcpJson))

    is Message<*, *> ->
        JsonObject(
            value.javaClass.fields
                .filterNot { Modifier.isStatic(it.modifiers) }
                .associate { it.name to mcpJson(it.get(value)) },
        )

    else -> JsonPrimitive(value.toString())
}

/** A bench-supplied field name or value that does not fit the proto it targets. */
internal class McpFieldException(message: String) : IllegalArgumentException(message)
