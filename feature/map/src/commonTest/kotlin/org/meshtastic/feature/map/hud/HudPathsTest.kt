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

import org.meshtastic.core.repository.PhoneLocation
import org.meshtastic.proto.Position
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val here = PhoneLocation(40.0, -105.0, 100.0, 0L, accuracyM = 5f)
private val northPose = CameraPose(right = Vec3(1.0, 0.0, 0.0), up = Vec3(0.0, 0.0, 1.0), forward = Vec3(0.0, 1.0, 0.0))

private fun at(lat: Double, time: Int = 0, altitude: Int? = 100) = Position.Builder()
    .also { b ->
        b.latitude_i = (lat * 1e7).toInt()
        b.longitude_i = (-105.0 * 1e7).toInt()
        b.altitude = altitude
        b.time = time
    }
    .build()

class HudPathsTest {

    @Test
    fun `a segment wholly in front is left alone`() {
        val a = Vec3(0.0, 1.0, 0.0)
        val b = Vec3(0.5, 1.0, 0.0)
        assertEquals(a to b, clipToNearPlane(a, b, northPose))
    }

    @Test
    fun `a segment wholly behind is dropped`() {
        assertNull(clipToNearPlane(Vec3(0.0, -1.0, 0.0), Vec3(0.5, -1.0, 0.0), northPose))
    }

    @Test
    fun `a segment crossing the near plane is cut at it`() {
        val (a, b) = assertNotNull(clipToNearPlane(Vec3(0.0, -1.0, 0.0), Vec3(0.0, 1.0, 0.0), northPose))
        assertEquals(NEAR_PLANE, a.y, 1e-9)
        assertEquals(1.0, b.y, 1e-9)
        assertNotNull(project(a, northPose, 800f, 1000f, 2000f))
    }

    @Test
    fun `an arc starts and ends at its hops and lifts between them`() {
        val a = PathPoint(bearingDeg = 0.0, elevationDeg = 0.0, distanceM = 1000.0, atViewer = false)
        val b = PathPoint(bearingDeg = 40.0, elevationDeg = 0.0, distanceM = 1000.0, atViewer = false)
        val arc = arcDirections(a, b, liftSign = 1)
        assertEquals(directionEnu(0.0, 0.0).x, arc.first().x, 1e-9)
        assertEquals(directionEnu(40.0, 0.0).x, arc.last().x, 1e-9)
        assertTrue(arc[arc.size / 2].z > 0)
        assertTrue(arcDirections(a, b, liftSign = -1)[arc.size / 2].z < 0)
    }

    @Test
    fun `a route counts unknown relays apart and reads SNR in dB`() {
        val positions = mapOf(1 to at(40.0), 3 to at(40.01))
        val route =
            buildHudRoute(
                here,
                declination = 0f,
                forward = listOf(1, UNKNOWN_HOP, 3),
                back = emptyList(),
                snrTowards = listOf(20, -128),
                snrBack = null,
                positionOf = positions::get,
            )
        assertEquals(2, route.total)
        assertEquals(2, route.mappable)
        assertNull(route.forward.points[1])
        assertEquals(listOf(5f, null), route.forward.snrDb)
        assertNull(route.back)
    }

    @Test
    fun `SNR is ignored when it does not match the route`() {
        val route = buildHudRoute(here, 0f, listOf(1, 2, 3), emptyList(), listOf(12), null) { at(40.01) }
        assertEquals(listOf(null, null), route.forward.snrDb)
    }

    @Test
    fun `a hop at the phone starts the route from the viewer`() {
        val route =
            buildHudRoute(here, 0f, listOf(1, 2), emptyList(), null, null) { num ->
                if (num == 1) at(40.0) else at(40.01)
            }
        assertTrue(assertNotNull(route.forward.points[0]).atViewer)
        assertFalse(assertNotNull(route.forward.points[1]).atViewer)
    }

    @Test
    fun `the connected radio is at the viewer even with no position or a distant one`() {
        val unplaced =
            buildHudRoute(here, 0f, listOf(1, 2), listOf(2, 1), null, null, viewerNum = 1) { num ->
                if (num == 1) null else at(40.01)
            }
        assertTrue(assertNotNull(unplaced.forward.points[0]).atViewer)
        assertTrue(assertNotNull(assertNotNull(unplaced.back).points[1]).atViewer)
        val distant = buildHudRoute(here, 0f, listOf(1, 2), emptyList(), null, null, viewerNum = 1) { at(40.01) }
        assertTrue(assertNotNull(distant.forward.points[0]).atViewer)
        assertEquals(2, unplaced.mappable)
    }

    @Test
    fun `a track runs oldest first and flags points with no altitude`() {
        val track =
            buildHudTrack(
                here,
                declination = 0f,
                nodeNum = 7,
                color = 0,
                positions = listOf(at(40.02, time = 300), at(40.01, time = 100, altitude = null)),
            )
        assertEquals(listOf(100L, 300L), track.points.map { it.fixTimeSec })
        assertFalse(track.points.first().altitudeKnown)
    }

    @Test
    fun `a leg out of the viewer names its far end`() {
        val near = point(atViewer = true)
        val far = point(atViewer = false)
        assertEquals(far, viewerLeg(near, far))
        assertEquals(far, viewerLeg(far, near))
    }

    @Test
    fun `a leg between two viewer points or two far points is not a viewer leg`() {
        assertNull(viewerLeg(point(atViewer = true), point(atViewer = true)))
        assertNull(viewerLeg(point(atViewer = false), point(atViewer = false)))
    }

