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
package org.meshtastic.feature.widget

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.appwidget.SizeMode

/**
 * Responsive breakpoints shared by both widgets. Under [SizeMode.Responsive] `LocalSize` is the chosen breakpoint, not
 * the real bounds, and a breakpoint larger than the real bounds is never chosen, so each one sits just under the
 * smallest cell span it has to catch: one row is 51dp tall in landscape and about 95dp in portrait, two cells 130dp
 * wide in portrait.
 */
internal object WidgetSizes {
    val ROW = DpSize(130.dp, 48.dp)
    val WIDE_ROW = DpSize(200.dp, 48.dp)
    val SQUARE = DpSize(130.dp, 90.dp)
    val WIDE = DpSize(200.dp, 90.dp)
    val TALL = DpSize(130.dp, 180.dp)
    val BIG = DpSize(200.dp, 180.dp)
    val EXTRA_TALL = DpSize(130.dp, 260.dp)
    val EXTRA_BIG = DpSize(200.dp, 260.dp)

    /** The type scale both widgets share, so the same reading is the same size in either widget. */
    object Type {
        val title = 14.sp
        val compactTitle = 12.sp
        val label = 12.sp
        val value = 13.sp
        val hero = 22.sp
        val heroWord = 16.sp
        val footnote = 11.sp
    }

    val responsive = SizeMode.Responsive(setOf(ROW, WIDE_ROW, SQUARE, WIDE, TALL, BIG, EXTRA_TALL, EXTRA_BIG))
}
