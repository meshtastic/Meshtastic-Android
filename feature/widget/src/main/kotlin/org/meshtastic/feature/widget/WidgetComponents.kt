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
import androidx.compose.ui.unit.dp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.appwidget.CircularProgressIndicator
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.cornerRadius
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider

/** The Meshtastic mark, tinted to the theme so it reads on any wallpaper-derived background. */
@Composable
internal fun WidgetMark(size: Dp) {
    Image(
        provider = ImageProvider(R.drawable.widget_app_icon),
        contentDescription = null,
        modifier = GlanceModifier.size(size),
        colorFilter = ColorFilter.tint(GlanceTheme.colors.onSurface),
    )
}

/**
 * The brand header both widgets carry from two cells tall up: the mark and the widget's name, with an optional trailing
 * action. One-row sizes have no header; they lead their first row with the mark instead.
 */
@Composable
internal fun WidgetHeader(title: String, compact: Boolean, action: (@Composable () -> Unit)? = null) {
    val height =
        when {
            action != null -> 44.dp
            compact -> 24.dp
            else -> 32.dp
        }
    Row(modifier = GlanceModifier.fillMaxWidth().height(height), verticalAlignment = Alignment.CenterVertically) {
        WidgetMark(if (compact) 16.dp else 20.dp)
        Spacer(GlanceModifier.width(6.dp))
        Text(
            text = title,
            maxLines = 1,
            style =
            TextStyle(
                color = GlanceTheme.colors.onSurface,
                fontSize = if (compact) WidgetSizes.Type.compactTitle else WidgetSizes.Type.title,
                fontWeight = FontWeight.Medium,
            ),
            modifier = GlanceModifier.defaultWeight(),
        )
        action?.invoke()
    }
}

/**
 * The node chip with the battery as the widget's hero value. With a [trailing] pair the hero sits beside the chip and
 * the pair takes the right edge; without one the hero takes the right edge.
 */
@Composable
internal fun IdentityRow(
    chip: @Composable () -> Unit,
    hero: String?,
    heroIsWord: Boolean,
    isStale: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(modifier = GlanceModifier.fillMaxWidth().height(28.dp), verticalAlignment = Alignment.CenterVertically) {
        chip()
        if (trailing != null) {
            Spacer(GlanceModifier.width(8.dp))
            hero?.let { HeroText(it, heroIsWord, isStale) }
            Spacer(GlanceModifier.defaultWeight())
            trailing()
        } else {
            Spacer(GlanceModifier.defaultWeight())
            hero?.let { HeroText(it, heroIsWord, isStale) }
        }
    }
}

@Composable
private fun HeroText(text: String, isWord: Boolean, isStale: Boolean) {
    Text(
        text = text,
        maxLines = 1,
        style =
        TextStyle(
            color = valueColor(isStale),
            fontSize = if (isWord) WidgetSizes.Type.heroWord else WidgetSizes.Type.hero,
            fontWeight = FontWeight.Medium,
        ),
    )
}

/** A label on the left and its value on the right, the one reading row both widgets use. */
@Composable
internal fun StatRow(label: String, value: String, isStale: Boolean = false) {
    Row(
        modifier = GlanceModifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            maxLines = 1,
            style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = WidgetSizes.Type.label),
            modifier = GlanceModifier.defaultWeight(),
        )
        Spacer(GlanceModifier.width(4.dp))
        ValueText(value, isStale)
    }
}

/** A [StatRow] over a 0..1 bar, for utilization. */
@Composable
internal fun BarRow(label: String, value: String, progress: Float, modifier: GlanceModifier = GlanceModifier) {
    Column(modifier = modifier) {
        StatRow(label, value)
        Spacer(GlanceModifier.height(2.dp))
        LinearProgressIndicator(
            progress = progress,
            modifier = GlanceModifier.fillMaxWidth().height(4.dp).cornerRadius(2.dp),
            color = GlanceTheme.colors.primary,
            backgroundColor = GlanceTheme.colors.surfaceVariant,
        )
    }
}

/**
 * An inline label and value that size to their text, for rows that hold two readings; [label] null shows the value
 * alone.
 */
@Composable
internal fun PairText(label: String?, value: String, isStale: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (label != null) {
            Text(
                text = label,
                maxLines = 1,
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = WidgetSizes.Type.label),
            )
            Spacer(GlanceModifier.width(4.dp))
        }
        ValueText(value, isStale)
    }
}

@Composable
internal fun ValueText(value: String, isStale: Boolean = false) {
    Text(
        text = value,
        maxLines = 1,
        style =
        TextStyle(color = valueColor(isStale), fontSize = WidgetSizes.Type.value, fontWeight = FontWeight.Medium),
    )
}

/** A centered line of small print, such as when the readings were last updated. */
@Composable
internal fun Footnote(text: String) {
    Text(
        text = text,
        maxLines = 1,
        style =
        TextStyle(
            color = GlanceTheme.colors.onSurfaceVariant,
            fontSize = WidgetSizes.Type.footnote,
            textAlign = TextAlign.Center,
        ),
        modifier = GlanceModifier.fillMaxWidth(),
    )
}

/**
 * The body when there is nothing to read: disconnected, connecting, sleeping or an unknown node. At one row it sits
 * beside the mark, since that size has no header.
 */
@Composable
internal fun StatusBody(text: String, connecting: Boolean, oneRow: Boolean) {
    Row(
        modifier = GlanceModifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = if (oneRow) Alignment.Start else Alignment.CenterHorizontally,
    ) {
        if (oneRow) {
            WidgetMark(16.dp)
            Spacer(GlanceModifier.width(8.dp))
        }
        if (connecting) {
            CircularProgressIndicator(modifier = GlanceModifier.size(16.dp))
            Spacer(GlanceModifier.width(8.dp))
        }
        Text(
            text = text,
            maxLines = 2,
            style =
            TextStyle(
                color = GlanceTheme.colors.onSurfaceVariant,
                fontSize = WidgetSizes.Type.value,
                fontWeight = FontWeight.Medium,
                textAlign = if (oneRow) TextAlign.Start else TextAlign.Center,
            ),
        )
    }
}

@Composable
@ReadOnlyComposable
internal fun valueColor(isStale: Boolean): ColorProvider =
    if (isStale) GlanceTheme.colors.outline else GlanceTheme.colors.onSurface
