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
package org.meshtastic.core.service

import android.graphics.Canvas
import android.graphics.Paint
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.IconCompat

/**
 * Renders avatar icons for [androidx.core.app.Person] icons in MessagingStyle notifications and for conversation
 * shortcuts. Two shapes give conversations a recognizable identity at a glance:
 * - a **wide pill** for people / direct messages (holds the node initial or short name), mirroring the in-app node
 *   chip, and
 * - a **rounded square** for channels (holds the channel number),
 *
 * both filled with the caller-supplied colors (node colors for people, a channel-derived color for channels).
 */
internal object PersonIconFactory {

    private const val ICON_SIZE = 128
    private const val TEXT_SIZE_RATIO = 0.5f

    // Leave a margin so multi-character labels (e.g. a 4-char node short name) don't touch the edge.
    private const val MAX_TEXT_WIDTH_RATIO = 0.72f
    private const val CORNER_RADIUS_RATIO = 0.28f

    // Height of the people pill relative to the square canvas; the rest is transparent top/bottom margin so the pill
    // reads as a wide chip like the in-app node chip.
    private const val PILL_HEIGHT_RATIO = 0.56f

    // Corner radius of the people pill as a fraction of its height. Less than 0.5 (a full stadium) so it reads as a
    // rounded-rectangle chip like the M3 AssistChip, not a lozenge.
    private const val PILL_CORNER_RATIO = 0.3f

    /** Wide-pill avatar with a single uppercase initial taken from [name]. */
    fun create(name: String, backgroundColor: Int, foregroundColor: Int): IconCompat =
        render(firstInitial(name), backgroundColor, foregroundColor, rounded = false)

    /**
     * Avatar showing a short [label] verbatim (e.g. a node's 4-character short name, or a channel number), auto-sized
     * to fit. [rounded] draws a rounded square (channels) instead of the people pill.
     */
    fun createLabel(label: String, backgroundColor: Int, foregroundColor: Int, rounded: Boolean): IconCompat =
        render(label.ifBlank { "?" }, backgroundColor, foregroundColor, rounded)

    private fun firstInitial(name: String): String =
        if (name.isEmpty()) "?" else String(Character.toChars(name.codePointAt(0))).uppercase()

    private fun render(text: String, backgroundColor: Int, foregroundColor: Int, rounded: Boolean): IconCompat {
        val bitmap = createBitmap(ICON_SIZE, ICON_SIZE)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val size = ICON_SIZE.toFloat()

        paint.color = backgroundColor
        if (rounded) {
            val radius = ICON_SIZE * CORNER_RADIUS_RATIO
            canvas.drawRoundRect(0f, 0f, size, size, radius, radius, paint)
        } else {
            // Wide rounded-rectangle chip spanning the full width, vertically centered — matches the in-app node chip.
            val pillHeight = size * PILL_HEIGHT_RATIO
            val top = (size - pillHeight) / 2f
            val cap = pillHeight * PILL_CORNER_RATIO
            canvas.drawRoundRect(0f, top, size, size - top, cap, cap, paint)
        }

        paint.color = foregroundColor
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = ICON_SIZE * TEXT_SIZE_RATIO
        // Shrink the text if it would overflow the icon (keeps 4-char short names inside the circle).
        val measured = paint.measureText(text)
        val maxWidth = ICON_SIZE * MAX_TEXT_WIDTH_RATIO
        if (measured > maxWidth) paint.textSize *= maxWidth / measured

        val xPos = canvas.width / 2f
        val yPos = canvas.height / 2f - (paint.descent() + paint.ascent()) / 2f
        canvas.drawText(text, xPos, yPos, paint)

        return IconCompat.createWithBitmap(bitmap)
    }
}
