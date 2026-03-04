/*
 * Copyright (c) 2025-2026 Meshtastic LLC
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
package org.meshtastic.core.database

import androidx.room.TypeConverter
import co.touchlab.kermit.Logger
import kotlinx.serialization.json.Json
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.model.MessageStatus
import org.meshtastic.core.model.util.decodeOrNull
import org.meshtastic.proto.DeviceMetadata
import org.meshtastic.proto.FromRadio
import org.meshtastic.proto.Paxcount
import org.meshtastic.proto.Position
import org.meshtastic.proto.Telemetry
import org.meshtastic.proto.User

@Suppress("TooManyFunctions")
class Converters {
    private val json = Json {
        isLenient = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @TypeConverter fun dataFromString(value: String): DataPacket = json.decodeFromString(DataPacket.serializer(), value)

    @TypeConverter fun dataToString(value: DataPacket): String = json.encodeToString(DataPacket.serializer(), value)

    @TypeConverter
    fun bytesToFromRadio(bytes: ByteArray): FromRadio = FromRadio.ADAPTER.decodeOrNull(bytes, Logger) ?: FromRadio()

    @TypeConverter fun fromRadioToBytes(value: FromRadio): ByteArray = FromRadio.ADAPTER.encode(value)

    @TypeConverter fun bytesToUser(bytes: ByteArray): User = User.ADAPTER.decodeOrNull(bytes, Logger) ?: User()

    @TypeConverter fun userToBytes(value: User): ByteArray = User.ADAPTER.encode(value)

    @TypeConverter
    fun bytesToPosition(bytes: ByteArray): Position = Position.ADAPTER.decodeOrNull(bytes, Logger) ?: Position()

    @TypeConverter fun positionToBytes(value: Position): ByteArray = Position.ADAPTER.encode(value)

    @TypeConverter
    fun bytesToTelemetry(bytes: ByteArray): Telemetry = Telemetry.ADAPTER.decodeOrNull(bytes, Logger) ?: Telemetry()

    @TypeConverter fun telemetryToBytes(value: Telemetry): ByteArray = Telemetry.ADAPTER.encode(value)

    @TypeConverter
    fun bytesToPaxcounter(bytes: ByteArray): Paxcount = Paxcount.ADAPTER.decodeOrNull(bytes, Logger) ?: Paxcount()

    @TypeConverter fun paxCounterToBytes(value: Paxcount): ByteArray = Paxcount.ADAPTER.encode(value)

    @TypeConverter
    fun bytesToMetadata(bytes: ByteArray): DeviceMetadata =
        DeviceMetadata.ADAPTER.decodeOrNull(bytes, Logger) ?: DeviceMetadata()

    @TypeConverter fun metadataToBytes(value: DeviceMetadata): ByteArray = DeviceMetadata.ADAPTER.encode(value)

    @TypeConverter
    fun fromStringList(value: String?): List<String>? {
        if (value == null) {
            return null
        }
        return Json.decodeFromString<List<String>>(value)
    }

    @TypeConverter
    fun toStringList(list: List<String>?): String? {
        if (list == null) {
            return null
        }
        return Json.encodeToString(list)
    }

    @TypeConverter fun bytesToByteString(bytes: ByteArray?): ByteString? = bytes?.toByteString()

    @TypeConverter fun byteStringToBytes(value: ByteString?): ByteArray? = value?.toByteArray()

    @TypeConverter fun messageStatusToInt(value: MessageStatus?): Int = value?.ordinal ?: MessageStatus.UNKNOWN.ordinal

    @TypeConverter
    fun intToMessageStatus(value: Int): MessageStatus = MessageStatus.entries.getOrElse(value) { MessageStatus.UNKNOWN }
}
