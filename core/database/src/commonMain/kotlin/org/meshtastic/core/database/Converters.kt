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
package org.meshtastic.core.database

import androidx.room3.ColumnTypeConverter
import co.touchlab.kermit.Logger
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.model.MessageStatus
import org.meshtastic.core.model.util.decodeOrNull
import org.meshtastic.proto.ChannelSet
import org.meshtastic.proto.ChannelSettings
import org.meshtastic.proto.Config
import org.meshtastic.proto.DeviceMetadata
import org.meshtastic.proto.FromRadio
import org.meshtastic.proto.Paxcount
import org.meshtastic.proto.Position
import org.meshtastic.proto.Telemetry
import org.meshtastic.proto.User

@Suppress("TooManyFunctions")
class Converters {
    @OptIn(ExperimentalSerializationApi::class)
    private val json = Json {
        isLenient = true
        ignoreUnknownKeys = true
        encodeDefaults = true
        exceptionsWithDebugInfo = false
    }

    @ColumnTypeConverter
    fun dataFromString(value: String): DataPacket = json.decodeFromString(DataPacket.serializer(), value)

    @ColumnTypeConverter
    fun dataToString(value: DataPacket): String = json.encodeToString(DataPacket.serializer(), value)

    @ColumnTypeConverter
    fun bytesToFromRadio(bytes: ByteArray): FromRadio = FromRadio.ADAPTER.decodeOrNull(bytes, Logger) ?: FromRadio()

    @ColumnTypeConverter fun fromRadioToBytes(value: FromRadio): ByteArray = FromRadio.ADAPTER.encode(value)

    @ColumnTypeConverter fun bytesToUser(bytes: ByteArray): User = User.ADAPTER.decodeOrNull(bytes, Logger) ?: User()

    @ColumnTypeConverter fun userToBytes(value: User): ByteArray = User.ADAPTER.encode(value)

    @ColumnTypeConverter
    fun bytesToPosition(bytes: ByteArray): Position = Position.ADAPTER.decodeOrNull(bytes, Logger) ?: Position()

    @ColumnTypeConverter fun positionToBytes(value: Position): ByteArray = Position.ADAPTER.encode(value)

    @ColumnTypeConverter
    fun bytesToTelemetry(bytes: ByteArray): Telemetry = Telemetry.ADAPTER.decodeOrNull(bytes, Logger) ?: Telemetry()

    @ColumnTypeConverter fun telemetryToBytes(value: Telemetry): ByteArray = Telemetry.ADAPTER.encode(value)

    @ColumnTypeConverter
    fun bytesToPaxcounter(bytes: ByteArray): Paxcount = Paxcount.ADAPTER.decodeOrNull(bytes, Logger) ?: Paxcount()

    @ColumnTypeConverter fun paxCounterToBytes(value: Paxcount): ByteArray = Paxcount.ADAPTER.encode(value)

    @ColumnTypeConverter
    fun bytesToMetadata(bytes: ByteArray): DeviceMetadata =
        DeviceMetadata.ADAPTER.decodeOrNull(bytes, Logger) ?: DeviceMetadata()

    @ColumnTypeConverter fun metadataToBytes(value: DeviceMetadata): ByteArray = DeviceMetadata.ADAPTER.encode(value)

    @ColumnTypeConverter
    fun bytesToChannelSet(bytes: ByteArray): ChannelSet = ChannelSet.ADAPTER.decodeOrNull(bytes, Logger) ?: ChannelSet()

    @ColumnTypeConverter fun channelSetToBytes(value: ChannelSet): ByteArray = ChannelSet.ADAPTER.encode(value)

    @ColumnTypeConverter
    fun fromStringList(value: String?): List<String>? {
        if (value == null) {
            return null
        }
        return Json.decodeFromString<List<String>>(value)
    }

    @ColumnTypeConverter
    fun toStringList(list: List<String>?): String? {
        if (list == null) {
            return null
        }
        return Json.encodeToString(list)
    }

    @ColumnTypeConverter fun bytesToByteString(bytes: ByteArray?): ByteString? = bytes?.toByteString()

    @ColumnTypeConverter fun byteStringToBytes(value: ByteString?): ByteArray? = value?.toByteArray()

    /** Discovery scans capture the radio's pre-scan LoRa config so an interrupted scan can restore it later. */
    @ColumnTypeConverter
    fun bytesToLoRaConfig(bytes: ByteArray?): Config.LoRaConfig? =
        bytes?.let { Config.LoRaConfig.ADAPTER.decodeOrNull(it, Logger) }

    @ColumnTypeConverter
    fun loRaConfigToBytes(value: Config.LoRaConfig?): ByteArray? = value?.let { Config.LoRaConfig.ADAPTER.encode(it) }

    /** Discovery scans capture the radio's pre-scan primary channel to restore after a custom-channel target. */
    @ColumnTypeConverter
    fun bytesToChannelSettings(bytes: ByteArray?): ChannelSettings? =
        bytes?.let { ChannelSettings.ADAPTER.decodeOrNull(it, Logger) }

    @ColumnTypeConverter
    fun channelSettingsToBytes(value: ChannelSettings?): ByteArray? = value?.let { ChannelSettings.ADAPTER.encode(it) }

    @ColumnTypeConverter
    fun messageStatusToInt(value: MessageStatus?): Int = value?.ordinal ?: MessageStatus.UNKNOWN.ordinal

    @ColumnTypeConverter
    fun intToMessageStatus(value: Int): MessageStatus = MessageStatus.entries.getOrElse(value) { MessageStatus.UNKNOWN }
}
