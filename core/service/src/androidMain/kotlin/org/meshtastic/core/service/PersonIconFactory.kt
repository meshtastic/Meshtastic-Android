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
 * Renders a circular avatar with a single uppercase initial — used for [androidx.core.app.Person] icons in
 * MessagingStyle notifications and for conversation shortcut avatars.
 *
 * Shared by [MeshServiceNotificationsImpl] and [ConversationShortcutManager] to keep the avatar appearance consistent
 * across the notification shade and the launcher / Android Auto.
 */
internal object PersonIconFactory {

    private const val ICON_SIZE = 128
    private const val TEXT_SIZE_RATIO = 0.5f

    fun create(name: String, backgroundColor: Int, foregroundColor: Int): IconCompat {
        val bitmap = createBitmap(ICON_SIZE, ICON_SIZE)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Background circle.
        paint.color = backgroundColor
        canvas.drawCircle(ICON_SIZE / 2f, ICON_SIZE / 2f, ICON_SIZE / 2f, paint)

        // Single uppercase initial centered on the circle.
        paint.color = foregroundColor
        paint.textSize = ICON_SIZE * TEXT_SIZE_RATIO
        paint.textAlign = Paint.Align.CENTER
        val initial =
            if (name.isNotEmpty()) {
                val codePoint = name.codePointAt(0)
                String(Character.toChars(codePoint)).uppercase()
            } else {
                "?"
            }
        val xPos = canvas.width / 2f
        val yPos = (canvas.height / 2f - (paint.descent() + paint.ascent()) / 2f)
        canvas.drawText(initial, xPos, yPos, paint)

        return IconCompat.createWithBitmap(bitmap)
    }
}
