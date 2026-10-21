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
package org.meshtastic.feature.map.hud

import org.meshtastic.core.common.util.bearing
import org.meshtastic.core.common.util.latLongToMeter
import org.meshtastic.core.model.NodeAddress
import org.meshtastic.core.model.Position
import org.meshtastic.core.model.hasFix
import org.meshtastic.core.model.util.fixTimeSecondsOrNull
import org.meshtastic.core.model.util.uncertaintyRadiusMetersOrNull
import org.meshtastic.core.repository.PhoneLocation
import kotlin.math.PI
import kotlin.math.hypot
import kotlin.math.sin
import org.meshtastic.proto.Position as ProtoPosition

private const val FULL_CIRCLE = 360.0
private const val SNR_UNKNOWN = -128
private const val QUARTER_DB = 4f
private const val ARC_SAMPLES = 24
private const val ARC_LIFT_FRACTION = 0.15
private const val ARC_MIN_LIFT_DEG = 2.0
private const val ARC_MAX_LIFT_DEG = 15.0

/** Points this close are drawn from the viewer: the phone's own fix cannot tell them apart from where it stands. */
private const val MIN_VIEWER_RADIUS_M = 5.0

/** The most breadcrumbs a track draws, as the Google track map caps them. */
private const val MAX_TRACK_POINTS = 500

/**
 * A point on a path through the air, seen from the phone. [bearingDeg] is magnetic, like the camera's. [atViewer] is
 * true when the point is inside the phone's own accuracy, so the path starts from the viewer rather than a direction.
 */
data class PathPoint(val bearingDeg: Double, val elevationDeg: Double, val distanceM: Double, val atViewer: Boolean)

/**
 * One direction of a traceroute. [points] runs hop by hop, null for a hop with no position; [snrDb] holds the signal of
 * the link into each hop after the first, null where it was not reported.
 */
data class RouteLeg(
    val nodeNums: List<Int>,
    val points: List<PathPoint?>,
    val snrDb: List<Float?>,
    val forward: Boolean,
)

/** A traceroute to draw: its legs, and how many of its known nodes have a position to draw them at. */
data class HudRoute(val forward: RouteLeg, val back: RouteLeg?, val mappable: Int, val total: Int)

data class TrackPoint(val point: PathPoint, val ageSec: Long?, val altitudeKnown: Boolean)

/** A node's position history as breadcrumbs, oldest first, in the node's [color]. */
data class HudTrack(val nodeNum: Int, val color: Int, val points: List<TrackPoint>)

/** The node num a traceroute uses for a relay it could not identify. */
internal const val UNKNOWN_HOP = NodeAddress.NODENUM_BROADCAST

/** Where [position] is from here, or null when it has no fix. */
private fun PhoneLocation.pathPoint(position: ProtoPosition, declination: Float): PathPoint? {
    if (!position.hasFix()) return null
    val lat = Position.degD(position.latitude_i ?: 0)
    val lon = Position.degD(position.longitude_i ?: 0)
    val distance = latLongToMeter(latitude, longitude, lat, lon)
    val reach =
        maxOf(MIN_VIEWER_RADIUS_M, hypot(position.uncertaintyRadiusMetersOrNull() ?: 0.0, accuracyM?.toDouble() ?: 0.0))
    return PathPoint(
        bearingDeg = (bearing(latitude, longitude, lat, lon) - declination + FULL_CIRCLE) % FULL_CIRCLE,
        elevationDeg = elevationDeg(distance, riseTo(position)),
        distanceM = distance,
        atViewer = distance <= reach,
    )
}

/** Quarter-dB link SNRs as dB, valid only when there is one per link of [route]; unknowns become null. */
private fun snrFor(route: List<Int>, quarterDb: List<Int>?): List<Float?> =
    if (quarterDb != null && quarterDb.size == route.size - 1) {
        quarterDb.map { q -> q.takeIf { it != SNR_UNKNOWN }?.let { it / QUARTER_DB } }
    } else {
        List((route.size - 1).coerceAtLeast(0)) { null }
    }

/** The phone itself, where a leg to or from the connected radio starts or ends. */
private val ViewerPoint = PathPoint(bearingDeg = 0.0, elevationDeg = 0.0, distanceM = 0.0, atViewer = true)

