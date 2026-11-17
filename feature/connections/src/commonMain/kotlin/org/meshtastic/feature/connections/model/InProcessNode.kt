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

import org.meshtastic.core.common.util.formatString
import org.meshtastic.feature.connections.model.host.Bearer
import org.meshtastic.feature.connections.model.host.ExtraRadio
import org.meshtastic.feature.connections.model.host.PrimaryRadio
import org.meshtastic.node.host.NodeStatus
import org.meshtastic.node.host.Requirement
import org.meshtastic.node.sensors.I2cBusState
import org.meshtastic.node.sensors.I2cSensorState
import org.meshtastic.node.transport.TransportAvailability
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import org.meshtastic.core.network.radio.PrimaryRadio as NetworkPrimaryRadio
import org.meshtastic.node.host.ExtraRadio as NodeExtraRadio
import org.meshtastic.node.transport.Bearer as NodeBearer

// The in-process node's seam, with InProcessNodeEnums.kt: node-kmp's types become the card's here, and nowhere else.

/**
 * The card's state; [extraRadiosOff] names the extra LoRa radios switched off by themselves, and [primaryRadio] is what
 * the host says its first LoRa radio is.
 */
fun NodeStatus.toUi(
    extraRadiosOff: Set<String> = emptySet(),
    primaryRadio: NetworkPrimaryRadio? = null,
): NodeStatusUi = nodeStatusUi(
    phase = phase.toApp(),
    nodeNum = nodeNum,
    longName = longName,
    shortName = shortName,
    peerCount = peerCount,
    failure = failure,
    settingsReset = settingsReset,
    extraRadioFailure = extraRadioFailure,
    bearers =
    NodeBearer.entries
        .filter { it.toApp().onCard }
        .flatMap { nodeBearer ->
            val status = bearers[nodeBearer] ?: return@flatMap emptyList()
            val bearer = nodeBearer.toApp()
            val row =
                bearerRow(
                    bearer = bearer,
                    enabled = status.enabled,
                    availability = status.availability,
                    requirement = status.requirement,
                    received = status.counters.rx,
                    sent = status.counters.tx,
                    relayed = status.counters.relayed,
                )
            if (bearer == Bearer.LORA) {
                listOf(row.copy(primary = primaryRadio?.toApp())) +
                    extraRadioRows(extraRadiosOff, status.enabled)
            } else {
                listOf(row)
            }
        },
)

/** The host's further LoRa radios, in number order, each a row under the LoRa bearer's. */
private fun NodeStatus.extraRadioRows(off: Set<String>, loraOn: Boolean): List<BearerRowUi> = extraRadios
    .mapNotNull { (name, status) ->
        val extra = extraRadioUi(name, extraRadioDetails[name]?.toApp(), off, loraOn) ?: return@mapNotNull null
        bearerRow(
            bearer = Bearer.LORA,
            enabled = status.enabled,
            availability = status.availability,
            requirement = status.requirement,
            received = status.counters.rx,
            sent = status.counters.tx,
            relayed = status.counters.relayed,
            extra = extra,
        )
    }
    .sortedBy { it.radio }

/** A requirement outranks the availability: the user can act on it, while availability only reports. */
@Suppress("LongParameterList")
internal fun bearerRow(
    bearer: Bearer,
    enabled: Boolean,
    availability: TransportAvailability?,
    requirement: Requirement,
    received: Long,
    sent: Long,
    relayed: Long,
    extra: ExtraRadioUi? = null,
): BearerRowUi = BearerRowUi(
    bearer = bearer,
    enabled = enabled,
    switchable = bearer.switchable,
    extra = extra,
    state =
    when (requirement) {
        is Requirement.NeedsPermission -> BearerRowState.NeedsPermission(requirement.permission)

        is Requirement.NeedsUsbPermission -> BearerRowState.NeedsUsbPermission(requirement.device)

        is Requirement.NeedsHardware -> BearerRowState.NeedsHardware(requirement.what)

        is Requirement.Unsupported -> BearerRowState.Unsupported(requirement.reason)

        Requirement.Ready ->
            when {
                !enabled && bearer == Bearer.LORA && extra == null -> BearerRowState.RegionUnset

                !enabled -> BearerRowState.Off

                availability is TransportAvailability.Active -> BearerRowState.Active

                availability is TransportAvailability.Unavailable ->
                    BearerRowState.Unavailable(availability.reason)

                availability is TransportAvailability.NeedsPermission ->
                    BearerRowState.NeedsPermission(availability.permission)

                else -> BearerRowState.Ready
            }
    },
    received = received,
    sent = sent,
    relayed = relayed,
)

fun I2cBusState.toSensorsUi(): SensorsUi = when (this) {
    I2cBusState.Scanning -> SensorsUi.Scanning

    is I2cBusState.Failed -> SensorsUi.BusFailed(reason)

    is I2cBusState.Scanned ->
        if (sensors.isEmpty()) SensorsUi.NoneFound else SensorsUi.Found(sensors.map { it.toRow() })
}

private fun I2cSensorState.toRow(): SensorRowUi {
    val error = lastError
    val unread = unread
    return SensorRowUi(
        name = sensorName ?: name,
        address = formatString("0x%02x", address),
        feeds = kinds.sorted().map { it.toApp() },
        state =
        when {
            unread != null -> SensorRowState.Unread(unread.toApp())
            error != null -> SensorRowState.Failing(error, sinceGoodRead())
            reads.values.all { it.isPending } -> SensorRowState.Waiting
            else -> SensorRowState.Reading(sinceGoodRead())
        },
    )
}

/** The age of the newest good read of any kind, in whole minutes so a fresh state reads the same as the last. */
private fun I2cSensorState.sinceGoodRead(): Duration? =
    reads.values.mapNotNull { it.sinceLastSuccess }.minOrNull()?.inWholeMinutes?.takeIf { it > 0 }?.minutes

internal fun NodeExtraRadio.toApp(): ExtraRadio =
    ExtraRadio(device = device.toApp(), id = id, preset = preset, bridge = bridge, duplex = duplex)

internal fun NetworkPrimaryRadio.toApp(): PrimaryRadio = PrimaryRadio(device = device.toApp(), id = id)
