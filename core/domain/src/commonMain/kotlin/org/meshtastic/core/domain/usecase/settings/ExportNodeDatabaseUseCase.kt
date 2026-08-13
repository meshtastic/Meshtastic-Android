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

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.okio.encodeToBufferedSink
import okio.BufferedSink
import org.koin.core.annotation.Single
import org.meshtastic.core.common.util.nowMillis
import org.meshtastic.core.model.Node
import org.meshtastic.core.model.util.GeoConstants
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.proto.AirQualityMetrics
import org.meshtastic.proto.DeviceMetadata
import org.meshtastic.proto.DeviceMetrics
import org.meshtastic.proto.EnvironmentMetrics
import org.meshtastic.proto.HardwareModel
import org.meshtastic.proto.MeshPacket
import org.meshtastic.proto.Paxcount
import org.meshtastic.proto.PowerMetrics
import kotlin.time.Instant
import org.meshtastic.proto.Position as WirePosition

/**
 * Exports the currently selected device's node database as a JSON [NodeDatabaseExport] document. Absent readings are
 * omitted rather than written as 0/sentinel values, so 0 dB SNR or 0 °C stay distinguishable from "no reading".
 */
@Single
class ExportNodeDatabaseUseCase(private val nodeRepository: NodeRepository) {

    companion object {
        private const val SCHEMA_VERSION = 1
    }

    private val json = Json {
        prettyPrint = true
        explicitNulls = false
    }

    @OptIn(ExperimentalSerializationApi::class)
    suspend operator fun invoke(sink: BufferedSink) {
        val nodes =
            nodeRepository
                .getNodeDbSnapshot()
                .values
                .sortedWith(compareByDescending<Node> { it.lastHeard }.thenBy { it.num.toUInt() })
                .map { it.toExport() }
        val export =
            NodeDatabaseExport(
                schemaVersion = SCHEMA_VERSION,
                exportedAt = Instant.fromEpochMilliseconds(nowMillis).toString(),
                myNodeNum = nodeRepository.myNodeInfo.value?.myNodeNum?.toUnsignedLong(),
                nodes = nodes,
            )
        json.encodeToBufferedSink(export, sink)
        sink.flush()
    }
}

private fun Int.toUnsignedLong(): Long = toUInt().toLong()

private fun Node.toExport(): NodeExport = NodeExport(
    num = num.toUnsignedLong(),
    id = user.id.takeIf { it.isNotEmpty() },
    longName = user.long_name.takeIf { it.isNotEmpty() },
    shortName = user.short_name.takeIf { it.isNotEmpty() },
    hwModel = user.hw_model.takeIf { it != HardwareModel.UNSET }?.name,
    role = user.role.name,
    isLicensed = user.is_licensed,
    isUnmessagable = user.is_unmessagable,
    publicKey = (publicKey ?: user.public_key).takeIf { it.size > 0 }?.base64(),
    signsPackets = signsPackets,
    manuallyVerified = manuallyVerified,
    lastHeard = lastHeard.takeIf { it != 0 }?.toLong(),
    snr = snrOrNull,
    rssi = rssiOrNull,
    channel = channel,
    viaMqtt = viaMqtt,
    hopsAway = hopsAway.takeIf { it >= 0 },
    lastTransport = MeshPacket.TransportMechanism.fromValue(lastTransport)?.takeIf { lastTransport != 0 }?.name,
    isFavorite = isFavorite,
    isIgnored = isIgnored,
    isMuted = isMuted,
    nodeStatus = nodeStatus?.takeIf { it.isNotEmpty() },
    notes = notes.takeIf { it.isNotEmpty() },
    position = position.toExport(),
    deviceMetrics = deviceMetrics.toExport(),
    environmentMetrics = environmentMetrics.toExport(),
    powerMetrics = powerMetrics.toExport(),
    powerChannelLabels = powerChannelLabels.takeIf { it.isNotEmpty() },
    airQualityMetrics = airQualityMetrics.toExport(),
    paxcounter = paxcounter.toExport(),
    metadata = metadata?.toExport(),
)

private fun WirePosition.toExport(): PositionExport? = PositionExport(
    latitude = latitude_i?.times(GeoConstants.DEG_D),
    longitude = longitude_i?.times(GeoConstants.DEG_D),
    altitude = altitude,
    time = time.takeIf { it != 0 }?.toLong(),
    locationSource = location_source.takeIf { it != WirePosition.LocSource.LOC_UNSET }?.name,
    groundSpeed = ground_speed,
    groundTrack = ground_track,
    satsInView = sats_in_view.takeIf { it != 0 },
    precisionBits = precision_bits.takeIf { it != 0 },
)
    .takeUnless { it == PositionExport() }

private fun DeviceMetrics.toExport(): DeviceMetricsExport? = DeviceMetricsExport(
    batteryLevel = battery_level,
    voltage = voltage,
    channelUtilization = channel_utilization,
    airUtilTx = air_util_tx,
    uptimeSeconds = uptime_seconds,
)
    .takeUnless { it == DeviceMetricsExport() }

