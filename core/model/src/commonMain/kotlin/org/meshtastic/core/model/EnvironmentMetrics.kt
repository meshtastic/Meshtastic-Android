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

import org.meshtastic.core.common.util.nowSeconds

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
) {
    @Suppress("MagicNumber")
    companion object {
        fun currentTime() = nowSeconds.toInt()

        fun fromTelemetryProto(proto: org.meshtastic.proto.EnvironmentMetrics, time: Int): EnvironmentMetrics =
            EnvironmentMetrics(
                temperature = proto.temperature?.takeIf { !it.isNaN() },
                // 0%RH is treated as "no reading" — firmware emits 0 when the humidity sensor isn't fitted and a real
                // outdoor reading of exactly 0%RH is physically implausible. Other fields don't get this guard because
                // their natural zero values (0 V, 0 A, 0°C) are meaningful sensor data.
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
