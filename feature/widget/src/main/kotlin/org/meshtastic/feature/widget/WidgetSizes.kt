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

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.LocalContext
import androidx.glance.LocalGlanceId
import androidx.glance.LocalSize
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.LocalAppWidgetOptions
import androidx.glance.appwidget.PreviewSizeMode
import androidx.glance.appwidget.SizeMode

/**
 * True inside `providePreview`. Glance composes the picker preview without a [LocalGlanceId], whose default throws, so
 * nothing under a preview may read it.
 */
@Suppress("CompositionLocalAllowlist")
internal val LocalWidgetPreview = staticCompositionLocalOf { false }

/**
 * How both widgets read their size. They compose under [SizeMode.Responsive], so the launcher picks the layout by the
 * breakpoint that fits the cell, which every launcher does reliably; [cell] then reads the cell's real bounds from the
 * widget options so the layout can fill the height it has, instead of leaving the difference between breakpoint and
 * cell blank. [SizeMode.Exact] would hand over the real bounds directly, but composes only for the sizes the launcher
 * reports, and a launcher that reports stale ones after a resize then shows a layout meant for another cell. The
 * thresholds sit just under the smallest cell span they catch: one row is 51dp tall in landscape and about 95dp in
 * portrait, two cells 130dp wide in portrait, four cells 340dp on a 4-column launcher.
 */
internal object WidgetSizes {
    private val NARROW_WIDTH = 130.dp
    private val WIDE_WIDTH = 200.dp
    private val TWO_COLUMN_WIDTH = 320.dp
    private val ONE_ROW_HEIGHT = 48.dp
    private val COMPACT_HEIGHT = 90.dp
    private val TWO_ROWS_HEIGHT = 180.dp
    private val THREE_ROWS_HEIGHT = 260.dp
    private val FOUR_ROWS_HEIGHT = 350.dp

    // Ascending, so each breakpoint knows the next: a composition for one is only ever shown under the next.
    private val WIDTHS = listOf(NARROW_WIDTH, WIDE_WIDTH, TWO_COLUMN_WIDTH)
    private val HEIGHTS = listOf(ONE_ROW_HEIGHT, COMPACT_HEIGHT, TWO_ROWS_HEIGHT, THREE_ROWS_HEIGHT, FOUR_ROWS_HEIGHT)

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

    /** Every width at every height: a missing combination would route a cell to a tier that wastes one dimension. */
    val sizeMode: SizeMode =
        SizeMode.Responsive(buildSet { for (width in WIDTHS) for (height in HEIGHTS) add(DpSize(width, height)) })

    /** The picker preview has no cell to read, so it gets the breakpoints plus two measured 2x2 cells. */
    val previewSizeMode: PreviewSizeMode =
        SizeMode.Responsive(setOf(ROW, SQUARE, TALL, PIXEL_2X2, EMULATOR_2X2, BIG, TWO_COLUMNS))

    fun isOneRow(size: DpSize): Boolean = size.height < COMPACT_HEIGHT

    fun isCompact(size: DpSize): Boolean = size.height < TWO_ROWS_HEIGHT

    fun isWide(size: DpSize): Boolean = size.width >= WIDE_WIDTH

    /** Three rows and up, where the last-updated footnote earns its line. */
    fun isExtraTall(size: DpSize): Boolean = size.height >= THREE_ROWS_HEIGHT

