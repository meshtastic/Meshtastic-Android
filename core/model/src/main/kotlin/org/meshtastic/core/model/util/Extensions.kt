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

package org.meshtastic.core.model.util

import android.widget.EditText
import org.meshtastic.core.model.BuildConfig
import org.meshtastic.proto.ConfigProtos
import org.meshtastic.proto.MeshProtos

/**
 * When printing strings to logs sometimes we want to print useful debugging information about users or positions. But
 * we don't want to leak things like usernames or locations. So this function if given a string, will return a string
 * which is a maximum of three characters long, taken from the tail of the string. Which should effectively hide real
 * usernames and locations, but still let us see if values were zero, empty or different.
 */
val Any?.anonymize: String
    get() = this.anonymize()

/** A version of anonymize that allows passing in a custom minimum length */
fun Any?.anonymize(maxLen: Int = 3) = if (this != null) ("..." + this.toString().takeLast(maxLen)) else "null"

// A toString that makes sure all newlines are removed (for nice logging).
fun Any.toOneLineString() = this.toString().replace('\n', ' ')

fun ConfigProtos.Config.toOneLineString(): String {
    val redactedFields = """(wifi_psk:|public_key:|private_key:|admin_key:)\s*".*"""
    return this.toString()
        .replace(redactedFields.toRegex()) { "${it.groupValues[1]} \"[REDACTED]\"" }
        .replace('\n', ' ')
}

fun MeshProtos.MeshPacket.toOneLineString(): String {
    val redactedFields = """(public_key:|private_key:|admin_key:)\s*".*""" // Redact keys
    return this.toString()
        .replace(redactedFields.toRegex()) { "${it.groupValues[1]} \"[REDACTED]\"" }
        .replace('\n', ' ')
}

fun MeshProtos.toOneLineString(): String {
    val redactedFields = """(public_key:|private_key:|admin_key:)\s*".*""" // Redact keys
    return this.toString()
        .replace(redactedFields.toRegex()) { "${it.groupValues[1]} \"[REDACTED]\"" }
        .replace('\n', ' ')
}

// Return a one line string version of an object (but if a release build, just say 'might be PII)
fun Any.toPIIString() = if (!BuildConfig.DEBUG) {
    "<PII?>"
} else {
    this.toOneLineString()
}

fun ByteArray.toHexString() = joinToString("") { "%02x".format(it) }

@Suppress("MagicNumber")
fun formatAgo(lastSeenUnix: Int, currentTimeMillis: Long = System.currentTimeMillis()): String {
    val currentTime = (currentTimeMillis / 1000).toInt()
    val diffMin = (currentTime - lastSeenUnix) / 60
    return when {
        diffMin < 1 -> "now"
        diffMin < 60 -> diffMin.toString() + " min"
        diffMin < 2880 -> (diffMin / 60).toString() + " h"
        diffMin < 1440000 -> (diffMin / (60 * 24)).toString() + " d"
        else -> "?"
    }
}

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

// Allows usage like email.onEditorAction(EditorInfo.IME_ACTION_NEXT, { confirm() })
fun EditText.onEditorAction(actionId: Int, func: () -> Unit) {
    setOnEditorActionListener { _, receivedActionId, _ ->
        if (actionId == receivedActionId) {
            func()
        }
        true
    }
}
