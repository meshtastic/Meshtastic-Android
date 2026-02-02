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
package org.meshtastic.core.model

import android.graphics.Color
import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import org.meshtastic.core.model.util.anonymize
import org.meshtastic.core.model.util.bearing
import org.meshtastic.core.model.util.latLongToMeter
import org.meshtastic.core.model.util.onlineTimeThreshold
import org.meshtastic.proto.Config
import org.meshtastic.proto.HardwareModel

//
// model objects that directly map to the corresponding protobufs
//

@Parcelize
data class MeshUser(
    val id: String,
    val longName: String,
    val shortName: String,
    val hwModel: HardwareModel,
    val isLicensed: Boolean = false,
    val role: Int = 0,
) : Parcelable {

    override fun toString(): String = "MeshUser(id=${id.anonymize}, " +
        "longName=${longName.anonymize}, " +
        "shortName=${shortName.anonymize}, " +
        "hwModel=$hwModelString, " +
        "isLicensed=$isLicensed, " +
        "role=$role)"

    /** Create our model object from a protobuf. */
    constructor(
        p: org.meshtastic.proto.User,
    ) : this(p.id, p.long_name ?: "", p.short_name ?: "", p.hw_model, p.is_licensed, p.role.value)

    /**
     * a string version of the hardware model, converted into pretty lowercase and changing _ to -, and p to dot or null
     * if unset
     */
    val hwModelString: String?
        get() =
            if (hwModel == HardwareModel.UNSET) {
                null
            } else {
                hwModel.name.replace('_', '-').replace('p', '.').lowercase()
            }
}

@Parcelize
data class Position(
    val latitude: Double,
    val longitude: Double,
    val altitude: Int,
    val time: Int = currentTime(), // default to current time in secs (NOT MILLISECONDS!)
    val satellitesInView: Int = 0,
    val groundSpeed: Int = 0,
    val groundTrack: Int = 0, // "heading"
    val precisionBits: Int = 0,
) : Parcelable {

    @Suppress("MagicNumber")
    companion object {
        // / Convert to a double representation of degrees
        fun degD(i: Int) = i * 1e-7

        fun degI(d: Double) = (d * 1e7).toInt()

        fun currentTime() = (System.currentTimeMillis() / 1000).toInt()
    }

    /**
     * Create our model object from a protobuf. If time is unspecified in the protobuf, the provided default time will
     * be used.
     */
    constructor(
        position: org.meshtastic.proto.Position,
        defaultTime: Int = currentTime(),
    ) : this(
        // We prefer the int version of lat/lon but if not available use the depreciated legacy version
        degD(position.latitude_i ?: 0),
        degD(position.longitude_i ?: 0),
        position.altitude ?: 0,
        if (position.time != 0) position.time else defaultTime,
        position.sats_in_view ?: 0,
        position.ground_speed ?: 0,
        position.ground_track ?: 0,
        position.precision_bits ?: 0,
    )

    // / @return distance in meters to some other node (or null if unknown)
    fun distance(o: Position) = latLongToMeter(latitude, longitude, o.latitude, o.longitude)

    // / @return bearing to the other position in degrees
    fun bearing(o: Position) = bearing(latitude, longitude, o.latitude, o.longitude)

    // If GPS gives a crap position don't crash our app
    @Suppress("MagicNumber")
    fun isValid(): Boolean = latitude != 0.0 &&
        longitude != 0.0 &&
        (latitude >= -90 && latitude <= 90.0) &&
        (longitude >= -180 && longitude <= 180)

    override fun toString(): String =
        "Position(lat=${latitude.anonymize}, lon=${longitude.anonymize}, alt=${altitude.anonymize}, time=$time)"
}

@Parcelize
data class DeviceMetrics(
    val time: Int = currentTime(), // default to current time in secs (NOT MILLISECONDS!)
    val batteryLevel: Int = 0,
    val voltage: Float,
    val channelUtilization: Float,
    val airUtilTx: Float,
    val uptimeSeconds: Int,
) : Parcelable {
    companion object {
        @Suppress("MagicNumber")
        fun currentTime() = (System.currentTimeMillis() / 1000).toInt()
    }

    /** Create our model object from a protobuf. */
    constructor(
        p: org.meshtastic.proto.DeviceMetrics,
        telemetryTime: Int = currentTime(),
    ) : this(
        telemetryTime,
        p.battery_level ?: 0,
        p.voltage ?: 0f,
        p.channel_utilization ?: 0f,
        p.air_util_tx ?: 0f,
        p.uptime_seconds ?: 0,
    )
}

