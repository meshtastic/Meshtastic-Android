package com.geeksville.mesh.model.map

import android.view.MotionEvent
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.DefaultOverlayManager
import org.osmdroid.views.overlay.TilesOverlay

/**
 * CustomOverlayManager with disabled double taps events
 */
class CustomOverlayManager(tilesOverlay: TilesOverlay?) : DefaultOverlayManager(tilesOverlay) {
    /**
     * Override event & do nothing
     */
    override fun onDoubleTap(e: MotionEvent?, pMapView: MapView?): Boolean {
        return true
    }

    /**
     * Override event & do nothing
     */
    override fun onDoubleTapEvent(e: MotionEvent?, pMapView: MapView?): Boolean {
        return true
    }
}