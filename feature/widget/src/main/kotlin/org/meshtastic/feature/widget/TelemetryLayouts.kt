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

import android.annotation.SuppressLint
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.semantics.testTag
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.widget_telemetry_next_page
import org.meshtastic.core.resources.widget_telemetry_no_readings
import org.meshtastic.core.resources.widget_telemetry_page
import org.meshtastic.core.resources.widget_telemetry_stale
import org.meshtastic.core.resources.widget_telemetry_updated
import org.meshtastic.core.ui.util.formatAgo

/** Readings on one page of the grid. */
internal const val TELEMETRY_PAGE_SIZE = 4
private const val GRID_COLUMNS = 2

internal fun telemetryPageCount(readings: Int): Int =
    maxOf(1, (readings + TELEMETRY_PAGE_SIZE - 1) / TELEMETRY_PAGE_SIZE)

@Composable
internal fun TelemetryContent(
    state: TelemetryWidgetState.Showing,
    storedPage: Int,
    modifier: GlanceModifier = GlanceModifier,
) {
    val size = LocalSize.current
    val oneRow = WidgetSizes.isOneRow(size)
    val layout = telemetryLayout(size)
    val tiles = state.readings.map { it to telemetryTile(it, state) }
    // Four from four cells wide; below that, four tiles in a row show only their icons.
    val rowTiles = if (size.width >= WidgetSizes.TELEMETRY_WIDE_ROW.width) TELEMETRY_PAGE_SIZE else 2
    // The body sits directly in this column, weighted: Glance's unit-test harness caps nesting at ten levels.
    Column(modifier = modifier) {
        if (layout == TelemetryLayout.GRID || (layout == TelemetryLayout.ONE && !oneRow)) {
            WidgetNodeChip(shortName = state.shortName, colors = state.colors)
            Spacer(GlanceModifier.height(6.dp))
        }
        val body = GlanceModifier.fillMaxWidth().defaultWeight()
        when {
            tiles.isEmpty() ->
                Box(modifier = body) {
                    StatusBody(
                        stringResource(Res.string.widget_telemetry_no_readings),
                        connecting = false,
                        oneRow = oneRow,
                    )
                }

            layout == TelemetryLayout.ONE -> TileView(tiles.first().second, compact = oneRow, body)

            layout == TelemetryLayout.ROW ->
                Row(modifier = body) {
                    tiles.take(rowTiles).forEachIndexed { index, (_, tile) ->
                        if (index > 0) Spacer(GlanceModifier.width(TILE_GAP))
                        TileView(tile, compact = oneRow, GlanceModifier.defaultWeight().fillMaxHeight())
                    }
                }

            else -> Grid(tiles.map { it.second }, page(storedPage, tiles.size), body)
        }
        Spacer(GlanceModifier.height(4.dp))
        Footer(
            state = state,
            page = page(storedPage, tiles.size),
            pageCount = if (layout == TelemetryLayout.GRID) telemetryPageCount(tiles.size) else 1,
            showChip = layout == TelemetryLayout.ROW && !oneRow,
            short = layout == TelemetryLayout.ONE,
        )
    }
}

/**
 * The stored page wrapped to the pages there are now, so hiding readings never leaves the widget past its last page.
 */
private fun page(storedPage: Int, readings: Int): Int = Math.floorMod(storedPage, telemetryPageCount(readings))

/** Four tiles a page; a short last page keeps empty cells, so its tiles stay the size of a full page's. */
@Composable
private fun Grid(tiles: List<TelemetryTile>, page: Int, modifier: GlanceModifier = GlanceModifier) {
    val onPage = tiles.drop(page * TELEMETRY_PAGE_SIZE).take(TELEMETRY_PAGE_SIZE)
    Column(modifier = modifier) {
        repeat(TELEMETRY_PAGE_SIZE / GRID_COLUMNS) { row ->
            if (row > 0) Spacer(GlanceModifier.height(TILE_GAP))
            Row(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
                repeat(GRID_COLUMNS) { column ->
                    if (column > 0) Spacer(GlanceModifier.width(TILE_GAP))
                    val tile = onPage.getOrNull(row * GRID_COLUMNS + column)
                    if (tile == null) {
                        Spacer(GlanceModifier.defaultWeight().fillMaxHeight())
                    } else {
                        TileView(tile, compact = false, GlanceModifier.defaultWeight().fillMaxHeight())
                    }
                }
            }
        }
    }
}

/**
 * One reading as node details shows it: icon and name, the value in its unit, and the tile's own line. A reading the
 * node has stopped sending shows [MISSING_VALUE]. [compact] (a one-row cell) keeps the icon and value only.
 */