@Parcelize
data class EnvironmentMetrics(
    val time: Int = currentTime(), // default to current time in secs (NOT MILLISECONDS!)
    val temperature: Float?,
    val relativeHumidity: Float?,
    val soilTemperature: Float?,
    val soilMoisture: Int?,
    val barometricPressure: Float?,
    val gasResistance: Float?,
    val voltage: Float?,
    val current: Float?,
    val iaq: Int?,
    val lux: Float? = null,
    val uvLux: Float? = null,
) : Parcelable {
    @Suppress("MagicNumber")
    companion object {
        fun currentTime() = (System.currentTimeMillis() / 1000).toInt()

        fun fromTelemetryProto(proto: org.meshtastic.proto.EnvironmentMetrics, time: Int): EnvironmentMetrics =
            EnvironmentMetrics(
                temperature = proto.temperature?.takeIf { !it.isNaN() },
                relativeHumidity = proto.relative_humidity?.takeIf { !it.isNaN() && it != 0.0f },
                soilTemperature = proto.soil_temperature?.takeIf { !it.isNaN() },
                soilMoisture = proto.soil_moisture?.takeIf { it != Int.MIN_VALUE },
                barometricPressure = proto.barometric_pressure?.takeIf { !it.isNaN() },
                gasResistance = proto.gas_resistance?.takeIf { !it.isNaN() },
                voltage = proto.voltage?.takeIf { !it.isNaN() },
                current = proto.current?.takeIf { !it.isNaN() },
                iaq = proto.iaq?.takeIf { it != Int.MIN_VALUE },
                lux = proto.lux?.takeIf { !it.isNaN() },
                uvLux = proto.uv_lux?.takeIf { !it.isNaN() },
                time = time,
            )
    }
}

@Parcelize
data class NodeInfo(
    val num: Int, // This is immutable, and used as a key
    var user: MeshUser? = null,
    var position: Position? = null,
    var snr: Float = Float.MAX_VALUE,
    var rssi: Int = Int.MAX_VALUE,
    var lastHeard: Int = 0, // the last time we've seen this node in secs since 1970
    var deviceMetrics: DeviceMetrics? = null,
    var channel: Int = 0,
    var environmentMetrics: EnvironmentMetrics? = null,
    var hopsAway: Int = 0,
) : Parcelable {

    @Suppress("MagicNumber")
    val colors: Pair<Int, Int>
        get() { // returns foreground and background @ColorInt for each 'num'
            val r = (num and 0xFF0000) shr 16
            val g = (num and 0x00FF00) shr 8
            val b = num and 0x0000FF
            val brightness = ((r * 0.299) + (g * 0.587) + (b * 0.114)) / 255
            return (if (brightness > 0.5) Color.BLACK else Color.WHITE) to Color.rgb(r, g, b)
        }

    val batteryLevel
        get() = deviceMetrics?.batteryLevel

    val voltage
        get() = deviceMetrics?.voltage

    @Suppress("ImplicitDefaultLocale")
    val batteryStr
        get() = if (batteryLevel in 1..100) String.format("%d%%", batteryLevel) else ""

    /** true if the device was heard from recently */
    val isOnline: Boolean
        get() {
            return lastHeard > onlineTimeThreshold()
        }

    // / return the position if it is valid, else null
    val validPosition: Position?
        get() {
            return position?.takeIf { it.isValid() }
        }

    // / @return distance in meters to some other node (or null if unknown)
    fun distance(o: NodeInfo?): Int? {
        val p = validPosition
        val op = o?.validPosition
        return if (p != null && op != null) p.distance(op).toInt() else null
    }

    // / @return bearing to the other position in degrees
    fun bearing(o: NodeInfo?): Int? {
        val p = validPosition
        val op = o?.validPosition
        return if (p != null && op != null) p.bearing(op).toInt() else null
    }

    // / @return a nice human readable string for the distance, or null for unknown
    @Suppress("MagicNumber")
    fun distanceStr(o: NodeInfo?, prefUnits: Int = 0) = distance(o)?.let { dist ->
        when {
            dist == 0 -> null // same point
            prefUnits == Config.DisplayConfig.DisplayUnits.METRIC.value && dist < 1000 ->
                "%.0f m".format(dist.toDouble())
            prefUnits == Config.DisplayConfig.DisplayUnits.METRIC.value && dist >= 1000 ->
                "%.1f km".format(dist / 1000.0)
            prefUnits == Config.DisplayConfig.DisplayUnits.IMPERIAL.value && dist < 1609 ->
                "%.0f ft".format(dist.toDouble() * 3.281)
            prefUnits == Config.DisplayConfig.DisplayUnits.IMPERIAL.value && dist >= 1609 ->
                "%.1f mi".format(dist / 1609.34)
            else -> null
        }
    }
}
