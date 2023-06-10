package com.geeksville.mesh.model.map

import android.content.Context
import android.view.MotionEvent
import org.osmdroid.tileprovider.MapTileProviderBase
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.DefaultOverlayManager
import org.osmdroid.views.overlay.TilesOverlay

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

    companion object {
        /**
         * Use CustomOverlayManager and disable double taps events
         */
        fun disableDoubleTap(mapView: MapView, context: Context?): CustomOverlayManager {
            val mTileProvider: MapTileProviderBase = mapView.tileProvider
            val tilesOverlay = TilesOverlay(mTileProvider, context)
            mapView.overlayManager = CustomOverlayManager(tilesOverlay)
            //mapView.overlayManager.overlays().add(overlay)
            mapView.invalidate()
            return CustomOverlayManager(tilesOverlay)
        }

        /**
         * Use DefaultOverlayManager
         */
        fun default(mapView: MapView, context: Context?): DefaultOverlayManager {
            val tilesOverlay = TilesOverlay(mapView.tileProvider, context)
            mapView.overlayManager = DefaultOverlayManager(tilesOverlay)
            mapView.invalidate()
            return CustomOverlayManager(tilesOverlay)
        }
    }
}