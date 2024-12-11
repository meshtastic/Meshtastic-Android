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

package com.geeksville.mesh.model

import android.graphics.Color
import com.geeksville.mesh.ConfigProtos.Config.DisplayConfig
import com.geeksville.mesh.MeshProtos
import com.geeksville.mesh.PaxcountProtos
import com.geeksville.mesh.TelemetryProtos.DeviceMetrics
import com.geeksville.mesh.TelemetryProtos.EnvironmentMetrics
import com.geeksville.mesh.TelemetryProtos.PowerMetrics
import com.geeksville.mesh.util.GPSFormat
import com.geeksville.mesh.util.latLongToMeter
import com.geeksville.mesh.util.toDistanceString
import com.google.protobuf.ByteString

@Suppress("MagicNumber")
data class Node(
    val num: Int,
    val metadata: MeshProtos.DeviceMetadata? = null,
    val user: MeshProtos.User = MeshProtos.User.getDefaultInstance(),
    val position: MeshProtos.Position = MeshProtos.Position.getDefaultInstance(),
    val snr: Float = Float.MAX_VALUE,
    val rssi: Int = Int.MAX_VALUE,
    val lastHeard: Int = 0, // the last time we've seen this node in secs since 1970
    val deviceMetrics: DeviceMetrics = DeviceMetrics.getDefaultInstance(),
    val channel: Int = 0,
    val viaMqtt: Boolean = false,
    val hopsAway: Int = -1,
    val isFavorite: Boolean = false,
    val isIgnored: Boolean = false,
    val environmentMetrics: EnvironmentMetrics = EnvironmentMetrics.getDefaultInstance(),
    val powerMetrics: PowerMetrics = PowerMetrics.getDefaultInstance(),
    val paxcounter: PaxcountProtos.Paxcount = PaxcountProtos.Paxcount.getDefaultInstance(),
) {
    val colors: Pair<Int, Int>
        get() { // returns foreground and background @ColorInt for each 'num'
            val r = (num and 0xFF0000) shr 16
            val g = (num and 0x00FF00) shr 8
            val b = num and 0x0000FF
            val brightness = ((r * 0.299) + (g * 0.587) + (b * 0.114)) / 255
            return (if (brightness > 0.5) Color.BLACK else Color.WHITE) to Color.rgb(r, g, b)
        }

    val isUnknownUser get() = user.hwModel == MeshProtos.HardwareModel.UNSET
    val hasPKC get() = !user.publicKey.isEmpty
    val errorByteString: ByteString get() = ByteString.copyFrom(ByteArray(32) { 0 })
    val mismatchKey get() = user.publicKey == errorByteString

    val hasEnvironmentMetrics: Boolean
        get() = environmentMetrics != EnvironmentMetrics.getDefaultInstance()

    val hasPowerMetrics: Boolean
        get() = powerMetrics != PowerMetrics.getDefaultInstance()

    val batteryLevel get() = deviceMetrics.batteryLevel
    val voltage get() = deviceMetrics.voltage
    val batteryStr get() = if (batteryLevel in 1..100) "$batteryLevel%" else ""

    val latitude get() = position.latitudeI * 1e-7
    val longitude get() = position.longitudeI * 1e-7

    private fun hasValidPosition(): Boolean {
        return latitude != 0.0 && longitude != 0.0 &&
                (latitude >= -90 && latitude <= 90.0) &&
                (longitude >= -180 && longitude <= 180)
    }

    val validPosition: MeshProtos.Position? get() = position.takeIf { hasValidPosition() }

    // @return distance in meters to some other node (or null if unknown)
    fun distance(o: Node): Int? = when {
        validPosition == null || o.validPosition == null -> null
        else -> latLongToMeter(latitude, longitude, o.latitude, o.longitude).toInt()
    }

    // @return a nice human readable string for the distance, or null for unknown
    fun distanceStr(o: Node, displayUnits: Int = 0): String? = distance(o)?.let { dist ->
        val system = DisplayConfig.DisplayUnits.forNumber(displayUnits)
        return if (dist > 0) dist.toDistanceString(system) else null
    }

    // @return bearing to the other position in degrees
    fun bearing(o: Node?): Int? = when {
        validPosition == null || o?.validPosition == null -> null
        else -> com.geeksville.mesh.util.bearing(latitude, longitude, o.latitude, o.longitude).toInt()
    }

    fun gpsString(gpsFormat: Int): String = when (gpsFormat) {
        DisplayConfig.GpsCoordinateFormat.DEC_VALUE -> GPSFormat.toDEC(latitude, longitude)
        DisplayConfig.GpsCoordinateFormat.DMS_VALUE -> GPSFormat.toDMS(latitude, longitude)
        DisplayConfig.GpsCoordinateFormat.UTM_VALUE -> GPSFormat.toUTM(latitude, longitude)
        DisplayConfig.GpsCoordinateFormat.MGRS_VALUE -> GPSFormat.toMGRS(latitude, longitude)
        else -> GPSFormat.toDEC(latitude, longitude)
    }

    private fun EnvironmentMetrics.getDisplayString(isFahrenheit: Boolean): String {
        val temp = if (temperature != 0f) {
            if (isFahrenheit) {
                val fahrenheit = temperature * 1.8F + 32
                "%.1f°F".format(fahrenheit)
            } else {
                "%.1f°C".format(temperature)
            }
        } else {
            null
        }
        val humidity = if (relativeHumidity != 0f) "%.0f%%".format(relativeHumidity) else null
        val voltage = if (this.voltage != 0f) "%.2fV".format(this.voltage) else null
        val current = if (current != 0f) "%.1fmA".format(current) else null
        val iaq = if (iaq != 0) "IAQ: $iaq" else null

        return listOfNotNull(
            temp,
            humidity,
            voltage,
            current,
            iaq,
        ).joinToString(" ")
    }

    private fun PaxcountProtos.Paxcount.getDisplayString() =
        "PAX: ${ble + wifi} (B:$ble/W:$wifi)".takeIf { ble != 0 && wifi != 0 }

    fun getTelemetryString(isFahrenheit: Boolean = false): String {
        return listOfNotNull(
            paxcounter.getDisplayString(),
            environmentMetrics.getDisplayString(isFahrenheit),
        ).joinToString(" ")
    }
}
