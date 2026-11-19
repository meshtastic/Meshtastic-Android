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
package org.meshtastic.core.data.repository

import org.meshtastic.core.repository.GnssFix

/**
 * Folds a receiver's NMEA sentences into the latest [GnssFix], from GGA and GSA by any talker. A sentence whose
 * checksum fails is dropped. Fed from the platform's NMEA callback and read from the location callback, which may run
 * on different threads, so both sides hold the lock.
 */
internal class NmeaTracker(private val clock: () -> Long) {
    private var fix = GnssFix()
    private var updatedAt: Long? = null

    /** One sentence, with or without its line ending. */
    @Synchronized
    fun accept(sentence: String) {
        val body = nmeaBody(sentence.trim()) ?: return
        val fields = body.split(',')
        fix =
            when (fields[0].takeLast(KIND_LENGTH)) {
                "GGA" -> gga(fields)
                "GSA" -> gsa(fields)
                else -> return
            }
        updatedAt = clock()
    }

    /** The reading, if a sentence arrived within [freshMillis]; a stale one no longer describes the current fix. */
    @Synchronized
    fun fresh(freshMillis: Long): GnssFix? = updatedAt?.takeIf { clock() - it <= freshMillis }?.let { fix }

    private fun gga(f: List<String>): GnssFix {
        // 0 id, 1 time, 2 lat, 3 N/S, 4 lon, 5 E/W, 6 quality, 7 satellites, 8 hdop
        val quality = f.getOrNull(GGA_QUALITY)?.toIntOrNull()
        if (quality == null || quality == 0) return fix.copy(fixQuality = quality, satellitesUsed = null, hdop = null)
        return fix.copy(
            fixQuality = quality,
            satellitesUsed = f.getOrNull(GGA_SATELLITES)?.toIntOrNull(),
            hdop = f.getOrNull(GGA_HDOP)?.let(::hundredths),
        )
    }

    // 0 id, 1 mode, 2 fix type, 3..14 satellites, 15 pdop, 16 hdop, 17 vdop; NMEA 4.1 adds a system id at 18.
    private fun gsa(f: List<String>): GnssFix = fix.copy(
        fixType = f.getOrNull(GSA_FIX_TYPE)?.toIntOrNull(),
        pdop = f.getOrNull(GSA_PDOP)?.let(::hundredths),
        vdop = f.getOrNull(GSA_VDOP)?.let(::hundredths),
    )

    private companion object {
        const val KIND_LENGTH = 3
        const val GGA_QUALITY = 6
        const val GGA_SATELLITES = 7
        const val GGA_HDOP = 8
        const val GSA_FIX_TYPE = 2
        const val GSA_PDOP = 15
        const val GSA_VDOP = 17
    }
}

/** The sentence between `$` and `*` when its checksum, the XOR of every character between them, holds. */
internal fun nmeaBody(sentence: String): String? {
    val star = sentence.lastIndexOf('*')
    val framed = sentence.startsWith('$') && star > 0 && sentence.length >= star + CHECKSUM_DIGITS + 1
    val body = if (framed) sentence.substring(1, star) else null
    val given = if (framed) sentence.substring(star + 1, star + 1 + CHECKSUM_DIGITS).toIntOrNull(HEX_RADIX) else null
    return body?.takeIf { given != null && it.fold(0) { acc, c -> acc xor c.code } == given }
}

/**
 * A decimal as hundredths, truncated toward zero past two places as TinyGPS's `parseDecimal` does, with no floating
 * point to turn 1.15 into 114. Null for a blank or malformed field.
 */
internal fun hundredths(text: String): Int? {
    val negative = text.startsWith('-')
    val digits = text.removePrefix("-")
    val whole = digits.substringBefore('.').ifEmpty { if (digits.isEmpty()) "" else "0" }.toIntOrNull()
    val tail = digits.substringAfter('.', "").take(2).padEnd(2, '0').toIntOrNull()
    val magnitude = if (whole != null && tail != null) whole * HUNDRED + tail else null
    return magnitude?.let { if (negative) -it else it }
}

private const val CHECKSUM_DIGITS = 2
private const val HEX_RADIX = 16
private const val HUNDRED = 100
