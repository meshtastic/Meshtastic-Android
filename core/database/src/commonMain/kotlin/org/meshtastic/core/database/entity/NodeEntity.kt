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
package org.meshtastic.core.database.entity

import androidx.room3.ColumnInfo
import androidx.room3.Embedded
import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey
import androidx.room3.Relation
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.meshtastic.core.common.util.nowMillis
import org.meshtastic.core.common.util.nowSeconds
import org.meshtastic.core.model.Node
import org.meshtastic.core.model.util.onlineTimeThreshold
import org.meshtastic.proto.DeviceMetadata
import org.meshtastic.proto.HardwareModel
import org.meshtastic.proto.MeshPacket
import org.meshtastic.proto.Paxcount
import org.meshtastic.proto.Telemetry
import org.meshtastic.proto.User
import org.meshtastic.proto.Position as WirePosition

data class NodeWithRelations(
    @Embedded val node: NodeEntity,
    @Relation(entity = MetadataEntity::class, parentColumns = ["num"], entityColumns = ["num"])
    val metadata: MetadataEntity?,
) {
    // Direct construction avoids the previous `node.toModel().copy(metadata = …, manuallyVerified = …)` pattern,
    // which allocated the Node twice per DB row (once from toModel, once from copy). Hot path on every DB emission.
    fun toModel() = Node(
        num = node.num,
        user = node.user,
        position = node.position,
        snr = node.snr,
        rssi = node.rssi,
        lastHeard = node.lastHeard,
        deviceMetrics = node.deviceMetrics ?: org.meshtastic.proto.DeviceMetrics(),
        channel = node.channel,
        viaMqtt = node.viaMqtt,
        hopsAway = node.hopsAway,
        isFavorite = node.isFavorite,
        isIgnored = node.isIgnored,
        isMuted = node.isMuted,
        environmentMetrics = node.environmentMetrics ?: org.meshtastic.proto.EnvironmentMetrics(),
        powerMetrics = node.powerMetrics ?: org.meshtastic.proto.PowerMetrics(),
        airQualityMetrics = node.airQualityMetrics ?: org.meshtastic.proto.AirQualityMetrics(),
        paxcounter = node.paxcounter,
        publicKey = node.publicKey ?: node.user.public_key,
        notes = node.notes,
        powerChannelLabels = node.powerChannelLabels,
        nodeStatus = node.nodeStatus,
        lastTransport = node.lastTransport,
        metadata = metadata?.proto,
        manuallyVerified = node.manuallyVerified,
        signsPackets = node.signsPackets,
    )

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
            isMuted = isMuted,
            environmentTelemetry = environmentTelemetry,
            powerTelemetry = powerTelemetry,
            airQualityTelemetry = airQualityTelemetry,
            paxcounter = paxcounter,
            publicKey = publicKey ?: user.public_key,
            notes = notes,
            powerChannelLabels = powerChannelLabels,
            manuallyVerified = manuallyVerified,
            nodeStatus = nodeStatus,
            lastTransport = lastTransport,
            signsPackets = signsPackets,
        )
    }
}

@Entity(tableName = "metadata", indices = [Index(value = ["num"])])
data class MetadataEntity(
    @PrimaryKey val num: Int,
    @ColumnInfo(name = "proto", typeAffinity = ColumnInfo.BLOB) val proto: DeviceMetadata,
    val timestamp: Long = nowMillis,
)

