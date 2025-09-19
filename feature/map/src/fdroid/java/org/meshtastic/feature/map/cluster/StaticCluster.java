/*
 * Copyright (c) 2025 Meshtastic LLC
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

package org.meshtastic.feature.map.cluster;

import org.meshtastic.feature.map.model.MarkerWithLabel;

import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;

import java.util.ArrayList;

/** 
 * Cluster of Markers. 
 * @author M.Kergall
 */
public class StaticCluster {
	protected final ArrayList<MarkerWithLabel> mItems = new ArrayList<MarkerWithLabel>();
	protected GeoPoint mCenter;
	protected MarkerWithLabel mMarker;
	
	public StaticCluster(GeoPoint center) {
	    mCenter = center;
	}
	
	public void setPosition(GeoPoint center){
		mCenter = center;
	}
	
	public GeoPoint getPosition() {
	    return mCenter;
	}
	
	public int getSize() {
	    return mItems.size();
	}
	
	public MarkerWithLabel getItem(int index) {
	    return mItems.get(index);
	}
	
	public boolean add(MarkerWithLabel t) {
	    return mItems.add(t);
	}
	
	/** set the Marker to be displayed for this cluster */
	public void setMarker(MarkerWithLabel marker){
		mMarker = marker;
	}
	
	/** @return the Marker to be displayed for this cluster */
	public MarkerWithLabel getMarker(){
		return mMarker;
	}

	public BoundingBox getBoundingBox(){
		if (getSize()==0)
			return null;
		GeoPoint p = getItem(0).getPosition();
		BoundingBox bb = new BoundingBox(p.getLatitude(), p.getLongitude(), p.getLatitude(), p.getLongitude());
		for (int i=1; i<getSize(); i++) {
			p = getItem(i).getPosition();
            double minLat = Math.min(bb.getLatSouth(), p.getLatitude());
            double minLon = Math.min(bb.getLonWest(), p.getLongitude());
            double maxLat = Math.max(bb.getLatNorth(), p.getLatitude());
            double maxLon = Math.max(bb.getLonEast(), p.getLongitude());
            bb.set(maxLat, maxLon, minLat, minLon);
		}
		return bb;
	}
}
