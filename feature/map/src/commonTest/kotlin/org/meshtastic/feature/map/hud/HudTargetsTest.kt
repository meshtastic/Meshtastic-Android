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
import org.meshtastic.core.model.Node
import org.meshtastic.core.repository.PhoneLocation
import org.meshtastic.core.repository.PhoneLocationState
import org.meshtastic.proto.Position
import org.meshtastic.proto.Waypoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val LAT = 40.0
private const val LON = -105.0

/** About 1.1 km north of the phone. */
private const val NORTH_LAT = 40.01

private fun phone(hae: Double? = 100.0, msl: Double? = null, accuracyM: Float? = null) = PhoneLocationState(
    permissionGranted = true,
    providerEnabled = true,
    location = PhoneLocation(LAT, LON, hae, 0L, mslAltitude = msl, accuracyM = accuracyM),
)

private fun node(num: Int, lat: Double = NORTH_LAT, hae: Int? = null, msl: Int? = null, separation: Int? = null) = Node(
    num = num,
    position =
    Position.Builder()
        .also { b ->
            b.latitude_i = (lat * 1e7).toInt()
            b.longitude_i = (LON * 1e7).toInt()
            b.altitude_hae = hae
            b.altitude = msl
            b.altitude_geoidal_separation = separation
        }
        .build(),
)

private fun waypoint(id: Int, lat: Double) = Waypoint.Builder()
    .also { b ->
        b.id = id
        b.latitude_i = (lat * 1e7).toInt()
        b.longitude_i = (LON * 1e7).toInt()
    }
    .build()

private fun build(
    location: PhoneLocationState = phone(),
    nodes: List<Node> = emptyList(),
    waypoints: List<Waypoint> = emptyList(),
    focus: String? = null,
    declination: Float = 0f,
) = buildHudTargets(location, nodes, waypoints, MeasurementSystem.METRIC, focus, nowSeconds = 0L) { declination }

class HudTargetsTest {

    @Test
    fun `ellipsoid heights are compared to each other first`() {
        val target = build(nodes = listOf(node(1, hae = 200, msl = 5))).targets.single()
        assertTrue(target.elevationKnown)
        // 100 m up over about 1112 m.
        assertEquals(5.1, target.elevationDeg, 0.1)
    }

    @Test
    fun `sea level heights are compared when either side lacks the ellipsoid`() {
        val target = build(phone(hae = null, msl = 100.0), listOf(node(1, msl = 200))).targets.single()
        assertEquals(5.1, target.elevationDeg, 0.1)
    }

    @Test
    fun `the node geoid separation bridges a sea level node to an ellipsoid phone`() {
        // Phone HAE 100 with separation -20 is 120 MSL, so a node at 220 MSL is 100 m up.
        val target = build(phone(hae = 100.0), listOf(node(1, msl = 220, separation = -20))).targets.single()
        assertEquals(5.1, target.elevationDeg, 0.1)
    }

    @Test
    fun `with no shared frame the target sits level and says so`() {
        val target = build(phone(hae = null), listOf(node(1, msl = 300))).targets.single()
        assertFalse(target.elevationKnown)
        assertTrue(target.elevationDeg < 0 && target.elevationDeg > -0.1)
    }

    @Test
    fun `a node at sea level is a reading and not a missing altitude`() {
        val target = build(phone(hae = null, msl = 100.0), listOf(node(1, msl = 0))).targets.single()
        assertTrue(target.elevationKnown)
        assertEquals(-5.1, target.elevationDeg, 0.1)
    }

    @Test
    fun `a phone at zero ellipsoid height is a reading and not a missing altitude`() {
        val target = build(phone(hae = 0.0), listOf(node(1, hae = 100))).targets.single()
        assertTrue(target.elevationKnown)
        assertEquals(5.1, target.elevationDeg, 0.1)
    }

    @Test
    fun `a node closer than the phone fix accuracy has no direction`() {
        val result = build(phone(accuracyM = 2_000f), listOf(node(1)))
        assertTrue(result.targets.isEmpty())
        assertEquals("node-1", result.directionUnknown.single().subject.key)
    }

