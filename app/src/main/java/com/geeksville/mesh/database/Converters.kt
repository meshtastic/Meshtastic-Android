package com.geeksville.mesh.database

import androidx.room.TypeConverter
import com.geeksville.mesh.DataPacket
import com.geeksville.mesh.MeshProtos
import com.geeksville.mesh.PaxcountProtos
import com.geeksville.mesh.TelemetryProtos
import com.geeksville.mesh.android.Logging
import com.google.protobuf.InvalidProtocolBufferException
import kotlinx.serialization.json.Json

@Suppress("TooManyFunctions")
class Converters : Logging {
    @TypeConverter
    fun dataFromString(value: String): DataPacket {
        val json = Json { isLenient = true }
        return json.decodeFromString(DataPacket.serializer(), value)
    }

    @TypeConverter
    fun dataToString(value: DataPacket): String {
        val json = Json { isLenient = true }
        return json.encodeToString(DataPacket.serializer(), value)
    }

    @TypeConverter
    fun bytesToFromRadio(bytes: ByteArray): MeshProtos.FromRadio {
        return try {
            MeshProtos.FromRadio.parseFrom(bytes)
        } catch (ex: InvalidProtocolBufferException) {
            errormsg("bytesToFromRadio TypeConverter error:", ex)
            MeshProtos.FromRadio.getDefaultInstance()
        }
    }

    @TypeConverter
    fun fromRadioToBytes(value: MeshProtos.FromRadio): ByteArray? {
        return value.toByteArray()
    }

    @TypeConverter
    fun bytesToUser(bytes: ByteArray): MeshProtos.User {
        return try {
            MeshProtos.User.parseFrom(bytes)
        } catch (ex: InvalidProtocolBufferException) {
            errormsg("bytesToUser TypeConverter error:", ex)
            MeshProtos.User.getDefaultInstance()
        }
    }

    @TypeConverter
    fun userToBytes(value: MeshProtos.User): ByteArray? {
        return value.toByteArray()
    }

    @TypeConverter
    fun bytesToPosition(bytes: ByteArray): MeshProtos.Position {
        return try {
            MeshProtos.Position.parseFrom(bytes)
        } catch (ex: InvalidProtocolBufferException) {
            errormsg("bytesToPosition TypeConverter error:", ex)
            MeshProtos.Position.getDefaultInstance()
        }
    }

    @TypeConverter
    fun positionToBytes(value: MeshProtos.Position): ByteArray? {
        return value.toByteArray()
    }

    @TypeConverter
    fun bytesToTelemetry(bytes: ByteArray): TelemetryProtos.Telemetry {
        return try {
            TelemetryProtos.Telemetry.parseFrom(bytes)
        } catch (ex: InvalidProtocolBufferException) {
            errormsg("bytesToTelemetry TypeConverter error:", ex)
            TelemetryProtos.Telemetry.getDefaultInstance()
        }
    }

    @TypeConverter
    fun telemetryToBytes(value: TelemetryProtos.Telemetry): ByteArray? {
        return value.toByteArray()
    }

    @TypeConverter
    fun bytesToPaxcounter(bytes: ByteArray): PaxcountProtos.Paxcount {
        return try {
            PaxcountProtos.Paxcount.parseFrom(bytes)
        } catch (ex: InvalidProtocolBufferException) {
            errormsg("bytesToPaxcounter TypeConverter error:", ex)
            PaxcountProtos.Paxcount.getDefaultInstance()
        }
    }

    @TypeConverter
    fun paxCounterToBytes(value: PaxcountProtos.Paxcount): ByteArray? {
        return value.toByteArray()
    }
}
