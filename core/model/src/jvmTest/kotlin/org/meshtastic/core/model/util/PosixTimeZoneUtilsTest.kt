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
package org.meshtastic.core.model.util

import kotlinx.datetime.TimeZone
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals

class PosixTimeZoneUtilsTest {

    private fun posix(zone: String) = ZoneId.of(zone).toPosixString()

    @Test
    fun `a zone with daylight saving time lists both transitions`() {
        assertEquals("EST5EDT,M3.2.0,M11.1.0", posix("America/New_York"))
    }

    @Test
    fun `a transition away from 2am carries its wall clock time`() {
        assertEquals("GMT0BST,M3.5.0/1,M10.5.0", posix("Europe/London"))
        assertEquals("CET-1CEST,M3.5.0,M10.5.0/3", posix("Europe/Berlin"))
    }

    @Test
    fun `a southern hemisphere zone starts daylight time in its spring month`() {
        assertEquals("AEST-10AEDT,M10.1.0,M4.1.0/3", posix("Australia/Sydney"))
    }

    @Test
    fun `a fixed offset zone has no transitions`() {
        assertEquals("IST-5:30", posix("Asia/Kolkata"))
        assertEquals("UTC0", posix("UTC"))
    }

    @Test
    fun `a half hour standard offset keeps its minutes`() {
        assertEquals("NST3:30NDT,M3.2.0,M11.1.0", posix("America/St_Johns"))
    }

    @Test
    fun `a daylight shift other than one hour names the daylight offset`() {
        // The JDK has no English abbreviation for Lord Howe, so both names fall back to GMT; the offsets carry it.
        assertEquals("GMT-10:30GMT-11,M10.1.0,M4.1.0", posix("Australia/Lord_Howe"))
    }

    @Test
    fun `the kotlinx time zone overload matches the java one`() {
        assertEquals(posix("America/New_York"), TimeZone.of("America/New_York").toPosixString())
    }
}