// Reads the deprecated repeated one_wire_temperature so pre-2.8 stored telemetry still round-trips.
@Suppress("DEPRECATION")
private fun EnvironmentMetrics.toExport(): EnvironmentMetricsExport? = EnvironmentMetricsExport(
    temperature = temperature,
    relativeHumidity = relative_humidity,
    barometricPressure = barometric_pressure,
    gasResistance = gas_resistance,
    voltage = voltage,
    current = current,
    iaq = iaq,
    distance = distance,
    lux = lux,
    whiteLux = white_lux,
    irLux = ir_lux,
    uvLux = uv_lux,
    windDirection = wind_direction,
    windSpeed = wind_speed,
    weight = weight,
    windGust = wind_gust,
    windLull = wind_lull,
    radiation = radiation,
    rainfall1h = rainfall_1h,
    rainfall24h = rainfall_24h,
    soilMoisture = soil_moisture,
    soilTemperature = soil_temperature,
    oneWireTemperature = one_wire_temperature.takeIf { it.isNotEmpty() },
    oneWireTemperatureCh0 = one_wire_temperature_ch0,
    oneWireTemperatureCh1 = one_wire_temperature_ch1,
    oneWireTemperatureCh2 = one_wire_temperature_ch2,
    oneWireTemperatureCh3 = one_wire_temperature_ch3,
    oneWireTemperatureCh4 = one_wire_temperature_ch4,
    oneWireTemperatureCh5 = one_wire_temperature_ch5,
    oneWireTemperatureCh6 = one_wire_temperature_ch6,
    oneWireTemperatureCh7 = one_wire_temperature_ch7,
    adcVoltageCh0 = adc_voltage_ch0,
    adcVoltageCh1 = adc_voltage_ch1,
    adcVoltageCh2 = adc_voltage_ch2,
    adcVoltageCh3 = adc_voltage_ch3,
    adcVoltageCh4 = adc_voltage_ch4,
    adcVoltageCh5 = adc_voltage_ch5,
    adcVoltageCh6 = adc_voltage_ch6,
    adcVoltageCh7 = adc_voltage_ch7,
)
    .takeUnless { it == EnvironmentMetricsExport() }

private fun PowerMetrics.toExport(): PowerMetricsExport? = PowerMetricsExport(
    ch1Voltage = ch1_voltage,
    ch1Current = ch1_current,
    ch2Voltage = ch2_voltage,
    ch2Current = ch2_current,
    ch3Voltage = ch3_voltage,
    ch3Current = ch3_current,
    ch4Voltage = ch4_voltage,
    ch4Current = ch4_current,
    ch5Voltage = ch5_voltage,
    ch5Current = ch5_current,
    ch6Voltage = ch6_voltage,
    ch6Current = ch6_current,
    ch7Voltage = ch7_voltage,
    ch7Current = ch7_current,
    ch8Voltage = ch8_voltage,
    ch8Current = ch8_current,
)
    .takeUnless { it == PowerMetricsExport() }

private fun AirQualityMetrics.toExport(): AirQualityMetricsExport? = AirQualityMetricsExport(
    pm10Standard = pm10_standard,
    pm25Standard = pm25_standard,
    pm100Standard = pm100_standard,
    pm10Environmental = pm10_environmental,
    pm25Environmental = pm25_environmental,
    pm100Environmental = pm100_environmental,
    particles03um = particles_03um,
    particles05um = particles_05um,
    particles10um = particles_10um,
    particles25um = particles_25um,
    particles50um = particles_50um,
    particles100um = particles_100um,
    co2 = co2,
    co2Temperature = co2_temperature,
    co2Humidity = co2_humidity,
    formFormaldehyde = form_formaldehyde,
    formHumidity = form_humidity,
    formTemperature = form_temperature,
    pm40Standard = pm40_standard,
    particles40um = particles_40um,
    pmTemperature = pm_temperature,
    pmHumidity = pm_humidity,
    pmVocIdx = pm_voc_idx,
    pmNoxIdx = pm_nox_idx,
    particlesTps = particles_tps,
)
    .takeUnless { it == AirQualityMetricsExport() }

private fun Paxcount.toExport(): PaxcountExport? =
    PaxcountExport(wifi = wifi, ble = ble, uptime = uptime).takeUnless { it == PaxcountExport() }

private fun DeviceMetadata.toExport(): DeviceMetadataExport = DeviceMetadataExport(
    firmwareVersion = firmware_version.takeIf { it.isNotEmpty() },
    deviceStateVersion = device_state_version.takeIf { it != 0 },
    canShutdown = canShutdown,
    hasWifi = hasWifi,
    hasBluetooth = hasBluetooth,
    hasEthernet = hasEthernet,
    role = role.name,
    positionFlags = position_flags.takeIf { it != 0 },
    hwModel = hw_model.takeIf { it != HardwareModel.UNSET }?.name,
    hasRemoteHardware = hasRemoteHardware,
    hasPKC = hasPKC,
    excludedModules = excluded_modules.takeIf { it != 0 },
    hasXeddsa = has_xeddsa,
)
