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
package org.meshtastic.core.domain.usecase.settings

import kotlinx.serialization.Serializable

/**
 * Root document written by [ExportNodeDatabaseUseCase]. Absent fields are omitted from the JSON rather than emitted as
 * null/0 sentinels, so consumers can distinguish "no reading" from a genuine zero.
 */
@Serializable
data class NodeDatabaseExport(
    val schemaVersion: Int,
    /** ISO-8601 UTC timestamp of when the export was produced. */
    val exportedAt: String,
    /** Unsigned node number of the local device, when known. */
    val myNodeNum: Long? = null,
    val nodes: List<NodeExport>,
)

@Serializable
data class NodeExport(
    /** Unsigned 32-bit node number. */
    val num: Long,
    /** Hex user id, e.g. "!a1b2c3d4". */
    val id: String? = null,
    val longName: String? = null,
    val shortName: String? = null,
    val hwModel: String? = null,
    val role: String? = null,
    val isLicensed: Boolean = false,
    val isUnmessagable: Boolean? = null,
    /** Base64-encoded Curve25519 public key. */
    val publicKey: String? = null,
    val signsPackets: Boolean = false,
    val manuallyVerified: Boolean = false,
    /** Epoch seconds of the last packet heard from this node. */
    val lastHeard: Long? = null,
    val snr: Float? = null,
    val rssi: Int? = null,
    val channel: Int = 0,
    val viaMqtt: Boolean = false,
    val hopsAway: Int? = null,
    val lastTransport: String? = null,
    val isFavorite: Boolean = false,
    val isIgnored: Boolean = false,
    val isMuted: Boolean = false,
    val nodeStatus: String? = null,
    val notes: String? = null,
    val position: PositionExport? = null,
    val deviceMetrics: DeviceMetricsExport? = null,
    val environmentMetrics: EnvironmentMetricsExport? = null,
    val powerMetrics: PowerMetricsExport? = null,
    val powerChannelLabels: List<String>? = null,
    val airQualityMetrics: AirQualityMetricsExport? = null,
    val paxcounter: PaxcountExport? = null,
    val metadata: DeviceMetadataExport? = null,
)

@Serializable
data class PositionExport(
    val latitude: Double? = null,
    val longitude: Double? = null,
    val altitude: Int? = null,
    /** Epoch seconds of the position fix. */
    val time: Long? = null,
    val locationSource: String? = null,
    val groundSpeed: Int? = null,
    val groundTrack: Int? = null,
    val satsInView: Int? = null,
    val precisionBits: Int? = null,
)

@Serializable
data class DeviceMetricsExport(
    val batteryLevel: Int? = null,
    val voltage: Float? = null,
    val channelUtilization: Float? = null,
    val airUtilTx: Float? = null,
    val uptimeSeconds: Int? = null,
)

@Serializable
data class EnvironmentMetricsExport(
    val temperature: Float? = null,
    val relativeHumidity: Float? = null,
    val barometricPressure: Float? = null,
    val gasResistance: Float? = null,
    val voltage: Float? = null,
    val current: Float? = null,
    val iaq: Int? = null,
    val distance: Float? = null,
    val lux: Float? = null,
    val whiteLux: Float? = null,
    val irLux: Float? = null,
    val uvLux: Float? = null,
    val windDirection: Int? = null,
    val windSpeed: Float? = null,
    val weight: Float? = null,
    val windGust: Float? = null,
    val windLull: Float? = null,
    val radiation: Float? = null,
    val rainfall1h: Float? = null,
    val rainfall24h: Float? = null,
    val soilMoisture: Int? = null,
    val soilTemperature: Float? = null,
    /**
     * Legacy repeated 1-Wire readings. Firmware 2.8 moved these to [oneWireTemperatureCh0]..[oneWireTemperatureCh7];
     * retained so exports of telemetry stored before then keep their original shape.
     */
    val oneWireTemperature: List<Float>? = null,
    val oneWireTemperatureCh0: Float? = null,
    val oneWireTemperatureCh1: Float? = null,
    val oneWireTemperatureCh2: Float? = null,
    val oneWireTemperatureCh3: Float? = null,
    val oneWireTemperatureCh4: Float? = null,
    val oneWireTemperatureCh5: Float? = null,
    val oneWireTemperatureCh6: Float? = null,
    val oneWireTemperatureCh7: Float? = null,
    val adcVoltageCh0: Float? = null,
    val adcVoltageCh1: Float? = null,
    val adcVoltageCh2: Float? = null,
    val adcVoltageCh3: Float? = null,
    val adcVoltageCh4: Float? = null,
    val adcVoltageCh5: Float? = null,
    val adcVoltageCh6: Float? = null,
    val adcVoltageCh7: Float? = null,
)

@Serializable
data class PowerMetricsExport(
    val ch1Voltage: Float? = null,
    val ch1Current: Float? = null,
    val ch2Voltage: Float? = null,
    val ch2Current: Float? = null,
    val ch3Voltage: Float? = null,
    val ch3Current: Float? = null,
    val ch4Voltage: Float? = null,
    val ch4Current: Float? = null,
    val ch5Voltage: Float? = null,
    val ch5Current: Float? = null,
    val ch6Voltage: Float? = null,
    val ch6Current: Float? = null,
    val ch7Voltage: Float? = null,
    val ch7Current: Float? = null,
    val ch8Voltage: Float? = null,
    val ch8Current: Float? = null,
)

@Serializable
data class AirQualityMetricsExport(
    val pm10Standard: Int? = null,
    val pm25Standard: Int? = null,
    val pm100Standard: Int? = null,
    val pm10Environmental: Int? = null,
    val pm25Environmental: Int? = null,
    val pm100Environmental: Int? = null,
    val particles03um: Int? = null,
    val particles05um: Int? = null,
    val particles10um: Int? = null,
    val particles25um: Int? = null,
    val particles50um: Int? = null,
    val particles100um: Int? = null,
    val co2: Int? = null,
    val co2Temperature: Float? = null,
    val co2Humidity: Float? = null,
    val formFormaldehyde: Float? = null,
    val formHumidity: Float? = null,
    val formTemperature: Float? = null,
    val pm40Standard: Int? = null,
    val particles40um: Int? = null,
    val pmTemperature: Float? = null,
    val pmHumidity: Float? = null,
    val pmVocIdx: Float? = null,
    val pmNoxIdx: Float? = null,
    val particlesTps: Float? = null,
)

@Serializable data class PaxcountExport(val wifi: Int = 0, val ble: Int = 0, val uptime: Int = 0)

@Serializable
data class DeviceMetadataExport(
    val firmwareVersion: String? = null,
    val deviceStateVersion: Int? = null,
    val canShutdown: Boolean = false,
    val hasWifi: Boolean = false,
    val hasBluetooth: Boolean = false,
    val hasEthernet: Boolean = false,
    val role: String? = null,
    val positionFlags: Int? = null,
    val hwModel: String? = null,
    val hasRemoteHardware: Boolean = false,
    val hasPKC: Boolean = false,
    val excludedModules: Int? = null,
    val hasXeddsa: Boolean = false,
)
