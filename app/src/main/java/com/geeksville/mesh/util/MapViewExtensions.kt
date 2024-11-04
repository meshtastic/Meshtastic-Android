package com.geeksville.mesh.util

import android.graphics.Color
import android.graphics.Paint
import org.osmdroid.events.DelayedMapListener
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.CopyrightOverlay
import org.osmdroid.views.overlay.gridlines.LatLonGridlineOverlay2

/**
 * Adds copyright to map depending on what source is showing
 */
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
 * @param enabled: turn on/off gridlines
 */
fun MapView.createLatLongGrid(enabled: Boolean) {
    val latLongGridOverlay = LatLonGridlineOverlay2()
    latLongGridOverlay.isEnabled = enabled
    if (latLongGridOverlay.isEnabled) {
        val textPaint = Paint().apply {
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

private const val INACTIVITY_DELAY_MILLIS = 500L
fun MapView.addMapEventListener(onEvent: () -> Unit) {
    addMapListener(DelayedMapListener(object : MapListener {
        override fun onScroll(event: ScrollEvent): Boolean {
            onEvent()
            return true
        }

        override fun onZoom(event: ZoomEvent): Boolean {
            onEvent()
            return true
        }
    }, INACTIVITY_DELAY_MILLIS))
}