    @Test
    fun `independent errors combine as a root sum of squares`() {
        // 800 m and 600 m make 1000 m: a node 1.1 km off stays pointable, where adding them would not.
        val node =
            Node(
                num = 1,
                position =
                Position.Builder()
                    .also { b ->
                        b.latitude_i = (NORTH_LAT * 1e7).toInt()
                        b.longitude_i = (LON * 1e7).toInt()
                        b.gps_accuracy = 800_000
                        b.PDOP = 100
                    }
                    .build(),
            )
        assertEquals(1, build(phone(accuracyM = 600f), listOf(node)).targets.size)
    }

    @Test
    fun `a waypoint closer than the phone fix accuracy has no direction either`() {
        val result = build(phone(accuracyM = 2_000f), waypoints = listOf(waypoint(7, NORTH_LAT)))
        assertEquals("waypoint-7", result.directionUnknown.single().subject.key)
    }

    @Test
    fun `a waypoint on the equator and prime meridian is placed`() {
        val nullIsland =
            Waypoint.Builder()
                .also { b ->
                    b.id = 3
                    b.latitude_i = 0
                    b.longitude_i = 0
                }
                .build()
        assertEquals("waypoint-3", build(waypoints = listOf(nullIsland)).targets.single().subject.key)
    }

    @Test
    fun `a waypoint without coordinates is left out`() {
        val unplaced = Waypoint.Builder().also { it.id = 4 }.build()
        assertTrue(build(waypoints = listOf(unplaced)).targets.isEmpty())
    }

    @Test
    fun `a circular geofence is traced around its edge`() {
        val fenced = waypoint(9, NORTH_LAT).newBuilder().also { it.geofence_radius = 100 }.build()
        val fence = build(waypoints = listOf(fenced)).fences.single()
        assertEquals(64, fence.edge.size)
        assertFalse(fence.inside)
        // The near edge is about 1012 m off, the far edge about 1212 m.
        assertEquals(1012.0, fence.edge.minOf { it.distanceM }, 5.0)
    }

    @Test
    fun `standing inside a geofence is reported`() {
        val fenced = waypoint(9, LAT).newBuilder().also { it.geofence_radius = 100 }.build()
        assertTrue(build(waypoints = listOf(fenced)).fences.single().inside)
    }

    @Test
    fun `bearings are magnetic so they match the camera`() {
        val target = build(nodes = listOf(node(1)), declination = 10f).targets.single()
        assertEquals(350.0, target.bearingDeg, 0.01)
    }

    @Test
    fun `the focused target comes first and the rest nearest first`() {
        val result = build(nodes = listOf(node(1, lat = 40.03), node(2, lat = 40.02), node(3)), focus = "node-1")
        assertEquals(listOf("node-1", "node-3", "node-2"), result.targets.map { it.subject.key })
        assertTrue(result.targets.first().focused)
    }

    @Test
    fun `a rise well beyond the altitudes uncertainty is worded with its direction`() {
        val above = build(nodes = listOf(node(1, hae = 220))).targets.single().rise
        assertTrue(assertNotNull(above).above)
        assertTrue(above.text.contains("120"))
        val below = build(nodes = listOf(node(1, hae = 40))).targets.single().rise
        assertFalse(assertNotNull(below).above)
    }

    @Test
    fun `a small rise or an unknown altitude is not worded`() {
        assertNull(build(nodes = listOf(node(1, hae = 120))).targets.single().rise)
        assertNull(build(nodes = listOf(node(1))).targets.single().rise)
    }

    @Test
    fun `without a fix there are warnings and no targets`() {
        val result = build(PhoneLocationState(permissionGranted = false, providerEnabled = true), listOf(node(1)))
        assertEquals(listOf(HudWarning.NO_LOCATION_PERMISSION), result.warnings)
        assertTrue(result.targets.isEmpty())
    }
}
