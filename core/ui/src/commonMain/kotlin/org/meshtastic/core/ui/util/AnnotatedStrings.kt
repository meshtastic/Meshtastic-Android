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
package org.meshtastic.core.ui.util

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import org.meshtastic.core.ui.component.SNR_FAIR_THRESHOLD
import org.meshtastic.core.ui.component.SNR_GOOD_THRESHOLD

/**
 * Converts a raw traceroute string into an [AnnotatedString] with SNR values highlighted according to their quality.
 */
fun annotateTraceroute(
    inString: String?,
    statusGreen: Color,
    statusYellow: Color,
    statusOrange: Color,
): AnnotatedString {
    if (inString == null) return buildAnnotatedString { append("") }

    return buildAnnotatedString {
        inString.lines().forEachIndexed { i, line ->
            if (i > 0) append("\n")
            // Example line: "⇊ -8.75 dB SNR"
            if (line.trimStart().startsWith("⇊")) {
                val snrRegex = Regex("""⇊ ([\d.?-]+) dB""")
                val snrMatch = snrRegex.find(line)
                val snrValue = snrMatch?.groupValues?.getOrNull(1)?.toFloatOrNull()

                if (snrValue != null) {
                    val snrColor =
                        when {
                            snrValue >= SNR_GOOD_THRESHOLD -> statusGreen
                            snrValue >= SNR_FAIR_THRESHOLD -> statusYellow
                            else -> statusOrange
                        }
                    withStyle(style = SpanStyle(color = snrColor, fontWeight = FontWeight.Bold)) { append(line) }
                } else {
                    append(line)
                }
            } else {
                append(line)
            }
        }
    }
}

/**
 * Converts a raw neighbor info string into an [AnnotatedString] with SNR values highlighted according to their quality.
 */
fun annotateNeighborInfo(
    inString: String?,
    statusGreen: Color,
    statusYellow: Color,
    statusOrange: Color,
): AnnotatedString {
    if (inString == null) return buildAnnotatedString { append("") }

    return buildAnnotatedString {
        inString.lines().forEachIndexed { i, line ->
            if (i > 0) append("\n")
            // Example line: "• NodeName (SNR: 5.5)"
            if (line.contains("(SNR: ")) {
                val snrRegex = Regex("""\(SNR: ([\d.?-]+)\)""")
                val snrMatch = snrRegex.find(line)
                val snrValue = snrMatch?.groupValues?.getOrNull(1)?.toFloatOrNull()

                if (snrValue != null) {
                    val snrColor =
                        when {
                            snrValue >= SNR_GOOD_THRESHOLD -> statusGreen
                            snrValue >= SNR_FAIR_THRESHOLD -> statusYellow
                            else -> statusOrange
                        }
                    val snrPrefix = "(SNR: "
                    append(line.substring(0, line.indexOf(snrPrefix) + snrPrefix.length))
                    withStyle(style = SpanStyle(color = snrColor, fontWeight = FontWeight.Bold)) { append("$snrValue") }
                    append(")")
                } else {
                    append(line)
                }
            } else {
                append(line)
            }
        }
    }
}
