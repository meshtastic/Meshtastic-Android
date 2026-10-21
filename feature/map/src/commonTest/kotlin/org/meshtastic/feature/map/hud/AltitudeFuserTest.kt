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
import org.meshtastic.core.repository.PhoneLocationState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private fun fix(altitude: Double?, t: Long, msl: Double? = altitude?.minus(25.0)) =
    PhoneLocation(40.0, -105.0, altitude, t, mslAltitude = msl)

/** The pressure that reads [meters] above the standard atmosphere's sea level, inverting [pressureAltitudeM]. */
private fun hPaAt(meters: Double): Float {
    var lo = 300f
    var hi = 1100f
    repeat(60) {
        val mid = (lo + hi) / 2
        if (pressureAltitudeM(mid) > meters) lo = mid else hi = mid
    }
    return (lo + hi) / 2
}

class AltitudeFuserTest {

    @Test
    fun `without a barometer the fix passes through`() {
        val fix = fix(150.0, 1_000)
        assertEquals(fix, AltitudeFuser().fuse(fix))
    }

    @Test
    fun `the first fix sets the level and the barometer then carries every change`() {
        val fuser = AltitudeFuser()
        fuser.onPressure(hPaAt(300.0))
        assertEquals(150.0, assertNotNull(fuser.fuse(fix(150.0, 1_000)).altitude), 1e-6)
        // Up one floor by pressure, with no new fix: the height follows at once.
        fuser.onPressure(hPaAt(303.0))
        assertEquals(153.0, assertNotNull(fuser.fuse(fix(150.0, 1_000)).altitude), 0.01)
    }

    @Test
    fun `gps wander barely moves a height the barometer holds`() {
        val fuser = AltitudeFuser()
        fuser.onPressure(hPaAt(300.0))
        fuser.fuse(fix(150.0, 1_000))
        // A fix 12 m high one second later moves the fused height by about a sixtieth of that.
        val fused = assertNotNull(fuser.fuse(fix(162.0, 2_000)).altitude)
        assertTrue(fused - 150.0 in 0.1..0.3, "fused $fused")
    }

    @Test
    fun `over minutes the level settles onto gps`() {
        val fuser = AltitudeFuser()
        fuser.onPressure(hPaAt(300.0))
        fuser.fuse(fix(150.0, 0))
        var fused = 0.0
        for (s in 1..600) fused = assertNotNull(fuser.fuse(fix(160.0, s * 1_000L)).altitude)
        assertEquals(160.0, fused, 0.01)
    }

    @Test
    fun `the sea level height moves with the fused one`() {
        val fuser = AltitudeFuser()
        fuser.onPressure(hPaAt(300.0))
        fuser.fuse(fix(150.0, 1_000))
        fuser.onPressure(hPaAt(305.0))
        val fused = fuser.fuse(fix(150.0, 1_000))
        assertEquals(assertNotNull(fused.altitude) - 25.0, assertNotNull(fused.mslAltitude), 1e-6)
    }

    @Test
    fun `a fix without a gps height keeps the barometer height on the last level`() {
        val fuser = AltitudeFuser()
        fuser.onPressure(hPaAt(300.0))
        fuser.fuse(fix(150.0, 1_000))
        fuser.onPressure(hPaAt(302.0))
        val held = fuser.fuse(fix(null, 2_000, msl = null))
        assertEquals(152.0, assertNotNull(held.altitude), 0.01)
        assertEquals(127.0, assertNotNull(held.mslAltitude), 0.01)
    }

    @Test
    fun `before any level is set a fix without a gps height passes through`() {
        val fuser = AltitudeFuser()
        fuser.onPressure(hPaAt(300.0))
        val fix = fix(null, 1_000, msl = null)
        assertEquals(fix, fuser.fuse(fix))
    }

    @Test
    fun `a height change under a quarter meter is the same fix`() {
        val a = PhoneLocationState(true, true, fix(150.0, 1_000))
        assertTrue(sameFix(a, a.copy(location = fix(150.2, 1_000))))
        assertFalse(sameFix(a, a.copy(location = fix(150.3, 1_000))))
        assertFalse(sameFix(a, a.copy(location = fix(150.0, 2_000))))
    }
}
