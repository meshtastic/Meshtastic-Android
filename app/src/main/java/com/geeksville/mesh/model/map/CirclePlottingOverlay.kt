package com.geeksville.mesh.model.map

import android.util.Log
import android.view.MotionEvent
import org.osmdroid.api.IMapView
import org.osmdroid.config.Configuration
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay
import org.osmdroid.views.overlay.Polygon


class CirclePlottingOverlay(var distanceKm: Int) : Overlay() {
    override fun onLongPress(e: MotionEvent, mapView: MapView): Boolean {
        if (Configuration.getInstance().isDebugMapView) {
            Log.d(IMapView.LOGTAG, "CirclePlottingOverlay onLongPress")
        }
        val pt = mapView.projection.fromPixels(e.x.toInt(), e.y.toInt(), null) as GeoPoint
        /*
         * <b>Note</b></b: when plotting a point off the map, the conversion from
         * screen coordinates to map coordinates will return values that are invalid from a latitude,longitude
         * perspective. Sometimes this is a wanted behavior and sometimes it isn't. We are leaving it up to you,
         * the developer using osmdroid to decide on what is right for your application. See
         * <a href="https://github.com/osmdroid/osmdroid/pull/722">https://github.com/osmdroid/osmdroid/pull/722</a>
         * for more information and the discussion associated with this.
         */

        //just in case the point is off the map, let's fix the coordinates
        if (pt.longitude < -180) pt.longitude = pt.longitude + 360
        if (pt.longitude > 180) pt.longitude = pt.longitude - 360
        //latitude is a bit harder. see https://en.wikipedia.org/wiki/Mercator_projection
        if (pt.latitude > 85.05112877980659) pt.latitude = 85.05112877980659
        if (pt.latitude < -85.05112877980659) pt.latitude = -85.05112877980659
        val circle: List<GeoPoint> = Polygon.pointsAsCircle(pt, distanceKm.toDouble())
        val p = Polygon(mapView)
        p.points = circle
        p.title = "A circle"
        mapView.overlayManager.add(p)
        mapView.invalidate()
        return true
    }
}