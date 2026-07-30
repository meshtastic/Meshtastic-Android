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
@file:Suppress("TooManyFunctions")

package org.meshtastic.core.model.util

import org.meshtastic.proto.Channel
import org.meshtastic.proto.ClientNotification
import org.meshtastic.proto.Config
import org.meshtastic.proto.MeshPacket
import org.meshtastic.proto.ModuleConfig
import org.meshtastic.proto.MyNodeInfo
import org.meshtastic.proto.Telemetry

/**
 * When printing strings to logs sometimes we want to print useful debugging information about users or positions. But
 * we don't want to leak things like usernames or locations. So this function if given a string, will return a string
 * which is a maximum of three characters long, taken from the tail of the string. Which should effectively hide real
 * usernames and locations, but still let us see if values were zero, empty or different.
 */
val Any?.anonymize: String
    get() = this.anonymize()

/** A version of anonymize that allows passing in a custom minimum length */
fun Any?.anonymize(maxLen: Int = 3) = if (this != null) "...${this.toString().takeLast(maxLen)}" else "null"

// A toString that makes sure all newlines are removed (for nice logging).
fun Any.toOneLineString() = this.toString().replace('\n', ' ')

fun Config.toOneLineString(): String {
    // Wire toString uses field=value format
    val redactedFields = """(wifi_psk|public_key|private_key|admin_key)=[^,}]+"""
    return this.toString().replace(redactedFields.toRegex()) { "${it.groupValues[1]}=[REDACTED]" }.replace('\n', ' ')
}

fun MeshPacket.toOneLineString(): String {
    val redactedFields = """(public_key|private_key|admin_key)=[^,}]+""" // Redact keys
    return this.toString().replace(redactedFields.toRegex()) { "${it.groupValues[1]}=[REDACTED]" }.replace('\n', ' ')
}

fun Channel.toOneLineString(): String {
    // Redact the channel preshared key (psk) from logs.
    val redactedFields = """(psk)=[^,}]+"""
    return this.toString().replace(redactedFields.toRegex()) { "${it.groupValues[1]}=[REDACTED]" }.replace('\n', ' ')
}

fun ModuleConfig.toOneLineString(): String {
    // Redact MQTT credentials from logs.
    val redactedFields = """(password|username)=[^,}]+"""
    return this.toString().replace(redactedFields.toRegex()) { "${it.groupValues[1]}=[REDACTED]" }.replace('\n', ' ')
}

fun MyNodeInfo.toOneLineString(): String {
    // Redact the hardware unique identifier from logs.
    val redactedFields = """(device_id)=[^,}]+"""
    return this.toString().replace(redactedFields.toRegex()) { "${it.groupValues[1]}=[REDACTED]" }.replace('\n', ' ')
}

fun Any.toPIIString() = if (!isDebug) {
    "<PII?>"
} else {
    this.toOneLineString()
}

@Suppress("MagicNumber")
fun ByteArray.toHexString() = joinToString("") { it.toUByte().toString(16).padStart(2, '0') }

private const val MPS_TO_KMPH = 3.6f
private const val KM_TO_MILES = 0.621371f

fun Int.mpsToKmph(): Float {
    // Convert meters per second to kilometers per hour
    val kmph = this * MPS_TO_KMPH
    return kmph
}

fun Int.mpsToMph(): Float {
    // Convert meters per second to miles per hour
    val mph = this * MPS_TO_KMPH * KM_TO_MILES
    return mph
}

/** Returns true if this packet arrived via a LoRa transport mechanism. */
fun MeshPacket.isLora(): Boolean = transport_mechanism == MeshPacket.TransportMechanism.TRANSPORT_LORA ||
    transport_mechanism == MeshPacket.TransportMechanism.TRANSPORT_LORA_ALT1 ||
    transport_mechanism == MeshPacket.TransportMechanism.TRANSPORT_LORA_ALT2 ||
    transport_mechanism == MeshPacket.TransportMechanism.TRANSPORT_LORA_ALT3

