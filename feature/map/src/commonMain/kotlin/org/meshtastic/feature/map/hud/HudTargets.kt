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

import org.meshtastic.core.common.util.MeasurementSystem
import org.meshtastic.core.common.util.bearing
import org.meshtastic.core.common.util.latLongToMeter
import org.meshtastic.core.model.Node
import org.meshtastic.core.model.Position
import org.meshtastic.core.model.geofence.Geofence
import org.meshtastic.core.model.geofence.toGeofence
import org.meshtastic.core.model.util.fixTimeSecondsOrNull
import org.meshtastic.core.model.util.toDistanceString
import org.meshtastic.core.model.util.toElevationString
import org.meshtastic.core.model.util.uncertaintyRadiusMetersOrNull
import org.meshtastic.core.model.util.verticalUncertaintyMetersOrNull
import org.meshtastic.core.repository.PhoneLocation
import org.meshtastic.core.repository.PhoneLocationState
import org.meshtastic.proto.Waypoint
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin
import org.meshtastic.proto.Position as ProtoPosition

private const val FULL_CIRCLE = 360.0
private const val WAYPOINT_COLOR = 0xFFFFFFFF.toInt()

/** What a chip stands for. [key] is stable across updates and unique across both kinds. */
sealed interface HudSubject {
    val key: String

    /** ARGB fill for the chip's anchor dot and precision bar. */
    val color: Int

    data class NodeSubject(val node: Node) : HudSubject {
        override val key: String
            get() = "node-${node.num}"

        override val color: Int
            get() = node.colors.second
    }

    data class WaypointSubject(val waypoint: Waypoint) : HudSubject {
        override val key: String
            get() = "waypoint-${waypoint.id}"

        override val color: Int
            get() = WAYPOINT_COLOR
    }
}

/**
 * A positioned node or waypoint as the HUD draws it. [bearingDeg] is magnetic, to match the camera pose, and
 * [precisionHalfAngleDeg] spans the target's uncertainty circle widened by the phone's own fix accuracy, or is null
 * when neither is known. [elevationKnown] is false when the target and the phone share no altitude frame, so the target
 * sits level with the phone.
 */
data class HudTarget(
    val subject: HudSubject,
    val bearingDeg: Double,
    val elevationDeg: Double,
    val distanceM: Double,
    val distanceText: String,
    val precisionHalfAngleDeg: Double?,
    /** Half the vertical angle the target could lie within, from both altitudes' accuracy. */
    val elevationSpreadDeg: Double,
    val elevationKnown: Boolean,
    val positionAgeSec: Long?,
    val focused: Boolean,
    /** How far above or below the phone the target is, when that is known and beyond its own uncertainty. */
    val rise: HudRise? = null,
)

/** A rise worth saying: [text] is the magnitude in the user's units, [above] its direction. */
data class HudRise(val text: String, val above: Boolean)

/** A rise smaller than this, or than the altitudes' own uncertainty, is not worth a label. */
private const val MIN_SHOWN_RISE_M = 30.0

/**
 * A target too close to point at: the phone sits inside its uncertainty circle. The radar plots it by offset instead,
 * [eastM] and [northM] from the phone, inside a ring of [radiusM]. [bearingDeg] is magnetic, like the camera's.
 */
data class NearbyTarget(
    val subject: HudSubject,
    val eastM: Double,
    val northM: Double,
    val bearingDeg: Double,
    val distanceM: Double,
    val radiusM: Double,
)

/** A point on a geofence's edge as seen from the phone. [bearingDeg] is magnetic, like the camera's. */
data class FencePoint(val bearingDeg: Double, val distanceM: Double)

/** A waypoint's geofence outline, sampled around its edge, and whether the phone stands inside it. */
data class HudFence(val key: String, val name: String, val edge: List<FencePoint>, val inside: Boolean)

enum class HudWarning {
    NO_LOCATION_PERMISSION,
    LOCATION_DISABLED,
    NO_LOCATION_FIX,
}

data class MeshHudTargets(
    /**
     * Sorted focused first, then nearest, so the declutter pass keeps the focused chip on its point and stacks farther
     * chips above nearer ones.
     */
    val targets: List<HudTarget> = emptyList(),
    /** Nodes and waypoints whose uncertainty circle contains the phone, so their direction means nothing. */
    val directionUnknown: List<NearbyTarget> = emptyList(),
    val fences: List<HudFence> = emptyList(),
    /** The phone's own fix accuracy after averaging, in meters. */
    val ownAccuracyM: Float? = null,
    /** True when the phone's fix has no altitude, so every target sits level with it. */
    val elevationApproximate: Boolean = false,
    /** Added to the camera's magnetic azimuth to give a true heading. */
    val declinationDeg: Float = 0f,
    val units: MeasurementSystem = MeasurementSystem.METRIC,
    /** True when the view was opened for one purpose, so it shows only that and has no filters to change. */
    val scoped: Boolean = false,
    val warnings: List<HudWarning> = emptyList(),
    /** False until the first computation, so the screen does not report an empty mesh before it has looked. */
    val ready: Boolean = false,
    /** True while the phone is carried at walking pace, so the screen can remind the user to look up. */
    val moving: Boolean = false,
    /** The map's precision circle setting, which the precision zones follow. */
    val showPrecision: Boolean = true,
    /** The target the finder is on, whether placed in the view or on the radar. */
    val finding: HudFinding? = null,
)

