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

package org.meshtastic.feature.map.maplibre.utils

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.core.graphics.createBitmap
import timber.log.Timber

/** Convert a Unicode code point (int) to an emoji string */
internal fun convertIntToEmoji(unicodeCodePoint: Int): String = try {
    String(Character.toChars(unicodeCodePoint))
} catch (e: IllegalArgumentException) {
    Timber.tag("MapLibrePOC").w(e, "Invalid unicode code point: $unicodeCodePoint")
    "\uD83D\uDCCD" // 📍 default pin emoji
}

/** Convert emoji to Bitmap for use as a MapLibre marker icon */
internal fun unicodeEmojiToBitmap(icon: Int): Bitmap {
    val unicodeEmoji = convertIntToEmoji(icon)
    val paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 64f
            color = android.graphics.Color.BLACK
            textAlign = Paint.Align.CENTER
        }

    val baseline = -paint.ascent()
    val width = (paint.measureText(unicodeEmoji) + 0.5f).toInt()
    val height = (baseline + paint.descent() + 0.5f).toInt()
    val image = createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(image)
    canvas.drawText(unicodeEmoji, width / 2f, baseline, paint)

    return image
}
