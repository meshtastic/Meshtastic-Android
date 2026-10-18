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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Row
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider

private const val DIMMED_ALPHA = 0.55f

/** The app's node chip, short name on the node's own colors, for a Glance tree. */
@SuppressLint("RestrictedApi")
@Composable
internal fun WidgetNodeChip(
    shortName: String,
    colors: Pair<Int, Int>,
    modifier: GlanceModifier = GlanceModifier,
    dimmed: Boolean = false,
) {
    val (fg, bg) = colors
    val alpha = if (dimmed) DIMMED_ALPHA else 1f
    Row(
        modifier =
        modifier
            .width(64.dp)
            .background(Color(bg).copy(alpha = alpha))
            .cornerRadius(4.dp)
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = shortName,
            maxLines = 1,
            style =
            TextStyle(
                color = ColorProvider(Color(fg).copy(alpha = alpha)),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
            ),
        )
    }
}