/**
 * Arrival time in epoch seconds, or null when the radio had no clock at reception.
 *
 * Firmware that gained explicit presence omits the field; older firmware still sends 0 for the same state. Both mean
 * unknown — a 1970 arrival time is never a genuine reading. Folding 0 is safe here for exactly that reason; see
 * [snrOrNull] for the fields where it is not.
 */
fun MeshPacket.rxTimeOrNull(): Int? = rx_time?.takeIf { it != 0 }

/**
 * Signal-to-noise ratio in dB for this reception, or null when the packet carries no SNR measurement (it did not arrive
 * over LoRa, or the radio reported none).
 *
 * Deliberately does NOT fold 0 the way [rxTimeOrNull] does: 0 dB is a genuine, common reading — a signal at the noise
 * floor, comfortably demodulable on every preset — so treating it as "absent" would hide real measurements and, worse,
 * discard the only zero that can ever reach us. Under proto3 implicit presence a field at its zero value is never put
 * on the wire, so an SNR-less packet from firmware predating the optional conversion already decodes to null for free.
 * A 0 that survives to this accessor was written explicitly and means 0 dB.
 *
 * Presence cannot be inferred from [isLora] instead: `transport_mechanism` defaults to `TRANSPORT_INTERNAL` (0), so
 * firmware that never sets it would have every reading suppressed.
 */
fun MeshPacket.snrOrNull(): Float? = rx_snr

/** Returns true if this packet is a direct LoRa signal (not MQTT, and hop count matches). */
fun MeshPacket.isDirectSignal(): Boolean =
    rxTimeOrNull() != null && hop_start == hop_limit && via_mqtt != true && isLora()

/** Returns true if this telemetry packet contains valid, plot-able environment metrics. */
fun Telemetry.hasValidEnvironmentMetrics(): Boolean {
    val metrics = this.environment_metrics ?: return false
    return metrics.relative_humidity != null && metrics.temperature != null && !metrics.temperature!!.isNaN()
}

/**
 * Given a human name, strip out the first letter of the first three words and return that as the initials for that
 * user, ignoring emojis. If the original name is only one word, strip vowels from the original name and if the result
 * is 3 or more characters, use the first three characters. If not, just take the first 3 characters of the original
 * name.
 */
@Suppress("MagicNumber")
fun getInitials(fullName: String): String {
    val maxInitialLength = 4
    val minWordCountForInitials = 2
    val name = fullName.trim().withoutEmojis()
    val words = name.split(Regex("\\s+")).filter { it.isNotEmpty() }

    val initials =
        when (words.size) {
            in 0 until minWordCountForInitials -> {
                val nameWithoutVowels =
                    if (name.isNotEmpty()) {
                        name.first() + name.drop(1).filterNot { c -> c.lowercase() in "aeiou" }
                    } else {
                        ""
                    }
                if (nameWithoutVowels.length >= maxInitialLength) nameWithoutVowels else name
            }

            else -> words.map { it.first() }.joinToString("")
        }
    return initials.take(maxInitialLength)
}

fun String.withoutEmojis(): String = filterNot { char -> char.isSurrogate() }

private val OTA_PATTERN = Regex("\\bOTA\\b", RegexOption.IGNORE_CASE)

/**
 * OTA-status detector matching the firmware update preflight gate's criterion: any ClientNotification whose message
 * contains "OTA" as a whole word (e.g. "Rebooting to BLE OTA", "Cannot start OTA: ...") is consumed by the firmware
 * update flow and suppressed as a generic alert. Uses word-boundary matching so unrelated messages containing the
 * substring (e.g. "ROTATE", "QUOTA") are not misclassified.
 */
fun ClientNotification.isOtaStatusNotification(): Boolean = message.isNotBlank() && OTA_PATTERN.containsMatchIn(message)
