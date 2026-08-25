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

/**
 * The owner name of a licensed (ham) node, which firmware builds from two `HamParameters` fields.
 *
 * `AdminModule::handleSetHamMode()` joins the call sign and the optional `long_name` with [SEPARATOR] — the form hams
 * already use on the air — so `KD2ABC` plus `Attic Heltec` becomes `KD2ABC//Attic Heltec`, and a ham who supplied no
 * long name is named after the bare call sign. The device only ever reports that composed name back as
 * `User.long_name`, so editing either half means splitting it again: [split] and [compose] are inverses over every name
 * firmware can produce.
 */
object HamName {

    /** What firmware puts between the call sign and the long name. */
    const val SEPARATOR = "//"

    /** Usable bytes in `HamParameters.call_sign` (`max_size:8`, one byte of which is the NUL terminator). */
    const val MAX_CALL_SIGN_BYTES = 7

    /** Usable bytes in `HamParameters.long_name` (`max_size:15`, one byte of which is the NUL terminator). */
    const val MAX_LONG_NAME_BYTES = 14

    /**
     * Joins the two halves the way firmware does. A blank [longName] yields the bare [callSign] — firmware treats unset
     * and whitespace-only alike, so composing a separator for either would not match what the node stores.
     */
    fun compose(callSign: String, longName: String): String =
        if (longName.isBlank()) callSign else callSign + SEPARATOR + longName

    /**
     * Splits an owner long name into its call sign and long name at the first [SEPARATOR].
     *
     * A name with no separator is all call sign, which is what firmware writes for a ham who supplied no long name.
     */
    fun split(ownerLongName: String): Pair<String, String> {
        val at = ownerLongName.indexOf(SEPARATOR)
        return if (at < 0) {
            ownerLongName to ""
        } else {
            ownerLongName.substring(0, at) to ownerLongName.substring(at + SEPARATOR.length)
        }
    }

    /**
     * Reshapes an existing owner long name for ham onboarding, so enabling licensed mode does not discard a name the
     * operator already chose.
     *
     * A name that can still serve as a call sign is returned untouched — including one this app composed during an
     * earlier licensing, whose halves survive as they are. Anything wider is demoted to the descriptive half, clipped
     * to [MAX_LONG_NAME_BYTES], leaving the call sign empty for the operator to fill in.
     */
    fun forOnboarding(ownerLongName: String): String {
        val (callSign, longName) = split(ownerLongName)
        if (callSign.utf8Size() <= MAX_CALL_SIGN_BYTES) return ownerLongName
        return compose("", longName.ifBlank { callSign }.limitToBytes(MAX_LONG_NAME_BYTES))
    }

    /**
     * Reshapes an owner long name on the way back out of licensed mode.
     *
     * A fully composed name survives untouched — `KD2ABC//Attic Heltec` is exactly what the node is called, and an
     * operator clearing the toggle has no reason to lose it. Only the half-filled name left by an abandoned onboarding,
     * where no call sign was ever entered, is flattened, so a stray separator cannot become the node's name.
     */
    fun forUnlicensing(ownerLongName: String): String {
        val (callSign, longName) = split(ownerLongName)
        return if (callSign.isBlank()) longName else ownerLongName
    }
}

/** UTF-8 length in bytes, the unit every `max_size` in the protobuf options is counted in. */
fun String.utf8Size(): Int = encodeToByteArray().size

/** Clips to at most [maxBytes] of UTF-8, stepping whole code points so a surrogate pair is never cut in half. */
private fun String.limitToBytes(maxBytes: Int): String {
    if (utf8Size() <= maxBytes) return this
    var end = 0
    var used = 0
    while (end < length) {
        val step = if (this[end].isHighSurrogate() && end + 1 < length && this[end + 1].isLowSurrogate()) 2 else 1
        val bytes = substring(end, end + step).utf8Size()
        if (used + bytes > maxBytes) break
        used += bytes
        end += step
    }
    return substring(0, end)
}
