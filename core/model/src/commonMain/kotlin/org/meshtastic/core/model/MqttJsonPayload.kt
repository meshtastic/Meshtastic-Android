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
package org.meshtastic.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonTransformingSerializer

@Serializable
data class MqttJsonPayload(
    val type: String,
    val from: Long,
    val to: Long? = null,
    val channel: Int? = null,
    @Serializable(with = MqttPayloadStringSerializer::class) val payload: String? = null,
    @SerialName("hop_limit") val hopLimit: Int? = null,
    val id: Long? = null,
    val time: Long? = null,
    val sender: String? = null,
    // Add other fields as needed for position/telemetry
)

// Firmware and MQTT bridges send "payload" as a string for text messages but as a nested JSON
// object for position/telemetry/map reports; coerce non-strings to their compact JSON text.
private object MqttPayloadStringSerializer : JsonTransformingSerializer<String>(String.serializer()) {
    override fun transformDeserialize(element: JsonElement): JsonElement =
        if (element is JsonPrimitive && element.isString) element else JsonPrimitive(element.toString())
}