/**
 * A traceroute as legs of points from here. Each hop takes [positionOf]'s answer, which should prefer the position the
 * route was recorded with and fall back to the live one, so a hop is only blank when neither is known. [viewerNum], the
 * connected radio, is always at the viewer: it is within Bluetooth or cable reach of the phone, and often has no
 * position of its own.
 */
@Suppress("LongParameterList")
internal fun buildHudRoute(
    here: PhoneLocation,
    declination: Float,
    forward: List<Int>,
    back: List<Int>,
    snrTowards: List<Int>?,
    snrBack: List<Int>?,
    viewerNum: Int? = null,
    positionOf: (Int) -> ProtoPosition?,
): HudRoute {
    fun pointOf(num: Int) =
        if (num == viewerNum) ViewerPoint else positionOf(num)?.let { here.pathPoint(it, declination) }
    fun leg(route: List<Int>, snr: List<Int>?, isForward: Boolean) = RouteLeg(
        nodeNums = route,
        points = route.map(::pointOf),
        snrDb = snrFor(route, snr),
        forward = isForward,
    )
    val known = (forward + back).filter { it != UNKNOWN_HOP }.distinct()
    return HudRoute(
        forward = leg(forward, snrTowards, isForward = true),
        back = back.takeIf { it.isNotEmpty() }?.let { leg(it, snrBack, isForward = false) },
        mappable = known.count { num -> num == viewerNum || positionOf(num)?.hasFix() == true },
        total = known.size,
    )
}

/** A node's positions as breadcrumbs from here, oldest first, keeping the newest [MAX_TRACK_POINTS] with a fix. */
internal fun buildHudTrack(
    here: PhoneLocation,
    declination: Float,
    nodeNum: Int,
    color: Int,
    positions: List<ProtoPosition>,
    nowSeconds: Long,
): HudTrack {
    val points =
        positions
            .filter { it.hasFix() }
            .sortedBy { it.fixTimeSecondsOrNull() ?: 0 }
            .takeLast(MAX_TRACK_POINTS)
            .mapNotNull { position ->
                here.pathPoint(position, declination)?.let { point ->
                    TrackPoint(
                        point = point,
                        ageSec = position.fixTimeSecondsOrNull()?.let { maxOf(0, nowSeconds - it) },
                        altitudeKnown = position.altitude != null || position.altitude_hae != null,
                    )
                }
            }
    return HudTrack(nodeNum, color, points)
}

/**
 * The directions along a lifted arc from [a] to [b], so a link reads as a span through the air rather than a line on
 * the ground. [liftSign] is 1 for the forward leg and -1 for the return, so the two directions of a route part.
 */
internal fun arcDirections(a: PathPoint, b: PathPoint, liftSign: Int, samples: Int = ARC_SAMPLES): List<Vec3> {
    val turn = signedTurnDeg(a.bearingDeg, b.bearingDeg)
    val rise = b.elevationDeg - a.elevationDeg
    val lift = (hypot(turn, rise) * ARC_LIFT_FRACTION).coerceIn(ARC_MIN_LIFT_DEG, ARC_MAX_LIFT_DEG) * liftSign
    return (0..samples).map { i ->
        val t = i.toDouble() / samples
        directionEnu(a.bearingDeg + turn * t, a.elevationDeg + rise * t + lift * sin(PI * t))
    }
}

/**
 * Clips the segment [a]-[b] to the part in front of the camera, where [project] can place it. A segment wholly behind
 * gives null; one crossing the near plane has its behind end moved onto the plane, which projects far off screen in the
 * right direction, so a line through the viewer's side still reaches the edge instead of wrapping around.
 */
internal fun clipToNearPlane(a: Vec3, b: Vec3, pose: CameraPose): Pair<Vec3, Vec3>? {
    val za = a dot pose.forward
    val zb = b dot pose.forward
    return when {
        za >= NEAR_PLANE && zb >= NEAR_PLANE -> a to b

        za < NEAR_PLANE && zb < NEAR_PLANE -> null

        else -> {
            val t = (NEAR_PLANE - za) / (zb - za)
            val q = Vec3(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t, a.z + (b.z - a.z) * t)
            if (za < NEAR_PLANE) q to b else a to q
        }
    }
}

/** Just inside [project]'s own cutoff, so a point clipped onto it still projects despite rounding. */
internal const val NEAR_PLANE = 0.051
