package com.geeksville.mesh.model.map.clustering;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.view.MotionEvent;

import org.osmdroid.bonuspack.R;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import com.geeksville.mesh.model.map.MarkerWithLabel;

import java.util.ArrayList;
import java.util.Iterator;

/**
 * Radius-based Clustering algorithm:
 * create a cluster using the first point from the cloned list.
 * All points that are found within the neighborhood are added to this cluster.
 * Then all the neighbors and the main point are removed from the list of points.
 * It continues until the list is empty.
 *
 * Largely inspired from GridMarkerClusterer by M.Kergall
 *
 * @author sidorovroman92@gmail.com
 */

public class RadiusMarkerClusterer extends MarkerClusterer {

    protected int mMaxClusteringZoomLevel = 7;
    protected int mRadiusInPixels = 100;
    protected double mRadiusInMeters;
    protected Paint mTextPaint;
    private ArrayList<MarkerWithLabel> mClonedMarkers;
    protected boolean mAnimated;
    int mDensityDpi;

    /** cluster icon anchor */
    public float mAnchorU = MarkerWithLabel.ANCHOR_CENTER, mAnchorV = MarkerWithLabel.ANCHOR_CENTER;
    /** anchor point to draw the number of markers inside the cluster icon */
    public float mTextAnchorU = MarkerWithLabel.ANCHOR_CENTER, mTextAnchorV = MarkerWithLabel.ANCHOR_CENTER;

    public RadiusMarkerClusterer(Context ctx) {
        super();
        mTextPaint = new Paint();
        mTextPaint.setColor(Color.WHITE);
        mTextPaint.setTextSize(15 * ctx.getResources().getDisplayMetrics().density);
        mTextPaint.setFakeBoldText(true);
        mTextPaint.setTextAlign(Paint.Align.CENTER);
        mTextPaint.setAntiAlias(true);
        Drawable clusterIconD = ctx.getResources().getDrawable(R.drawable.marker_cluster);
        Bitmap clusterIcon = ((BitmapDrawable) clusterIconD).getBitmap();
        setIcon(clusterIcon);
        mAnimated = true;
        mDensityDpi = ctx.getResources().getDisplayMetrics().densityDpi;
    }

    /** If you want to change the default text paint (color, size, font) */
    public Paint getTextPaint(){
        return mTextPaint;
    }

    /** Set the radius of clustering in pixels. Default is 100px. */
    public void setRadius(int radius){
        mRadiusInPixels = radius;
    }

    /** Set max zoom level with clustering. When zoom is higher or equal to this level, clustering is disabled. 
     * You can put a high value to disable this feature. */
    public void setMaxClusteringZoomLevel(int zoom){
        mMaxClusteringZoomLevel = zoom;
    }

    /** Radius-Based clustering algorithm */
    @Override public ArrayList<StaticCluster> clusterer(MapView mapView) {

        ArrayList<StaticCluster> clusters = new ArrayList<StaticCluster>();
        convertRadiusToMeters(mapView);

        mClonedMarkers = new ArrayList<MarkerWithLabel>(mItems); //shallow copy
        while (!mClonedMarkers.isEmpty()) {
            MarkerWithLabel m = mClonedMarkers.get(0);
            StaticCluster cluster = createCluster(m, mapView);
            clusters.add(cluster);
        }
        return clusters;
    }

    private StaticCluster createCluster(MarkerWithLabel m, MapView mapView) {
        GeoPoint clusterPosition = m.getPosition();

        StaticCluster cluster = new StaticCluster(clusterPosition);
        cluster.add(m);

        mClonedMarkers.remove(m);
        
        if (mapView.getZoomLevel() > mMaxClusteringZoomLevel) {
        	//above max level => block clustering:
        	return cluster;
        }
        
        Iterator<MarkerWithLabel> it = mClonedMarkers.iterator();
        while (it.hasNext()) {
            MarkerWithLabel neighbour = it.next();
            double distance = clusterPosition.distanceToAsDouble(neighbour.getPosition());
            if (distance <= mRadiusInMeters) {
                cluster.add(neighbour);
                it.remove();
            }
        }

        return cluster;
    }

    @Override public MarkerWithLabel buildClusterMarker(StaticCluster cluster, MapView mapView) {
        MarkerWithLabel m = new MarkerWithLabel(mapView, "", null);
        m.setPosition(cluster.getPosition());
        m.setInfoWindow(null);
        m.setAnchor(mAnchorU, mAnchorV);

        Bitmap finalIcon = Bitmap.createBitmap(mClusterIcon.getScaledWidth(mDensityDpi),
                mClusterIcon.getScaledHeight(mDensityDpi), mClusterIcon.getConfig());
        Canvas iconCanvas = new Canvas(finalIcon);
        iconCanvas.drawBitmap(mClusterIcon, 0, 0, null);
        String text = "" + cluster.getSize();
        int textHeight = (int) (mTextPaint.descent() + mTextPaint.ascent());
        iconCanvas.drawText(text,
                mTextAnchorU * finalIcon.getWidth(),
                mTextAnchorV * finalIcon.getHeight() - textHeight / 2,
                mTextPaint);
        m.setIcon(new BitmapDrawable(mapView.getContext().getResources(), finalIcon));

        return m;
    }

    @Override public void renderer(ArrayList<StaticCluster> clusters, Canvas canvas, MapView mapView) {
        for (StaticCluster cluster : clusters) {
            if (cluster.getSize() == 1) {
                //cluster has only 1 marker => use it as it is:
                cluster.setMarker(cluster.getItem(0));
            } else {
                //only draw 1 Marker at Cluster center, displaying number of Markers contained
                MarkerWithLabel m = buildClusterMarker(cluster, mapView);
                cluster.setMarker(m);
            }
        }
    }

    private void convertRadiusToMeters(MapView mapView) {

        Rect mScreenRect = mapView.getIntrinsicScreenRect(null);

        int screenWidth = mScreenRect.right - mScreenRect.left;
        int screenHeight = mScreenRect.bottom - mScreenRect.top;

        BoundingBox bb = mapView.getBoundingBox();

        double diagonalInMeters = bb.getDiagonalLengthInMeters();
        double diagonalInPixels = Math.sqrt(screenWidth * screenWidth + screenHeight * screenHeight);
        double metersInPixel = diagonalInMeters / diagonalInPixels;

        mRadiusInMeters = mRadiusInPixels * metersInPixel;
    }

    public void setAnimation(boolean animate){
        mAnimated = animate;
    }

    public void zoomOnCluster(MapView mapView, StaticCluster cluster){
        BoundingBox bb = cluster.getBoundingBox();
        if (bb.getLatNorth()!=bb.getLatSouth() || bb.getLonEast()!=bb.getLonWest()) {
            bb = bb.increaseByScale(2.3f);
            mapView.zoomToBoundingBox(bb, true);
        } else //all points exactly at the same place:
            mapView.setExpectedCenter(bb.getCenterWithDateLine());
    }

    @Override public boolean onSingleTapConfirmed(final MotionEvent event, final MapView mapView){
        for (final StaticCluster cluster : reversedClusters()) {
            if (cluster.getMarker().onSingleTapConfirmed(event, mapView)) {
                if (mAnimated && cluster.getSize() > 1)
                    zoomOnCluster(mapView, cluster);
                return true;
            }
        }
        return false;
    }

}
