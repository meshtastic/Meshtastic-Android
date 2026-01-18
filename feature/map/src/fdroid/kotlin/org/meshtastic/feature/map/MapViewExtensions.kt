/*
 * Copyright (c) 2025-2026 Meshtastic LLC
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
package org.meshtastic.feature.map

import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import org.meshtastic.core.ui.R
import org.meshtastic.proto.Position
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.CopyrightOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.ScaleBarOverlay
import org.osmdroid.views.overlay.advancedpolyline.MonochromaticPaintList
import org.osmdroid.views.overlay.gridlines.LatLonGridlineOverlay2

/** Adds copyright to map depending on what source is showing */
fun MapView.addCopyright() {
    if (overlays.none { it is CopyrightOverlay }) {
        val copyrightNotice: String = tileProvider.tileSource.copyrightNotice ?: return
        val copyrightOverlay = CopyrightOverlay(context)
        copyrightOverlay.setCopyrightNotice(copyrightNotice)
        overlays.add(copyrightOverlay)
    }
}

/**
 * Create LatLong Grid line overlay
 *
 * @param enabled: turn on/off gridlines
 */
fun MapView.createLatLongGrid(enabled: Boolean) {
    val latLongGridOverlay = LatLonGridlineOverlay2()
    latLongGridOverlay.isEnabled = enabled
    if (latLongGridOverlay.isEnabled) {
        val textPaint =
            Paint().apply {
                textSize = 40f
                color = Color.GRAY
                isAntiAlias = true
                isFakeBoldText = true
                textAlign = Paint.Align.CENTER
            }
        latLongGridOverlay.textPaint = textPaint
        latLongGridOverlay.setBackgroundColor(Color.TRANSPARENT)
        latLongGridOverlay.setLineWidth(3.0f)
        latLongGridOverlay.setLineColor(Color.GRAY)
        overlays.add(latLongGridOverlay)
    }
}

fun MapView.addScaleBarOverlay(density: Density) {
    if (overlays.none { it is ScaleBarOverlay }) {
        val scaleBarOverlay =
            ScaleBarOverlay(this).apply {
                setAlignBottom(true)
                with(density) {
                    setScaleBarOffset(15.dp.toPx().toInt(), 40.dp.toPx().toInt())
                    setTextSize(12.sp.toPx())
                }
                textPaint.apply {
                    isAntiAlias = true
                    typeface = Typeface.DEFAULT_BOLD
                }
            }
        overlays.add(scaleBarOverlay)
    }
}

fun MapView.addPolyline(density: Density, geoPoints: List<GeoPoint>, onClick: () -> Unit): Polyline {
    val polyline =
        Polyline(this).apply {
            val borderPaint =
                Paint().apply {
                    color = Color.BLACK
                    isAntiAlias = true
                    strokeWidth = with(density) { 10.dp.toPx() }
                    style = Paint.Style.STROKE
                    strokeJoin = Paint.Join.ROUND
                    strokeCap = Paint.Cap.ROUND
                    pathEffect = DashPathEffect(floatArrayOf(80f, 60f), 0f)
                }
            outlinePaintLists.add(MonochromaticPaintList(borderPaint))
            val fillPaint =
                Paint().apply {
                    color = Color.WHITE
                    isAntiAlias = true
                    strokeWidth = with(density) { 6.dp.toPx() }
                    style = Paint.Style.FILL_AND_STROKE
                    strokeJoin = Paint.Join.ROUND
                    strokeCap = Paint.Cap.ROUND
                    pathEffect = DashPathEffect(floatArrayOf(80f, 60f), 0f)
                }
            outlinePaintLists.add(MonochromaticPaintList(fillPaint))
            setPoints(geoPoints)
            setOnClickListener { _, _, _ ->
                onClick()
                true
            }
        }
    overlays.add(polyline)

    return polyline
}

fun MapView.addPositionMarkers(positions: List<Position>, onClick: () -> Unit): List<Marker> {
    val navIcon = ContextCompat.getDrawable(context, R.drawable.ic_map_navigation_24)
    val markers =
        positions.map {
            Marker(this).apply {
                icon = navIcon
                rotation = ((it.ground_track ?: 0) * 1e-5).toFloat()
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                position = GeoPoint((it.latitude_i ?: 0) * 1e-7, (it.longitude_i ?: 0) * 1e-7)
                setOnMarkerClickListener { _, _ ->
                    onClick()
                    true
                }
            }
        }
    overlays.addAll(markers)

    return markers
}