/** True when there is nothing to show and no warning explaining why. */
internal val MeshHudTargets.showsNothing: Boolean
    get() = warnings.isEmpty() && targets.isEmpty() && directionUnknown.isEmpty()

/** What the finder is looking for and how far away it is. */
data class HudFinding(val subject: HudSubject, val distanceText: String)

/**
 * The HUD's view of [nodes] and [waypoints] from where the phone is. Pure, so the rules are testable without sensors:
 * targets inside their uncertainty circle go to [MeshHudTargets.directionUnknown], and the rest are sorted nearest
 * first.
 */
@Suppress("LongParameterList")
internal fun buildHudTargets(
    locationState: PhoneLocationState,
    nodes: List<Node>,
    waypoints: List<Waypoint>,
    units: MeasurementSystem,
    focusKey: String?,
    nowSeconds: Long,
    declinationOf: (PhoneLocation) -> Float,
): MeshHudTargets {
    val warnings = locationWarnings(locationState)
    val here = locationState.location?.takeIf { warnings.isEmpty() } ?: return MeshHudTargets(warnings = warnings)
    val declination = declinationOf(here)
    val placed = mutableListOf<HudTarget>()
    val unknown = mutableListOf<NearbyTarget>()
    nodes.forEach { node ->
        val subject = HudSubject.NodeSubject(node)
        val target =
            here.targetAt(
                subject = subject,
                latitude = node.latitude,
                longitude = node.longitude,
                targetRadiusM = node.position.uncertaintyRadiusMetersOrNull(),
                riseM = here.riseTo(node.position),
                declination = declination,
                units = units,
                targetVerticalM = node.position.verticalUncertaintyMetersOrNull(),
            )
        if (target == null) {
            unknown +=
                here.nearby(
                    subject,
                    node.latitude,
                    node.longitude,
                    node.position.uncertaintyRadiusMetersOrNull(),
                    declination,
                )
        } else {
            placed +=
                target.copy(
                    positionAgeSec = node.position.fixTimeSecondsOrNull()?.let { maxOf(0, nowSeconds - it) },
                    focused = subject.key == focusKey,
                )
        }
    }
    waypoints.forEach { waypoint ->
        val lat = waypoint.latitude_i?.let { Position.degD(it) } ?: return@forEach
        val lon = waypoint.longitude_i?.let { Position.degD(it) } ?: return@forEach
        val subject = HudSubject.WaypointSubject(waypoint)
        val target = here.targetAt(subject, lat, lon, null, null, declination, units)
        if (target == null) {
            unknown += here.nearby(subject, lat, lon, null, declination)
        } else {
            placed += target.copy(focused = subject.key == focusKey)
        }
    }
    return MeshHudTargets(
        targets = placed.sortedWith(compareByDescending<HudTarget> { it.focused }.thenBy { it.distanceM }),
        directionUnknown = unknown,
        fences = waypoints.mapNotNull { here.fenceFor(it, declination) },
        elevationApproximate = here.altitude == null && here.mslAltitude == null,
        ownAccuracyM = here.accuracyM,
        declinationDeg = declination,
    )
}

/**
 * How far above the phone [position] is, in one altitude frame: ellipsoid to ellipsoid when both report it, else sea
 * level to sea level, else the phone's ellipsoid height moved to sea level by the node's geoid separation (the geoid is
 * near flat over mesh distances). Null when no frame is shared.
 */
internal fun PhoneLocation.riseTo(position: ProtoPosition): Double? {
    val nodeHae = position.altitude_hae
    val nodeMsl = position.altitude
    val separation = position.altitude_geoidal_separation
    val phoneHae = altitude
    val phoneMsl = mslAltitude
    return when {
        nodeHae != null && phoneHae != null -> nodeHae - phoneHae
        nodeMsl != null && phoneMsl != null -> nodeMsl - phoneMsl
        nodeMsl != null && phoneHae != null && separation != null -> nodeMsl - (phoneHae - separation)
        else -> null
    }
}

