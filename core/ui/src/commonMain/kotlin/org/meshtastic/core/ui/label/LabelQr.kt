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
package org.meshtastic.core.ui.label

import org.meshtastic.core.model.util.getSharedContactUrl
import org.meshtastic.proto.SharedContact
import qrcode.QRCode
import qrcode.raw.ErrorCorrectionLevel

/** Dots across the D110's head, which is also the height of 12 mm stock. */
const val LABEL_HEAD_DOTS = 96

/** Below 2 printer dots per module (0.25 mm) a thermal print stops scanning. */
internal const val MIN_DOTS_PER_MODULE = 2

/** One module of quiet zone on each side of the code. */
private const val QUIET_ZONE_MODULES = 2

/** A QR code's modules, without a quiet zone. */
class QrModules(val size: Int, private val dark: BooleanArray) {
    fun isDark(row: Int, column: Int): Boolean = dark[row * size + column]
}

fun interface QrEncoder {
    fun encode(text: String): QrModules
}

/** Error correction L, as labeltastic prints: the label has no room to spend on redundancy. */
val LabelQrEncoder = QrEncoder { text ->
    val rows = QRCode.ofSquares().withErrorCorrectionLevel(ErrorCorrectionLevel.LOW).build(text).rawData
    val size = rows.size
    QrModules(size, BooleanArray(size * size) { rows[it / size][it % size].dark })
}

/** A QR code that fits the head at a whole number of dots per module. */
class FittedQr(val payload: String, val modules: QrModules, val dotsPerModule: Int)

/**
 * The largest whole number of dots per module at which a code of [moduleCount] modules and its quiet zone fit [side],
 * or null when that is under [MIN_DOTS_PER_MODULE].
 */
internal fun dotsPerModule(moduleCount: Int, side: Int = LABEL_HEAD_DOTS): Int? =
    (side / (moduleCount + QUIET_ZONE_MODULES)).takeIf { it >= MIN_DOTS_PER_MODULE }

/** The first of [payloads] whose code fits, or null when none does. Later payloads are only encoded when needed. */
internal fun fitQr(payloads: Sequence<String>, encoder: QrEncoder, side: Int = LABEL_HEAD_DOTS): FittedQr? =
    payloads.firstNotNullOfOrNull { payload ->
        val modules = encoder.encode(payload)
        dotsPerModule(modules.size, side)?.let { FittedQr(payload, modules, it) }
    }

/** Long name lengths, in UTF-8 bytes, tried in turn when a contact's code is too dense: first the whole name. */
private val LONG_NAME_CUTS = listOf(null, 32, 24, 16)

/**
 * The contact URL to encode, then the same with the long name cut shorter and shorter. Only the QR payload is cut; the
 * label still prints the whole name.
 */
internal fun contactQrPayloads(contact: SharedContact): Sequence<String> =
    LONG_NAME_CUTS.asSequence().map { cut -> contact.withLongNameCut(cut).getSharedContactUrl().toString() }

private fun SharedContact.withLongNameCut(maxBytes: Int?): SharedContact {
    val user = user
    return if (maxBytes == null || user == null) {
        this
    } else {
        val cut = user.newBuilder().also { it.long_name = user.long_name.utf8Prefix(maxBytes) }.build()
        newBuilder().also { it.user = cut }.build()
    }
}

/** The longest prefix of whole characters that is at most [maxBytes] of UTF-8, so an emoji is never split. */
internal fun String.utf8Prefix(maxBytes: Int): String {
    var bytes = 0
    return codePointList()
        .takeWhile { point ->
            bytes += listOf(point).codePointsToString().encodeToByteArray().size
            bytes <= maxBytes
        }
        .codePointsToString()
}
