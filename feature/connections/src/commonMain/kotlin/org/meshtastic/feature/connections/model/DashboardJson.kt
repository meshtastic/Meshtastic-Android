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
package org.meshtastic.feature.connections.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

// A node-kmp dashboard body read as loosely as the host writes it.

private val JSON = Json { ignoreUnknownKeys = true }

// A body that is not JSON reads as no body.
@Suppress("SwallowedException")
internal fun parse(body: String?): JsonObject? = body?.let {
    try {
        JSON.parseToJsonElement(it) as? JsonObject
    } catch (e: IllegalArgumentException) {
        null
    }
}

private fun JsonObject.primitive(key: String): JsonPrimitive? =
    (get(key) as? JsonPrimitive)?.takeUnless { it is JsonNull }

internal fun JsonObject.string(key: String): String? = primitive(key)?.contentOrNull

internal fun JsonObject.long(key: String): Long? = primitive(key)?.longOrNull

internal fun JsonObject.int(key: String): Int? = primitive(key)?.intOrNull

internal fun JsonObject.bool(key: String): Boolean? = primitive(key)?.booleanOrNull

internal fun JsonObject.array(key: String): List<JsonElement> = (get(key) as? JsonArray).orEmpty()