/** Null when the phone sits inside the target's uncertainty circle widened by its own fix accuracy. */
@Suppress("LongParameterList")
private fun PhoneLocation.targetAt(
    subject: HudSubject,
    latitude: Double,
    longitude: Double,
    targetRadiusM: Double?,
    riseM: Double?,
    declination: Float,
    units: MeasurementSystem,
    targetVerticalM: Double? = null,
): HudTarget? {
    val distance = latLongToMeter(this.latitude, this.longitude, latitude, longitude)
    // The two errors are independent, so they combine as a root sum of squares, not a sum.
    val radius = hypot(targetRadiusM ?: 0.0, accuracyM?.toDouble() ?: 0.0)
    val halfAngle =
        if (radius > 0.0) {
            precisionHalfAngleDeg(radius, distance) ?: return null
        } else {
            null
        }
    val trueBearing = bearing(this.latitude, this.longitude, latitude, longitude)
    val verticalM = hypot(targetVerticalM ?: 0.0, verticalAccuracyM?.toDouble() ?: 0.0)
    return HudTarget(
        subject = subject,
        bearingDeg = (trueBearing - declination + FULL_CIRCLE) % FULL_CIRCLE,
        elevationDeg = elevationDeg(distance, riseM),
        distanceM = distance,
        distanceText = distance.toInt().toDistanceString(units),
        precisionHalfAngleDeg = halfAngle,
        elevationSpreadDeg = elevationSpreadDeg(distance, verticalM, riseM != null),
        elevationKnown = riseM != null,
        positionAgeSec = null,
        focused = false,
        rise =
        riseM
            ?.takeIf { abs(it) > maxOf(MIN_SHOWN_RISE_M, verticalM) }
            ?.let { HudRise(abs(it).roundToInt().toElevationString(units), above = it > 0) },
    )
}

private fun PhoneLocation.nearby(
    subject: HudSubject,
    latitude: Double,
    longitude: Double,
    targetRadiusM: Double?,
    declination: Float,
): NearbyTarget {
    val distance = latLongToMeter(this.latitude, this.longitude, latitude, longitude)
    val trueBearing = bearing(this.latitude, this.longitude, latitude, longitude)
    val rad = trueBearing.toRadians()
    return NearbyTarget(
        subject = subject,
        eastM = distance * sin(rad),
        northM = distance * cos(rad),
        bearingDeg = (trueBearing - declination + FULL_CIRCLE) % FULL_CIRCLE,
        distanceM = distance,
        radiusM = targetRadiusM ?: 0.0,
    )
}

private const val CIRCLE_SAMPLES = 64
private const val BOX_EDGE_SAMPLES = 16
private const val METERS_PER_DEGREE_LAT = 111_320.0

/** The waypoint's geofence as edge points around the phone, or null when it has none. */
private fun PhoneLocation.fenceFor(waypoint: Waypoint, declination: Float): HudFence? {
    val fence = waypoint.toGeofence()
    return fence?.outline()?.let { outline ->
        HudFence(
            key = "fence-${waypoint.id}",
            name = waypoint.name,
            edge =
            outline.map { (lat, lon) ->
                FencePoint(
                    bearingDeg = (bearing(latitude, longitude, lat, lon) - declination + FULL_CIRCLE) % FULL_CIRCLE,
                    distanceM = latLongToMeter(latitude, longitude, lat, lon),
                )
            },
            inside = fence.contains(latitude, longitude),
        )
    }
}

/**
 * Points around the fence's edge, the circle when it has one, else the box. Fences are a few hundred meters to a few
 * kilometers across, where a flat-earth offset is well within GPS error.
 */
private fun Geofence.outline(): List<Pair<Double, Double>>? = circle?.let { c ->
    (0 until CIRCLE_SAMPLES).map { i ->
        val theta = 2 * PI * i / CIRCLE_SAMPLES
        val dLat = c.radiusMeters * cos(theta) / METERS_PER_DEGREE_LAT
        val dLon = c.radiusMeters * sin(theta) / (METERS_PER_DEGREE_LAT * cos(c.centerLat.toRadians()))
        (c.centerLat + dLat) to (c.centerLon + dLon)
    }
}
    ?: box?.let { b ->
        val corners = listOf(b.south to b.west, b.north to b.west, b.north to b.east, b.south to b.east)
        corners.indices.flatMap { i ->
            val (lat0, lon0) = corners[i]
            val (lat1, lon1) = corners[(i + 1) % corners.size]
            (0 until BOX_EDGE_SAMPLES).map { k ->
                val t = k.toDouble() / BOX_EDGE_SAMPLES
                (lat0 + (lat1 - lat0) * t) to (lon0 + (lon1 - lon0) * t)
            }
        }
    }

private fun locationWarnings(state: PhoneLocationState): List<HudWarning> = when {
    !state.permissionGranted -> listOf(HudWarning.NO_LOCATION_PERMISSION)
    !state.providerEnabled -> listOf(HudWarning.LOCATION_DISABLED)
    !state.hasFix -> listOf(HudWarning.NO_LOCATION_FIX)
    else -> emptyList()
}
