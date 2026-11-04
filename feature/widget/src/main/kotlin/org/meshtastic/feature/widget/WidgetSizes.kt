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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.appwidget.PreviewSizeMode
import androidx.glance.appwidget.SizeMode

/**
 * How both widgets read their size. They compose under [SizeMode.Exact], so `LocalSize` is the cell's real bounds and
 * each layout counts the reading lines its height holds instead of fitting a breakpoint and leaving the difference
 * blank. The thresholds sit just under the smallest cell span they catch: one row is 51dp tall in landscape and about
 * 95dp in portrait, two cells 130dp wide in portrait, four cells 340dp on a 4-column launcher. The named sizes are the
 * tiers' corners, for tests and the picker previews, which cannot take [SizeMode.Exact].
 */
internal object WidgetSizes {
    private val NARROW_WIDTH = 130.dp
    private val WIDE_WIDTH = 200.dp
    private val TWO_COLUMN_WIDTH = 300.dp
    private val ONE_ROW_HEIGHT = 48.dp
    private val COMPACT_HEIGHT = 90.dp
    private val TWO_ROWS_HEIGHT = 180.dp
    private val THREE_ROWS_HEIGHT = 260.dp

    val ROW = DpSize(NARROW_WIDTH, ONE_ROW_HEIGHT)
    val WIDE_ROW = DpSize(WIDE_WIDTH, ONE_ROW_HEIGHT)
    val SQUARE = DpSize(NARROW_WIDTH, COMPACT_HEIGHT)
    val WIDE = DpSize(WIDE_WIDTH, COMPACT_HEIGHT)
    val TALL = DpSize(NARROW_WIDTH, TWO_ROWS_HEIGHT)
    val BIG = DpSize(WIDE_WIDTH, TWO_ROWS_HEIGHT)
    val TWO_COLUMNS = DpSize(TWO_COLUMN_WIDTH, TWO_ROWS_HEIGHT)
    val EXTRA_TALL = DpSize(NARROW_WIDTH, THREE_ROWS_HEIGHT)
    val EXTRA_BIG = DpSize(WIDE_WIDTH, THREE_ROWS_HEIGHT)
    val EXTRA_TWO_COLUMNS = DpSize(TWO_COLUMN_WIDTH, THREE_ROWS_HEIGHT)

    /** A 2x2 cell on a Pixel 6a and on the API 36 emulator's Pixel launcher, so the picker preview fills its frame. */
    private val PIXEL_2X2 = DpSize(179.dp, 202.dp)
    private val EMULATOR_2X2 = DpSize(172.dp, 234.dp)

    val sizeMode: SizeMode = SizeMode.Exact
    val previewSizeMode: PreviewSizeMode =
        SizeMode.Responsive(setOf(ROW, SQUARE, TALL, PIXEL_2X2, EMULATOR_2X2, BIG, TWO_COLUMNS))

    fun isOneRow(size: DpSize): Boolean = size.height < COMPACT_HEIGHT

    fun isCompact(size: DpSize): Boolean = size.height < TWO_ROWS_HEIGHT

    fun isWide(size: DpSize): Boolean = size.width >= WIDE_WIDTH

    /** Three rows and up, where the last-updated footnote earns its line. */
    fun isExtraTall(size: DpSize): Boolean = size.height >= THREE_ROWS_HEIGHT

    /** How many reading columns a width holds: two from four cells up, where a label and value pair fits in each. */
    fun columns(size: DpSize): Int = if (size.width >= TWO_COLUMN_WIDTH) 2 else 1

    /**
     * How many [StatRow] lines fit in the current height under [fixed] chrome (headers, gaps, padding) and [text]
     * chrome (rows that grow with the font). The text chrome and the lines scale with the font, the fixed chrome does
     * not, so a large font counts fewer lines rather than clipping the last or, scaled wholesale, none. The heights in
     * [Heights] sit on the high side: a line left over is a gap, a line counted but clipped is a lost reading.
     */
    @Composable
    @ReadOnlyComposable
    fun lines(fixed: Dp, text: Dp): Int {
        val fontScale = LocalContext.current.resources.configuration.fontScale
        val available = LocalSize.current.height - fixed - text * fontScale
        return (available / (Heights.line * fontScale)).toInt().coerceAtLeast(0)
    }

    /** The space each piece of a tall layout takes at font scale 1, for [lines]. */
    object Heights {
        val header = 32.dp
        val actionHeader = 48.dp
        val gap = 4.dp
        val bottom = 4.dp
        val identity = 32.dp
        val bar = 27.dp
        val name = 18.dp
        val line = 22.dp
        val footnote = 18.dp
    }

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
}
