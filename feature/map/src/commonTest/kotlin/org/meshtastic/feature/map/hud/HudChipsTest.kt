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

import org.meshtastic.core.model.Node
import org.meshtastic.proto.User
import kotlin.test.Test
import kotlin.test.assertEquals

class HudChipsTest {

    @Test
    fun `straight ahead is twelve o clock`() {
        assertEquals(12, clockHour(bearingDeg = 0.0, headingDeg = 0.0))
        assertEquals(12, clockHour(bearingDeg = 214.0, headingDeg = 214.0))
    }

    @Test
    fun `the quarters read three six and nine`() {
        assertEquals(3, clockHour(bearingDeg = 90.0, headingDeg = 0.0))
        assertEquals(6, clockHour(bearingDeg = 180.0, headingDeg = 0.0))
        assertEquals(9, clockHour(bearingDeg = 270.0, headingDeg = 0.0))
    }

    @Test
    fun `the hour is relative to the heading`() {
        assertEquals(3, clockHour(bearingDeg = 180.0, headingDeg = 90.0))
        assertEquals(9, clockHour(bearingDeg = 0.0, headingDeg = 90.0))
    }

    @Test
    fun `bearings wrap across north`() {
        assertEquals(1, clockHour(bearingDeg = 20.0, headingDeg = 350.0))
        assertEquals(11, clockHour(bearingDeg = 340.0, headingDeg = 10.0))
        assertEquals(12, clockHour(bearingDeg = 5.0, headingDeg = 355.0))
    }

    @Test
    fun `an angle rounds to the nearest hour`() {
        assertEquals(12, clockHour(bearingDeg = 14.0, headingDeg = 0.0))
        assertEquals(1, clockHour(bearingDeg = 16.0, headingDeg = 0.0))
        assertEquals(11, clockHour(bearingDeg = 344.0, headingDeg = 0.0))
    }

    @Test
    fun `a node with no names is spoken by its node id`() {
        val node = Node(num = 0x1a2b3c4d, user = User.Builder().build())
        assertEquals("!1a2b3c4d", HudSubject.NodeSubject(node).spokenName())
    }

    @Test
    fun `a node with only a short name is spoken by it`() {
        val node = Node(num = 1, user = User.Builder().also { it.short_name = "ABCD" }.build())
        assertEquals("ABCD", HudSubject.NodeSubject(node).spokenName())
    }
}
