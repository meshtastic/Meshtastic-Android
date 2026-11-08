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
package org.meshtastic.app.map.offline.pmtiles

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.rememberUpdatedMarkerState
import java.util.Locale
import kotlin.math.ceil

/** How a kind of label is set: size in sp, whether it is italic or bold, and whether it is spaced capitals. */
internal data class LabelStyle(
    val sizeSp: Float,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val uppercase: Boolean = false,
    val letterSpacingEm: Float = 0f,
)

internal fun labelStyle(kind: OfflineMapLabel.Kind): LabelStyle = when (kind) {
    OfflineMapLabel.Kind.Region -> LabelStyle(sizeSp = 13f, uppercase = true, letterSpacingEm = 0.15f)
    OfflineMapLabel.Kind.City -> LabelStyle(sizeSp = 15f, bold = true)
    OfflineMapLabel.Kind.Town -> LabelStyle(sizeSp = 13f, bold = true)
    OfflineMapLabel.Kind.Village -> LabelStyle(sizeSp = 11.5f)
    OfflineMapLabel.Kind.Suburb -> LabelStyle(sizeSp = 11f, uppercase = true, letterSpacingEm = 0.1f)
    OfflineMapLabel.Kind.Neighborhood -> LabelStyle(sizeSp = 10f, uppercase = true, letterSpacingEm = 0.1f)
    OfflineMapLabel.Kind.Water -> LabelStyle(sizeSp = 12f, italic = true, letterSpacingEm = 0.04f)
}

private val WATER_TEXT = Color(0xFF_386BA8)
private val WATER_TEXT_DARK = Color(0xFF_8CB3E6)
private val MUTED_TEXT = Color(0xFF_6B6B6B)
private val MUTED_TEXT_DARK = Color(0xFF_A8A8A8)
private val TEXT = Color(0xFF_2E2E2E)
private val TEXT_DARK = Color(0xFF_E6E6E6)
private val HALO = Color(0xE6_FFFFFF)
private val HALO_DARK = Color(0xE6_141414)

private fun labelTextColor(kind: OfflineMapLabel.Kind, dark: Boolean): Int = when (kind) {
    OfflineMapLabel.Kind.Water -> if (dark) WATER_TEXT_DARK else WATER_TEXT

    OfflineMapLabel.Kind.Suburb,
    OfflineMapLabel.Kind.Neighborhood,
    OfflineMapLabel.Kind.Region,
    -> if (dark) MUTED_TEXT_DARK else MUTED_TEXT

    else -> if (dark) TEXT_DARK else TEXT
}.toArgb()

private data class LabelKey(val name: String, val kind: OfflineMapLabel.Kind, val dark: Boolean)

private val CENTER = Offset(0.5f, 0.5f)
private const val HALO_WIDTH_DP = 3f
private const val MAX_CACHED_ICONS = 256

/** Renders label text with a halo to a marker icon, remembering each (name, kind, theme) so a pan doesn't redraw. */
internal class LabelIconCache(private val density: Float, private val scaledDensity: Float) {
    private val icons =
        object : LinkedHashMap<LabelKey, BitmapDescriptor>(MAX_CACHED_ICONS, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<LabelKey, BitmapDescriptor>) =
                size > MAX_CACHED_ICONS
        }

    fun icon(label: OfflineMapLabel, dark: Boolean): BitmapDescriptor =
        icons.getOrPut(LabelKey(label.name, label.kind, dark)) {
            BitmapDescriptorFactory.fromBitmap(render(label, dark))
        }

    private fun render(label: OfflineMapLabel, dark: Boolean): Bitmap {
        val style = labelStyle(label.kind)
        val text = if (style.uppercase) label.name.uppercase(Locale.getDefault()) else label.name
        val fill =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = style.sizeSp * scaledDensity
                letterSpacing = style.letterSpacingEm
                typeface =
                    Typeface.create(
                        Typeface.DEFAULT,
                        when {
                            style.bold -> Typeface.BOLD
                            style.italic -> Typeface.ITALIC
                            else -> Typeface.NORMAL
                        },
                    )
                color = labelTextColor(label.kind, dark)
            }
        val halo =
            Paint(fill).apply {
                this.style = Paint.Style.STROKE
                strokeWidth = HALO_WIDTH_DP * density
                strokeJoin = Paint.Join.ROUND
                color = (if (dark) HALO_DARK else HALO).toArgb()
            }
        val pad = ceil(halo.strokeWidth).toInt()
        val metrics = fill.fontMetrics
        val width = ceil(fill.measureText(text)).toInt() + 2 * pad
        val height = ceil(metrics.descent - metrics.ascent).toInt() + 2 * pad
        return Bitmap.createBitmap(width.coerceAtLeast(1), height.coerceAtLeast(1), Bitmap.Config.ARGB_8888).also {
            val canvas = Canvas(it)
            val baseline = pad - metrics.ascent
            canvas.drawText(text, pad.toFloat(), baseline, halo)
            canvas.drawText(text, pad.toFloat(), baseline, fill)
        }
    }
}

/**
 * One flat-off marker per label, centered on its point, below node markers. A marker is the only text primitive Google
 * Maps Compose has; [onClick] consumes taps so a label neither recenters the camera nor opens an info window.
 */
@Composable
internal fun OfflineLabelMarkers(labels: List<OfflineMapLabel>, dark: Boolean) {
    val density = LocalDensity.current
    val cache =
        remember(density.density, density.fontScale) {
            LabelIconCache(density.density, density.density * density.fontScale)
        }
    labels.forEach { label ->
        key(label.kind, label.name, label.position) {
            Marker(
                state = rememberUpdatedMarkerState(position = label.position),
                icon = cache.icon(label, dark),
                anchor = CENTER,
                flat = false,
                zIndex = Z_LABEL,
                onClick = { true },
            )
        }
    }
}

/** Above the offline shapes (-0.75f), below node markers and waypoints. */
private const val Z_LABEL = -0.5f