@Suppress("MagicNumber")
@Entity(
    tableName = "nodes",
    indices =
    [
        Index(value = ["last_heard"]),
        Index(value = ["short_name"]),
        Index(value = ["long_name"]),
        Index(value = ["hops_away"]),
        Index(value = ["is_favorite"]),
        Index(value = ["last_heard", "is_favorite"]),
        Index(value = ["public_key"]),
    ],
)
data class NodeEntity(
    @PrimaryKey(autoGenerate = false) val num: Int, // This is immutable, and used as a key
    @ColumnInfo(typeAffinity = ColumnInfo.BLOB) var user: User = User(),
    @ColumnInfo(name = "long_name") var longName: String? = null,
    @ColumnInfo(name = "short_name") var shortName: String? = null, // used in includeUnknown filter
    @ColumnInfo(typeAffinity = ColumnInfo.BLOB) var position: WirePosition = WirePosition(),
    var latitude: Double = 0.0,
    var longitude: Double = 0.0,
    var snr: Float = Float.MAX_VALUE,
    var rssi: Int = Int.MAX_VALUE,
    @ColumnInfo(name = "last_heard") var lastHeard: Int = 0, // the last time we've seen this node in secs since 1970
    @ColumnInfo(name = "device_metrics", typeAffinity = ColumnInfo.BLOB) var deviceTelemetry: Telemetry = Telemetry(),
    var channel: Int = 0,
    @ColumnInfo(name = "via_mqtt") var viaMqtt: Boolean = false,
    @ColumnInfo(name = "hops_away") var hopsAway: Int = -1,
    @ColumnInfo(name = "is_favorite") var isFavorite: Boolean = false,
    @ColumnInfo(name = "is_ignored", defaultValue = "0") var isIgnored: Boolean = false,
    @ColumnInfo(name = "is_muted", defaultValue = "0") var isMuted: Boolean = false,
    @ColumnInfo(name = "environment_metrics", typeAffinity = ColumnInfo.BLOB)
    var environmentTelemetry: Telemetry = Telemetry(),
    @ColumnInfo(name = "power_metrics", typeAffinity = ColumnInfo.BLOB) var powerTelemetry: Telemetry = Telemetry(),
    @ColumnInfo(name = "air_quality_metrics", typeAffinity = ColumnInfo.BLOB, defaultValue = "x''")
    var airQualityTelemetry: Telemetry = Telemetry(),
    @ColumnInfo(typeAffinity = ColumnInfo.BLOB) var paxcounter: Paxcount = Paxcount(),
    @ColumnInfo(name = "public_key") var publicKey: ByteString? = null,
    @ColumnInfo(name = "notes", defaultValue = "") var notes: String = "",
    @ColumnInfo(name = "power_channel_labels", defaultValue = "[]") var powerChannelLabels: List<String> = emptyList(),
    @ColumnInfo(name = "manually_verified", defaultValue = "0")
    var manuallyVerified: Boolean = false, // ONLY set true when scanned/imported manually
    @ColumnInfo(name = "node_status") var nodeStatus: String? = null,
    /** The transport mechanism this node was last heard over (see [MeshPacket.TransportMechanism]). */
    @ColumnInfo(name = "last_transport", defaultValue = "0") var lastTransport: Int = 0,
    /** True when this node signs its broadcasts via XEdDSA (NodeInfo.has_xeddsa_signed). */
    @ColumnInfo(name = "has_xeddsa_signed", defaultValue = "0") var signsPackets: Boolean = false,
) {
    val deviceMetrics: org.meshtastic.proto.DeviceMetrics?
        get() = deviceTelemetry.device_metrics

    val environmentMetrics: org.meshtastic.proto.EnvironmentMetrics?
        get() = environmentTelemetry.environment_metrics

    val powerMetrics: org.meshtastic.proto.PowerMetrics?
        get() = powerTelemetry.power_metrics

    val airQualityMetrics: org.meshtastic.proto.AirQualityMetrics?
        get() = airQualityTelemetry.air_quality_metrics

    val isUnknownUser
        get() = user.hw_model == HardwareModel.UNSET

    val hasPKC
        get() = (publicKey ?: user.public_key).size > 0

    fun setPosition(p: WirePosition, defaultTime: Int = currentTime()) {
        position = p.copy(time = if (p.time != 0) p.time else defaultTime)
        latitude = degD(p.latitude_i ?: 0)
        longitude = degD(p.longitude_i ?: 0)
    }

    /** true if the device was heard from recently */
    val isOnline: Boolean
        get() {
            return lastHeard > onlineTimeThreshold()
        }

    companion object {
        // Convert to a double representation of degrees
        fun degD(i: Int) = i * 1e-7

        fun degI(d: Double) = (d * 1e7).toInt()

        val ERROR_BYTE_STRING: ByteString = ByteArray(32) { 0 }.toByteString()

        fun currentTime() = nowSeconds.toInt()
    }

    fun toModel() = Node(
        num = num,
        user = user,
        position = position,
        snr = snr,
        rssi = rssi,
        lastHeard = lastHeard,
        deviceMetrics = deviceMetrics ?: org.meshtastic.proto.DeviceMetrics(),
        channel = channel,
        viaMqtt = viaMqtt,
        hopsAway = hopsAway,
        isFavorite = isFavorite,
        isIgnored = isIgnored,
        isMuted = isMuted,
        environmentMetrics = environmentMetrics ?: org.meshtastic.proto.EnvironmentMetrics(),
        powerMetrics = powerMetrics ?: org.meshtastic.proto.PowerMetrics(),
        airQualityMetrics = airQualityMetrics ?: org.meshtastic.proto.AirQualityMetrics(),
        paxcounter = paxcounter,
        publicKey = publicKey ?: user.public_key,
        notes = notes,
        powerChannelLabels = powerChannelLabels,
        nodeStatus = nodeStatus,
        lastTransport = lastTransport,
        signsPackets = signsPackets,
    )
}