    /**
     * The real bounds this composition is shown at: the cell the launcher reports for the current orientation when the
     * current breakpoint fits in it (the launcher only shows a breakpoint where it fits), else the other orientation's
     * when it fits there, else the breakpoint itself, so a stale or missing option costs slack, never a layout meant
     * for another cell. Capped at the next breakpoint in each direction, from which the launcher shows that one
     * instead: between a resize and the recomposition it triggers, the launcher shows the old composition's layout for
     * the new size, and the cap keeps that layout counted for a cell of its own tier.
     */
    @Composable
    @ReadOnlyComposable
    fun cell(): DpSize {
        val breakpoint = LocalSize.current
        val context = LocalContext.current
        val landscape = context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val reported = currentOptions(context).reportedCells()
        val preferred = if (landscape) reported.landscape else reported.portrait
        val other = if (landscape) reported.portrait else reported.landscape
        val cell = preferred.takeIf { breakpoint fitsIn it } ?: other.takeIf { breakpoint fitsIn it } ?: breakpoint
        return DpSize(
            cell.width.coerceAtMost(WIDTHS.nextAbove(breakpoint.width)),
            cell.height.coerceAtMost(HEIGHTS.nextAbove(breakpoint.height)),
        )
    }

    /**
     * The widget's options as the launcher last wrote them, read from the system on every composition. Glance's
     * [LocalAppWidgetOptions] is the copy its session took, which it refreshes only when the launcher's options
     * broadcast arrives, and a launcher that resizes without one leaves it stale until the widget is re-added; a
     * composition with no app widget id or no options for it (the picker preview, the test harness) uses that copy.
     */
    @Composable
    @ReadOnlyComposable
    private fun currentOptions(context: Context): Bundle {
        val appWidgetId =
            if (LocalWidgetPreview.current) {
                null
            } else {
                val glanceId = LocalGlanceId.current
                runCatching { GlanceAppWidgetManager(context).getAppWidgetId(glanceId) }.getOrNull()
            }
        return appWidgetId
            ?.let { AppWidgetManager.getInstance(context).getAppWidgetOptions(it) }
            ?.takeIf { !it.isEmpty } ?: LocalAppWidgetOptions.current
    }

    private infix fun DpSize.fitsIn(cell: DpSize?): Boolean =
        cell != null && width <= cell.width && height <= cell.height

    /** The breakpoint after [value], or [Dp.Infinity] past the last. */
    private fun List<Dp>.nextAbove(value: Dp): Dp = firstOrNull { it > value } ?: Dp.Infinity

    /**
     * How many reading columns the cell holds: two from four cells up, where a label and value pair fits in each. The
     * width counts in font-scaled dp, since the labels grow with the font and a column too narrow for them clips.
     */
    @Composable @ReadOnlyComposable
    fun columns(): Int = if (cell().width / fontScale() >= TWO_COLUMN_WIDTH) 2 else 1

    /**
     * How many [StatRow] lines fit in the cell under [fixed] chrome (headers, gaps, padding) and [text] chrome (rows
     * that grow with the font). The text chrome and the lines scale with the font, the fixed chrome does not, so a
     * large font counts fewer lines rather than clipping the last or, scaled wholesale, none. The heights in [Heights]
     * sit on the high side: a line left over is a gap, a line counted but clipped is a lost reading.
     */
    @Composable
    @ReadOnlyComposable
    fun lines(fixed: Dp, text: Dp): Int {
        val fontScale = fontScale()
        val available = cell().height - fixed - text * fontScale
        return (available / (Heights.line * fontScale)).toInt().coerceAtLeast(0)
    }

    @Composable
    @ReadOnlyComposable
    private fun fontScale(): Float = LocalContext.current.resources.configuration.fontScale

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

/** The cell in each orientation as the launcher last reported it; null until it has reported one. */
private data class ReportedCells(val portrait: DpSize?, val landscape: DpSize?)

/**
 * What every launcher puts in the widget options: the portrait cell as min width by max height and the landscape cell
 * as max width by min height.
 */
private fun Bundle.reportedCells(): ReportedCells {
    val minWidth = getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)
    val maxWidth = getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 0)
    val minHeight = getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0)
    val maxHeight = getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0)
    return ReportedCells(
        portrait = if (minWidth > 0 && maxHeight > 0) DpSize(minWidth.dp, maxHeight.dp) else null,
        landscape = if (maxWidth > 0 && minHeight > 0) DpSize(maxWidth.dp, minHeight.dp) else null,
    )
}
