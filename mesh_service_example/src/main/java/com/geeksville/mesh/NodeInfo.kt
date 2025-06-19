/*
 * Copyright (c) 2025 Meshtastic LLC
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

package com.geeksville.mesh

import android.graphics.Color
import android.os.Parcelable
import com.geeksville.mesh.util.GPSFormat
import com.geeksville.mesh.util.bearing
import com.geeksville.mesh.util.latLongToMeter
import com.geeksville.mesh.util.anonymize
import kotlinx.parcelize.Parcelize

//
// model objects that directly map to the corresponding protobufs
//

@Parcelize
data class MeshUser(
    val id: String,
    val longName: String,
    val shortName: String,
    val hwModel: MeshProtos.HardwareModel,
    val isLicensed: Boolean = false,
    val role: Int = 0,
) : Parcelable {

    override fun toString(): String {
        return "MeshUser(id=${id.anonymize}, " +
                "longName=${longName.anonymize}, " +
                "shortName=${shortName.anonymize}, " +
                "hwModel=$hwModelString, " +
                "isLicensed=$isLicensed, " +
                "role=$role)"
    }

    /** Create our model object from a protobuf.
     */
    constructor(p: MeshProtos.User) : this(
        p.id,
        p.longName,
        p.shortName,
        p.hwModel,
        p.isLicensed,
        p.roleValue
    )

    /** a string version of the hardware model, converted into pretty lowercase and changing _ to -, and p to dot
     * or null if unset
     * */
    val hwModelString: String?
        get() =
            if (hwModel == MeshProtos.HardwareModel.UNSET) null
            else hwModel.name.replace('_', '-').replace('p', '.').lowercase()
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

    companion object {
        /// Convert to a double representation of degrees
        fun degD(i: Int) = i * 1e-7
        fun degI(d: Double) = (d * 1e7).toInt()

        fun currentTime() = (System.currentTimeMillis() / 1000).toInt()
    }

    /** Create our model object from a protobuf.  If time is unspecified in the protobuf, the provided default time will be used.
     */
    constructor(position: MeshProtos.Position, defaultTime: Int = currentTime()) : this(
        // We prefer the int version of lat/lon but if not available use the depreciated legacy version
        degD(position.latitudeI),
        degD(position.longitudeI),
        position.altitude,
        if (position.time != 0) position.time else defaultTime,
        position.satsInView,
        position.groundSpeed,
        position.groundTrack,
        position.precisionBits
    )

    /// @return distance in meters to some other node (or null if unknown)
    fun distance(o: Position) = latLongToMeter(latitude, longitude, o.latitude, o.longitude)

    /// @return bearing to the other position in degrees
    fun bearing(o: Position) = bearing(latitude, longitude, o.latitude, o.longitude)

    // If GPS gives a crap position don't crash our app
    fun isValid(): Boolean {
        return latitude != 0.0 && longitude != 0.0 &&
                (latitude >= -90 && latitude <= 90.0) &&
                (longitude >= -180 && longitude <= 180)
    }

    fun gpsString(gpsFormat: Int): String = when (gpsFormat) {
        ConfigProtos.Config.DisplayConfig.GpsCoordinateFormat.DEC_VALUE -> GPSFormat.DEC(this)
        ConfigProtos.Config.DisplayConfig.GpsCoordinateFormat.DMS_VALUE -> GPSFormat.DMS(this)
        ConfigProtos.Config.DisplayConfig.GpsCoordinateFormat.UTM_VALUE -> GPSFormat.UTM(this)
        ConfigProtos.Config.DisplayConfig.GpsCoordinateFormat.MGRS_VALUE -> GPSFormat.MGRS(this)
        else -> GPSFormat.DEC(this)
    }

    override fun toString(): String {
        return "Position(lat=${latitude.anonymize}, lon=${longitude.anonymize}, alt=${altitude.anonymize}, time=${time})"
    }
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
        fun currentTime() = (System.currentTimeMillis() / 1000).toInt()
    }

    /** Create our model object from a protobuf.
     */
    constructor(p: TelemetryProtos.DeviceMetrics, telemetryTime: Int = currentTime()) : this(
        telemetryTime,
        p.batteryLevel,
        p.voltage,
        p.channelUtilization,
        p.airUtilTx,
        p.uptimeSeconds,
    )
}

@Parcelize
data class EnvironmentMetrics(
    val time: Int = currentTime(), // default to current time in secs (NOT MILLISECONDS!)
    val temperature: Float,
    val relativeHumidity: Float,
    val barometricPressure: Float,
    val gasResistance: Float,
    val voltage: Float,
    val current: Float,
    val iaq: Int,
) : Parcelable {
    companion object {
        fun currentTime() = (System.currentTimeMillis() / 1000).toInt()
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
    var hopsAway: Int = 0
) : Parcelable {

    val colors: Pair<Int, Int>
        get() { // returns foreground and background @ColorInt for each 'num'
            val r = (num and 0xFF0000) shr 16
            val g = (num and 0x00FF00) shr 8
            val b = num and 0x0000FF
            val brightness = ((r * 0.299) + (g * 0.587) + (b * 0.114)) / 255
            return (if (brightness > 0.5) Color.BLACK else Color.WHITE) to Color.rgb(r, g, b)
        }

    val batteryLevel get() = deviceMetrics?.batteryLevel
    val voltage get() = deviceMetrics?.voltage
    val batteryStr get() = if (batteryLevel in 1..100) String.format("%d%%", batteryLevel) else ""

    /**
     * true if the device was heard from recently
     */
    val isOnline: Boolean
        get() {
            val now = System.currentTimeMillis() / 1000
            val timeout = 15 * 60
            return (now - lastHeard <= timeout)
        }

    /// return the position if it is valid, else null
    val validPosition: Position?
        get() {
            return position?.takeIf { it.isValid() }
        }

    /// @return distance in meters to some other node (or null if unknown)
    fun distance(o: NodeInfo?): Int? {
        val p = validPosition
        val op = o?.validPosition
        return if (p != null && op != null) p.distance(op).toInt() else null
    }

    /// @return bearing to the other position in degrees
    fun bearing(o: NodeInfo?): Int? {
        val p = validPosition
        val op = o?.validPosition
        return if (p != null && op != null) p.bearing(op).toInt() else null
    }

    /// @return a nice human readable string for the distance, or null for unknown
    fun distanceStr(o: NodeInfo?, prefUnits: Int = 0) = distance(o)?.let { dist ->
        when {
            dist == 0 -> null // same point
            prefUnits == ConfigProtos.Config.DisplayConfig.DisplayUnits.METRIC_VALUE && dist < 1000 -> "%.0f m".format(dist.toDouble())
            prefUnits == ConfigProtos.Config.DisplayConfig.DisplayUnits.METRIC_VALUE && dist >= 1000 -> "%.1f km".format(dist / 1000.0)
            prefUnits == ConfigProtos.Config.DisplayConfig.DisplayUnits.IMPERIAL_VALUE && dist < 1609 -> "%.0f ft".format(dist.toDouble()*3.281)
            prefUnits == ConfigProtos.Config.DisplayConfig.DisplayUnits.IMPERIAL_VALUE && dist >= 1609 -> "%.1f mi".format(dist / 1609.34)
            else -> null
        }
    }
}