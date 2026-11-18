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
package org.meshtastic.feature.connections.model

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import org.meshtastic.feature.connections.model.host.Bearer
import org.meshtastic.feature.connections.model.host.ExtraRadio
import org.meshtastic.feature.connections.model.host.ExtraRadioDevice
import org.meshtastic.feature.connections.model.host.NodePhase
import org.meshtastic.feature.connections.model.host.SensorKind
import org.meshtastic.feature.connections.model.host.SensorUnread
import org.meshtastic.proto.Config
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

/**
 * The node card from a node-kmp host's dashboard: [status], [bearers] and [sensors] are the bodies of
 * `/api/node/v1/status`, `bearers` and `sensors`, and [nowMs] the wall clock a sensor's last good read is aged against.
 * Null when [status] is not a status body.
 */
fun dashboardNodeStatus(status: String, bearers: String?, sensors: String?, nowMs: Long): NodeStatusUi? {
    val head = parse(status)
    val phase = head?.string("phase")?.let { name -> NodePhase.entries.firstOrNull { it.name == name } }
    if (head == null || phase == null) return null
    val bearerBody = parse(bearers)
    val rows = bearerBody?.let(::bearerRows).orEmpty()
    return NodeStatusUi(
        phase = phase,
        nodeId = head.string("id") ?: head.long("num")?.let(::nodeId),
        longName = head.string("longName"),
        shortName = head.string("shortName"),
        peerCount = head.int("peerCount") ?: 0,
        failure = head.string("failure"),
        settingsReset = head.bool("settingsReset") ?: false,
        bearers = rows,
        sensors = parse(sensors)?.let { sensorsUi(it, nowMs) },
        // Every extra radio is held off until one is switched on; said only where the host has one.
        extraRadioFailure = bearerBody?.string("extraRadioFailure")?.takeIf { rows.any { row -> row.extra != null } },
    )
}

/** The bearers in the card's order, LoRa followed by the host's extra LoRa radios. */
private fun bearerRows(body: JsonObject): List<BearerRowUi> {
    val items = body.array("items").mapNotNull { it as? JsonObject }
    val extras = body.array("extraRadios").mapNotNull { it as? JsonObject }
    return Bearer.entries
        .filter { it.onCard }
        .flatMap { bearer ->
            val item = items.firstOrNull { it.string("name") == bearer.transportName } ?: return@flatMap emptyList()
            val row = bearerRow(bearer, item, extra = null)
            if (bearer == Bearer.LORA) listOf(row) + extraRows(extras, row.enabled) else listOf(row)
        }
}

private fun extraRows(extras: List<JsonObject>, loraOn: Boolean): List<BearerRowUi> = extras
    .mapNotNull { item ->
        val name = item.string("name") ?: return@mapNotNull null
        val off = if (item.bool("switchedOn") == false) setOf(name) else emptySet()
        val details = (item["radio"] as? JsonObject)?.let(::extraRadio)
        val extra = extraRadioUi(name, details, off = off, loraOn = loraOn) ?: return@mapNotNull null
        bearerRow(Bearer.LORA, item, extra)
    }
    .sortedBy { it.radio }

/** What the host says an extra radio is, or null for a device kind this app does not know. */
private fun extraRadio(radio: JsonObject): ExtraRadio? {
    val device = radio.string("device")?.let { name -> ExtraRadioDevice.entries.firstOrNull { it.name == name } }
    return device?.let {
        ExtraRadio(
            device = it,
            id = radio.string("id"),
            preset =
            radio.string("preset")?.let { name ->
                Config.LoRaConfig.ModemPreset.entries.firstOrNull { preset -> preset.name == name }
            },
            bridge = radio.bool("bridge") ?: false,
            duplex = radio.bool("duplex") ?: false,
        )
    }
}

/** A requirement outranks the availability: the user can act on it, while availability only reports. */
private fun bearerRow(bearer: Bearer, item: JsonObject, extra: ExtraRadioUi?): BearerRowUi {
    val enabled = item.bool("enabled") ?: false
    val requirement = item["requirement"] as? JsonObject
    val detail = requirement?.string("detail").orEmpty()
    val reason = item.string("availabilityReason").orEmpty()
    val state =
        when (requirement?.string("state")) {
            "NEEDS_PERMISSION" -> BearerRowState.NeedsPermission(detail)

            "NEEDS_USB_PERMISSION" -> BearerRowState.NeedsUsbPermission(detail)

            "NEEDS_HARDWARE" -> BearerRowState.NeedsHardware(detail)

            "UNSUPPORTED" -> BearerRowState.Unsupported(detail)

            else ->
                when {
                    !enabled && bearer == Bearer.LORA && extra == null -> BearerRowState.RegionUnset
                    !enabled -> BearerRowState.Off
                    item.string("availability") == "ACTIVE" -> BearerRowState.Active
                    item.string("availability") == "UNAVAILABLE" -> BearerRowState.Unavailable(reason)
                    item.string("availability") == "NEEDS_PERMISSION" -> BearerRowState.NeedsPermission(reason)
                    else -> BearerRowState.Ready
                }
        }
    val counters = item["counters"] as? JsonObject
    return BearerRowUi(
        bearer = bearer,
        enabled = enabled,
        switchable = bearer.switchable,
        state = state,
        received = counters?.long("rx") ?: 0,
        sent = counters?.long("tx") ?: 0,
        relayed = counters?.long("relayed") ?: 0,
        extra = extra,
    )
}

/** The sensors section, or null on a host with no bus. */
private fun sensorsUi(body: JsonObject, nowMs: Long): SensorsUi? = when (body.string("bus")) {
    "SCANNING" -> SensorsUi.Scanning

    "FAILED" -> SensorsUi.BusFailed(body.string("failure").orEmpty())

    "SCANNED" -> {
        val rows = body.array("items").mapNotNull { (it as? JsonObject)?.let { item -> sensorRow(item, nowMs) } }
        if (rows.isEmpty()) SensorsUi.NoneFound else SensorsUi.Found(rows)
    }

    else -> null
}

private fun sensorRow(item: JsonObject, nowMs: Long): SensorRowUi? {
    val address = item.int("address") ?: return null
    val feeds = item.array("feeds").mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.let(KIND_KEYS::get) }.sorted()
    val reads = (item["reads"] as? JsonObject)?.values?.mapNotNull { it as? JsonObject }.orEmpty()
    // Whole minutes, so a fresh read and the one before it read the same.
    val sinceGoodRead =
        reads
            .mapNotNull { it.long("lastSuccessAtMs") }
            .maxOrNull()
            ?.let { (nowMs - it).milliseconds.inWholeMinutes }
            ?.takeIf { it > 0 }
            ?.minutes
    val error = item.string("error")
    val state =
        when (item.string("state")) {
            "NOT_READ" ->
                SensorRowState.Unread(
                    item.string("unread")?.let { name -> SensorUnread.entries.firstOrNull { it.name == name } }
                        ?: SensorUnread.UNIDENTIFIED,
                )

            "FAILING" -> SensorRowState.Failing(error.orEmpty(), sinceGoodRead)

            "WAITING" -> SensorRowState.Waiting

            else -> SensorRowState.Reading(sinceGoodRead)
        }
    return SensorRowUi(
        name = item.string("sensor") ?: item.string("name"),
        address = "0x" + address.toString(HEX).padStart(2, '0'),
        feeds = feeds,
        state = state,
    )
}

private val KIND_KEYS =
    mapOf("environment" to SensorKind.ENVIRONMENT, "airQuality" to SensorKind.AIR_QUALITY, "power" to SensorKind.POWER)

private const val HEX = 16
