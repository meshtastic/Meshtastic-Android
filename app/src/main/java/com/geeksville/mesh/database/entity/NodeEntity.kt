/*
 * Copyright (c) 2024 Meshtastic LLC
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

package com.geeksville.mesh.database.entity

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import com.geeksville.mesh.DeviceMetrics
import com.geeksville.mesh.EnvironmentMetrics
import com.geeksville.mesh.MeshProtos
import com.geeksville.mesh.MeshUser
import com.geeksville.mesh.NodeInfo
import com.geeksville.mesh.PaxcountProtos
import com.geeksville.mesh.Position
import com.geeksville.mesh.TelemetryProtos
import com.geeksville.mesh.copy
import com.geeksville.mesh.model.Node
import com.google.protobuf.ByteString

data class NodeWithRelations(
    @Embedded val node: NodeEntity,
    @Relation(entity = MetadataEntity::class, parentColumn = "num", entityColumn = "num")
    val metadata: MetadataEntity? = null,
) {
    fun toModel() = with(node) {
        Node(
            num = num,
            metadata = metadata?.proto,
            user = user,
            position = position,
            snr = snr,
            rssi = rssi,
            lastHeard = lastHeard,
            deviceMetrics = deviceTelemetry.deviceMetrics,
            channel = channel,
            viaMqtt = viaMqtt,
            hopsAway = hopsAway,
            isFavorite = isFavorite,
            isIgnored = isIgnored,
            environmentMetrics = environmentTelemetry.environmentMetrics,
            powerMetrics = powerTelemetry.powerMetrics,
            paxcounter = paxcounter,
        )
    }

    fun toEntity() = with(node) {
        NodeEntity(
            num = num,
            user = user,
            position = position,
            snr = snr,
            rssi = rssi,
            lastHeard = lastHeard,
            deviceTelemetry = deviceTelemetry,
            channel = channel,
            viaMqtt = viaMqtt,
            hopsAway = hopsAway,
            isFavorite = isFavorite,
            isIgnored = isIgnored,
            environmentTelemetry = environmentTelemetry,
            powerTelemetry = powerTelemetry,
            paxcounter = paxcounter,
        )
    }
}

@Entity(
    tableName = "metadata",
    indices = [
        Index(value = ["num"]),
    ],
)
data class MetadataEntity(
    @PrimaryKey val num: Int,
    @ColumnInfo(name = "proto", typeAffinity = ColumnInfo.BLOB)
    val proto: MeshProtos.DeviceMetadata,
    val timestamp: Long = System.currentTimeMillis(),
)

@Suppress("MagicNumber")
@Entity(tableName = "nodes")
data class NodeEntity(
    @PrimaryKey(autoGenerate = false)
    val num: Int, // This is immutable, and used as a key

    @ColumnInfo(typeAffinity = ColumnInfo.BLOB)
    var user: MeshProtos.User = MeshProtos.User.getDefaultInstance(),
    @ColumnInfo(name = "long_name") var longName: String? = null,
    @ColumnInfo(name = "short_name") var shortName: String? = null, // used in includeUnknown filter

    @ColumnInfo(typeAffinity = ColumnInfo.BLOB)
    var position: MeshProtos.Position = MeshProtos.Position.getDefaultInstance(),
    var latitude: Double = 0.0,
    var longitude: Double = 0.0,

    var snr: Float = Float.MAX_VALUE,
    var rssi: Int = Int.MAX_VALUE,

    @ColumnInfo(name = "last_heard")
    var lastHeard: Int = 0, // the last time we've seen this node in secs since 1970

    @ColumnInfo(name = "device_metrics", typeAffinity = ColumnInfo.BLOB)
    var deviceTelemetry: TelemetryProtos.Telemetry = TelemetryProtos.Telemetry.getDefaultInstance(),

    var channel: Int = 0,

    @ColumnInfo(name = "via_mqtt")
    var viaMqtt: Boolean = false,

    @ColumnInfo(name = "hops_away")
    var hopsAway: Int = -1,

    @ColumnInfo(name = "is_favorite")
    var isFavorite: Boolean = false,

    @ColumnInfo(name = "is_ignored", defaultValue = "0")
    var isIgnored: Boolean = false,

    @ColumnInfo(name = "environment_metrics", typeAffinity = ColumnInfo.BLOB)
    var environmentTelemetry: TelemetryProtos.Telemetry = TelemetryProtos.Telemetry.getDefaultInstance(),

    @ColumnInfo(name = "power_metrics", typeAffinity = ColumnInfo.BLOB)
    var powerTelemetry: TelemetryProtos.Telemetry = TelemetryProtos.Telemetry.getDefaultInstance(),

    @ColumnInfo(typeAffinity = ColumnInfo.BLOB)
    var paxcounter: PaxcountProtos.Paxcount = PaxcountProtos.Paxcount.getDefaultInstance(),
) {
    val deviceMetrics: TelemetryProtos.DeviceMetrics
        get() = deviceTelemetry.deviceMetrics

    val environmentMetrics: TelemetryProtos.EnvironmentMetrics
        get() = environmentTelemetry.environmentMetrics

    val isUnknownUser get() = user.hwModel == MeshProtos.HardwareModel.UNSET
    val hasPKC get() = !user.publicKey.isEmpty
    val errorByteString: ByteString get() = ByteString.copyFrom(ByteArray(32) { 0 })

    fun setPosition(p: MeshProtos.Position, defaultTime: Int = currentTime()) {
        position = p.copy { time = if (p.time != 0) p.time else defaultTime }
        latitude = degD(p.latitudeI)
        longitude = degD(p.longitudeI)
    }

    /**
     * true if the device was heard from recently
     */
    val isOnline: Boolean
        get() {
            val now = System.currentTimeMillis() / 1000
            val timeout = 2 * 60 * 60
            return (now - lastHeard <= timeout)
        }

    companion object {
        // Convert to a double representation of degrees
        fun degD(i: Int) = i * 1e-7
        fun degI(d: Double) = (d * 1e7).toInt()

        fun currentTime() = (System.currentTimeMillis() / 1000).toInt()
    }

    fun toNodeInfo() = NodeInfo(
        num = num,
        user = MeshUser(
            id = user.id,
            longName = user.longName,
            shortName = user.shortName,
            hwModel = user.hwModel,
            role = user.roleValue,
        ).takeIf { user.id.isNotEmpty() },
        position = Position(
            latitude = latitude,
            longitude = longitude,
            altitude = position.altitude,
            time = position.time,
            satellitesInView = position.satsInView,
            groundSpeed = position.groundSpeed,
            groundTrack = position.groundTrack,
            precisionBits = position.precisionBits,
        ).takeIf { it.isValid() },
        snr = snr,
        rssi = rssi,
        lastHeard = lastHeard,
        deviceMetrics = DeviceMetrics(
            time = deviceTelemetry.time,
            batteryLevel = deviceMetrics.batteryLevel,
            voltage = deviceMetrics.voltage,
            channelUtilization = deviceMetrics.channelUtilization,
            airUtilTx = deviceMetrics.airUtilTx,
            uptimeSeconds = deviceMetrics.uptimeSeconds,
        ),
        channel = channel,
        environmentMetrics = EnvironmentMetrics(
            time = environmentTelemetry.time,
            temperature = environmentMetrics.temperature,
            relativeHumidity = environmentMetrics.relativeHumidity,
            barometricPressure = environmentMetrics.barometricPressure,
            gasResistance = environmentMetrics.gasResistance,
            voltage = environmentMetrics.voltage,
            current = environmentMetrics.current,
            iaq = environmentMetrics.iaq,
        ),
        hopsAway = hopsAway,
    )
}
