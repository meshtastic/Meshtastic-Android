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
package org.meshtastic.core.database.model

import android.graphics.Color
import okio.ByteString
import org.meshtastic.core.database.entity.NodeEntity
import org.meshtastic.core.model.Capabilities
import org.meshtastic.core.model.util.GPSFormat
import org.meshtastic.core.model.util.UnitConversions.celsiusToFahrenheit
import org.meshtastic.core.model.util.latLongToMeter
import org.meshtastic.core.model.util.toDistanceString
import org.meshtastic.proto.Config
import org.meshtastic.proto.DeviceMetadata
import org.meshtastic.proto.DeviceMetrics
import org.meshtastic.proto.EnvironmentMetrics
import org.meshtastic.proto.HardwareModel
import org.meshtastic.proto.Paxcount
import org.meshtastic.proto.Position
import org.meshtastic.proto.PowerMetrics
import org.meshtastic.proto.User

@Suppress("MagicNumber")
data class Node(
    val num: Int,
    val metadata: DeviceMetadata? = null,
    val user: User = User(),
    val position: Position = Position(),
    val snr: Float = Float.MAX_VALUE,
    val rssi: Int = Int.MAX_VALUE,
    val lastHeard: Int = 0, // the last time we've seen this node in secs since 1970
    val deviceMetrics: DeviceMetrics = DeviceMetrics(),
    val channel: Int = 0,
    val viaMqtt: Boolean = false,
    val hopsAway: Int = -1,
    val isFavorite: Boolean = false,
    val isIgnored: Boolean = false,
    val isMuted: Boolean = false,
    val environmentMetrics: EnvironmentMetrics = EnvironmentMetrics(),
    val powerMetrics: PowerMetrics = PowerMetrics(),
    val paxcounter: Paxcount = Paxcount(),
    val publicKey: ByteString? = null,
    val notes: String = "",
    val manuallyVerified: Boolean = false,
) {
    val capabilities: Capabilities by lazy { Capabilities(metadata?.firmware_version) }

    val colors: Pair<Int, Int>
        get() { // returns foreground and background @ColorInt for each 'num'
            val r = (num and 0xFF0000) shr 16
            val g = (num and 0x00FF00) shr 8
            val b = num and 0x0000FF
            val brightness = ((r * 0.299) + (g * 0.587) + (b * 0.114)) / 255
            return (if (brightness > 0.5) Color.BLACK else Color.WHITE) to Color.rgb(r, g, b)
        }

    val isUnknownUser
        get() = user.hw_model == HardwareModel.UNSET

    val hasPKC
        get() = (publicKey ?: user.public_key).size > 0

    val mismatchKey
        get() = (publicKey ?: user.public_key) == NodeEntity.ERROR_BYTE_STRING

    val hasEnvironmentMetrics: Boolean
        get() = environmentMetrics != EnvironmentMetrics()

    val hasPowerMetrics: Boolean
        get() = powerMetrics != PowerMetrics()

    val batteryLevel
        get() = deviceMetrics.battery_level

    val voltage
        get() = deviceMetrics.voltage

    val batteryStr
        get() = if (batteryLevel in 1..100) "$batteryLevel%" else ""

    val latitude
        get() = (position.latitude_i ?: 0) * 1e-7

    val longitude
        get() = (position.longitude_i ?: 0) * 1e-7

    private fun hasValidPosition(): Boolean = latitude != 0.0 &&
        longitude != 0.0 &&
        (latitude >= -90 && latitude <= 90.0) &&
        (longitude >= -180 && longitude <= 180)

    val validPosition: Position?
        get() = position.takeIf { hasValidPosition() }

    // @return distance in meters to some other node (or null if unknown)
    fun distance(o: Node): Int? = when {
        validPosition == null || o.validPosition == null -> null
        else -> latLongToMeter(latitude, longitude, o.latitude, o.longitude).toInt()
    }

    // @return formatted distance string to another node, using the given display units
    fun distanceStr(o: Node, displayUnits: Config.DisplayConfig.DisplayUnits): String? =
        distance(o)?.toDistanceString(displayUnits)

    // @return bearing to the other position in degrees
    fun bearing(o: Node?): Int? = when {
        validPosition == null || o?.validPosition == null -> null
        else -> org.meshtastic.core.model.util.bearing(latitude, longitude, o.latitude, o.longitude).toInt()
    }

    fun gpsString(): String = GPSFormat.toDec(latitude, longitude)

    private fun EnvironmentMetrics.getDisplayStrings(isFahrenheit: Boolean): List<String> {
        val tempVal = temperature ?: 0f
        val temp =
            if (tempVal != 0f) {
                if (isFahrenheit) {
                    "%.1f°F".format(celsiusToFahrenheit(tempVal))
                } else {
                    "%.1f°C".format(tempVal)
                }
            } else {
                null
            }
        val humidity = if ((relative_humidity ?: 0f) != 0f) "%.0f%%".format(relative_humidity) else null
        val soilTemp = soil_temperature ?: 0f
        val soilTemperatureStr =
            if (soilTemp != 0f) {
                if (isFahrenheit) {
                    "%.1f°F".format(celsiusToFahrenheit(soilTemp))
                } else {
                    "%.1f°C".format(soilTemp)
                }
            } else {
                null
            }
        val soilMoistureRange = 0..100
        val soilMoistureValue = soil_moisture ?: 0
        val soilMoisture =
            if (soilMoistureValue in soilMoistureRange && soil_temperature != 0f) {
                "%d%%".format(soilMoistureValue)
            } else {
                null
            }
        val voltageValue = this.voltage ?: 0f
        val voltage = if (voltageValue != 0f) "%.2fV".format(voltageValue) else null
        val current = if (this.current != 0f) "%.1fmA".format(this.current) else null
        val iaq = if (this.iaq != 0) "IAQ: ${this.iaq}" else null

        return listOfNotNull(
            paxcounter.getDisplayString(),
            temp,
            humidity,
            soilTemperatureStr,
            soilMoisture,
            voltage,
            current,
            iaq,
        )
    }

    private fun Paxcount.getDisplayString() = "PAX: ${ble + wifi} (B:$ble/W:$wifi)".takeIf { ble != 0 || wifi != 0 }

    fun getTelemetryStrings(isFahrenheit: Boolean = false): List<String> =
        environmentMetrics.getDisplayStrings(isFahrenheit)
}

fun Config.DeviceConfig.Role?.isUnmessageableRole(): Boolean = this in
    listOf(
        Config.DeviceConfig.Role.REPEATER,
        Config.DeviceConfig.Role.ROUTER,
        Config.DeviceConfig.Role.ROUTER_LATE,
        Config.DeviceConfig.Role.SENSOR,
        Config.DeviceConfig.Role.TRACKER,
        Config.DeviceConfig.Role.TAK_TRACKER,
    )