@SuppressLint("RestrictedApi")
@Composable
private fun TileView(tile: TelemetryTile, compact: Boolean, modifier: GlanceModifier = GlanceModifier) {
    val value = tile.value ?: MISSING_VALUE
    Column(
        modifier =
        modifier
            .background(GlanceTheme.colors.secondaryContainer)
            .cornerRadius(12.dp)
            .padding(horizontal = 6.dp, vertical = if (compact) 2.dp else 6.dp)
            .semantics {
                contentDescription = listOfNotNull("${tile.label} $value", tile.detail).joinToString(", ")
                testTag = TelemetryWidget.TILE_TEST_TAG
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TileIcon(tile)
            if (compact) {
                Spacer(GlanceModifier.width(4.dp))
                TileValue(value, WidgetSizes.Type.value)
            } else {
                Spacer(GlanceModifier.width(4.dp))
                Text(
                    text = tile.label,
                    maxLines = 1,
                    style =
                    TextStyle(
                        color = GlanceTheme.colors.onSecondaryContainer,
                        fontSize = WidgetSizes.Type.footnote,
                    ),
                )
            }
        }
        if (!compact) {
            // A narrow tile clips "1,016 hPa" at the value's size, so the unit moves to the smaller second line.
            val (number, unit) = splitUnit(value)
            TileValue(number, TILE_VALUE_SIZE)
            if (unit != null || tile.detail != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    unit?.let { SecondaryText(if (tile.detail != null) "$it · " else it) }
                    tile.band?.let { band ->
                        Text(
                            text = "●",
                            style = TextStyle(color = ColorProvider(band), fontSize = WidgetSizes.Type.footnote),
                        )
                        Spacer(GlanceModifier.width(3.dp))
                    }
                    tile.detail?.let { SecondaryText(it) }
                }
            }
        }
    }
}

@Composable
private fun SecondaryText(text: String) {
    Text(
        text = text,
        maxLines = 1,
        style = TextStyle(color = GlanceTheme.colors.onSecondaryContainer, fontSize = WidgetSizes.Type.footnote),
    )
}

@Composable
private fun TileIcon(tile: TelemetryTile) {
    Image(
        provider = ImageProvider(tile.icon),
        contentDescription = null,
        modifier = GlanceModifier.size(14.dp),
        colorFilter = ColorFilter.tint(GlanceTheme.colors.onSecondaryContainer),
    )
}

@Composable
private fun TileValue(value: String, fontSize: TextUnit) {
    Text(
        text = value,
        maxLines = 1,
        style =
        TextStyle(
            color = GlanceTheme.colors.onSecondaryContainer,
            fontSize = fontSize,
            fontWeight = FontWeight.Medium,
        ),
    )
}

/**
 * The age of the newest reading, and the page button when there is more than one page. Once the newest reading is over
 * [STALE_AFTER] old the line says so in the error color; the tiles stay at full strength (meshtastic/design#179).
 */
@Composable
private fun Footer(state: TelemetryWidgetState.Showing, page: Int, pageCount: Int, showChip: Boolean, short: Boolean) {
    val age =
        when {
            state.isStale -> stringResource(Res.string.widget_telemetry_stale)

            // One tile wide leaves no room for the prefix.
            state.updatedAt != null && short -> formatAgo(state.updatedAt)

            state.updatedAt != null -> stringResource(Res.string.widget_telemetry_updated, formatAgo(state.updatedAt))

            else -> null
        }
    if (age == null && pageCount <= 1 && !showChip) return
    Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (showChip) {
            WidgetNodeChip(shortName = state.shortName, colors = state.colors)
            Spacer(GlanceModifier.width(8.dp))
        }
        Text(
            text = age.orEmpty(),
            maxLines = 1,
            style =
            TextStyle(
                color = if (state.isStale) GlanceTheme.colors.error else GlanceTheme.colors.onSurfaceVariant,
                fontSize = WidgetSizes.Type.footnote,
                fontWeight = if (state.isStale) FontWeight.Medium else FontWeight.Normal,
            ),
            modifier =
            GlanceModifier.defaultWeight().let {
                if (state.isStale) it.semantics { testTag = TelemetryWidget.STALE_TEST_TAG } else it
            },
        )
        if (pageCount > 1) PageButton(page, pageCount)
    }
}

/** Flips to the next four readings without opening the app; 48dp wide so it meets the minimum touch target. */
@Composable
private fun PageButton(page: Int, pageCount: Int) {
    val description = stringResource(Res.string.widget_telemetry_next_page, page + 1, pageCount)
    Box(
        modifier =
        GlanceModifier.width(48.dp)
            .height(32.dp)
            .cornerRadius(16.dp)
            .background(GlanceTheme.colors.primaryContainer)
            .clickable(
                actionRunCallback<TelemetryPageAction>(
                    actionParametersOf(TelemetryPageAction.PAGE_COUNT_KEY to pageCount),
                ),
            )
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(Res.string.widget_telemetry_page, page + 1, pageCount),
            maxLines = 1,
            style =
            TextStyle(
                color = GlanceTheme.colors.onPrimaryContainer,
                fontSize = WidgetSizes.Type.label,
                fontWeight = FontWeight.Medium,
            ),
        )
    }
}

private val TILE_GAP = 6.dp
private val TILE_VALUE_SIZE = 18.sp

internal enum class TelemetryLayout {
    ONE,
    ROW,
    GRID,
}

/** Which of the three layouts a breakpoint gets, by the thresholds in [WidgetSizes.telemetrySizeMode]. */
internal fun telemetryLayout(size: DpSize): TelemetryLayout = when {
    size.width < WidgetSizes.TELEMETRY_ROW.width -> TelemetryLayout.ONE
    size.height < WidgetSizes.TELEMETRY_GRID.height -> TelemetryLayout.ROW
    else -> TelemetryLayout.GRID
}