    @Test
    fun `a summary counts points and spans oldest to newest`() {
        val summary = assertNotNull(track(100L, null, 400L).summary())
        assertEquals(3, summary.points)
        assertEquals(300L, summary.spanSec)
        assertEquals(400L, summary.newestFixTimeSec)
    }

    @Test
    fun `an empty track has no summary`() {
        assertNull(HudTrack(nodeNum = 7, color = 0, points = emptyList()).summary())
    }

    @Test
    fun `the oldest point is labeled and the newest is not`() {
        assertEquals(listOf(0), trackLabelIndices(track(0L, 600L, 1_200L).points))
    }

    @Test
    fun `a track over an hour also labels the point nearest its middle`() {
        assertEquals(listOf(0, 2), trackLabelIndices(track(0L, 1_000L, 3_700L, 7_200L).points))
    }

    @Test
    fun `viewer points and untimed points are never labeled`() {
        val points =
            listOf(
                TrackPoint(point(atViewer = true), 0L, altitudeKnown = true),
                TrackPoint(point(atViewer = false), null, altitudeKnown = true),
                TrackPoint(point(atViewer = false), 500L, altitudeKnown = true),
                TrackPoint(point(atViewer = false), 900L, altitudeKnown = true),
            )
        assertEquals(listOf(2), trackLabelIndices(points))
    }

    @Test
    fun `a one point track labels nothing`() {
        assertTrue(trackLabelIndices(track(100L).points).isEmpty())
    }

    @Test
    fun `sparks sit a spacing apart in angle from the phase across every leg`() {
        val path = listOf(directionEnu(0.0, 0.0), directionEnu(45.0, 0.0), directionEnu(90.0, 0.0))
        val sparks = sparksAlong(path, phaseDeg = 10.0, spacingDeg = 30.0)
        assertEquals(listOf(10.0, 40.0, 70.0), sparks.map { bearingOf(it.at) }, absoluteTolerance = 1e-6)
    }

    @Test
    fun `a spark looks ahead the way the path runs`() {
        val spark = sparksAlong(listOf(directionEnu(0.0, 0.0), directionEnu(90.0, 0.0)), 20.0, 30.0).first()
        assertTrue(bearingOf(spark.behind) < bearingOf(spark.at))
        assertTrue(bearingOf(spark.at) < bearingOf(spark.ahead))
    }

    @Test
    fun `a phase one spacing on puts every spark back where it was`() {
        val path = listOf(directionEnu(0.0, 0.0), directionEnu(90.0, 0.0))
        assertEquals(
            sparksAlong(path, 5.0, 30.0).map { bearingOf(it.at) },
            sparksAlong(path, 35.0, 30.0).map { bearingOf(it.at) },
            absoluteTolerance = 1e-9,
        )
    }

    @Test
    fun `a path with no length carries no sparks`() {
        assertTrue(sparksAlong(listOf(directionEnu(10.0, 0.0), directionEnu(10.0, 0.0)), 0.0, 30.0).isEmpty())
        assertTrue(sparksAlong(emptyList(), 0.0, 30.0).isEmpty())
    }

    @Test
    fun `a short leg stays a straight chord between its ends`() {
        val a = ground(east = 100.0, north = 1_000.0, rise = 0.0)
        val b = ground(east = 120.0, north = 1_000.0, rise = 0.0)
        assertEquals(2, groundDirections(a, b).size)
    }

    @Test
    fun `a leg passing close by follows the ground and dips toward the viewer`() {
        val a = ground(east = -100.0, north = 20.0, rise = -2.0)
        val b = ground(east = 100.0, north = 20.0, rise = -2.0)
        val steps = groundDirections(a, b)
        assertTrue(steps.size > 2)
        val middle = steps[steps.size / 2]
        assertEquals(0.0, bearingOf(middle), absoluteTolerance = 1e-6)
        assertEquals(elevationDeg(20.0, -2.0), elevationOf(middle), absoluteTolerance = 1e-6)
        assertEquals(a.bearingDeg, bearingOf(steps.first()), absoluteTolerance = 1e-6)
        assertEquals(b.elevationDeg, elevationOf(steps.last()), absoluteTolerance = 1e-6)
    }

    private fun ground(east: Double, north: Double, rise: Double): PathPoint {
        val distance = hypot(east, north)
        return PathPoint(
            bearingDeg = (degrees(atan2(east, north)) + 360.0) % 360.0,
            elevationDeg = elevationDeg(distance, rise),
            distanceM = distance,
            atViewer = false,
        )
    }

    private fun degrees(radians: Double) = radians * 180.0 / PI

    private fun bearingOf(v: Vec3) = (degrees(atan2(v.x, v.y)) + 360.0) % 360.0

    private fun elevationOf(v: Vec3) = degrees(asin(v.z))

    private fun assertEquals(expected: List<Double>, actual: List<Double>, absoluteTolerance: Double) {
        assertEquals(expected.size, actual.size)
        expected.zip(actual).forEach { (e, a) -> assertEquals(e, a, absoluteTolerance) }
    }

    private fun point(atViewer: Boolean) = PathPoint(
        bearingDeg = if (atViewer) 0.0 else 90.0,
        elevationDeg = 0.0,
        distanceM = if (atViewer) 1.0 else 1_000.0,
        atViewer = atViewer,
    )

    private fun track(vararg times: Long?) = HudTrack(
        nodeNum = 7,
        color = 0,
        points = times.map { TrackPoint(point(atViewer = false), it, altitudeKnown = true) },
    )
}
