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
package org.meshtastic.core.data.repository

import org.junit.Test
import org.meshtastic.core.repository.GnssFix
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NmeaTest {
    private val tracker = NmeaTracker()

    /** [body] as a sentence, with the checksum NMEA 0183 defines. */
    private fun sentence(body: String): String =
        "$" + body + "*" + body.fold(0) { acc, c -> acc xor c.code }.toString(16).uppercase().padStart(2, '0')

    @Test
    fun `the standard GGA example passes its checksum and a wrong checksum does not`() {
        assertEquals(
            "GPGGA,123519,4807.038,N,01131.000,E,1,08,0.9,545.4,M,46.9,M,,",
            nmeaBody("\$GPGGA,123519,4807.038,N,01131.000,E,1,08,0.9,545.4,M,46.9,M,,*47"),
        )
        assertNull(nmeaBody("\$GPGGA,123519,4807.038,N,01131.000,E,1,08,0.9,545.4,M,46.9,M,,*48"))
        assertNull(nmeaBody("\$GPGGA,123519"))
    }

    @Test
    fun `GGA and GSA from any talker make one reading in hundredths`() {
        tracker.accept(sentence("GNGSA,A,3,01,02,03,04,05,06,07,08,,,,,1.6,0.9,1.3,1"), 0)
        tracker.accept(sentence("GNGGA,123519,4807.038,N,01131.000,E,1,12,0.7,100.0,M,10.0,M,,"), 0)
        assertEquals(
            GnssFix(satellitesUsed = 12, hdop = 70, pdop = 160, vdop = 130, fixQuality = 1, fixType = 3),
            tracker.fresh(0, 5_000),
        )
    }

    @Test
    fun `a GGA with no fix clears the satellites and HDOP it gave before`() {
        tracker.accept(sentence("GPGGA,123519,4807.038,N,01131.000,E,1,08,0.9,545.4,M,46.9,M,,"), 0)
        tracker.accept(sentence("GPGGA,123520,,,,,0,00,,,M,,M,,"), 0)
        val fix = tracker.fresh(0, 5_000)
        assertEquals(0, fix?.fixQuality)
        assertNull(fix?.satellitesUsed)
        assertNull(fix?.hdop)
    }

    @Test
    fun `a reading stamped outside the window around the fix is none`() {
        tracker.accept(sentence("GPGGA,123519,4807.038,N,01131.000,E,1,08,0.9,545.4,M,46.9,M,,"), 1_000)
        assertNull(tracker.fresh(6_001, 5_000))
        assertNull(tracker.fresh(-4_001, 5_000))
        assertEquals(8, tracker.fresh(6_000, 5_000)?.satellitesUsed)
    }

    @Test
    fun `a late callback does not make an old message describe a newer fix`() {
        // The message is stamped when the receiver produced it, not when the callback ran, so a callback delayed past
        // the window leaves the newer fix without it.
        tracker.accept(sentence("GPGGA,123519,4807.038,N,01131.000,E,1,08,0.9,545.4,M,46.9,M,,"), 0)
        assertNull(tracker.fresh(6_000, 5_000))
    }

    @Test
    fun `a GGA arriving later does not make an old GSA current again`() {
        tracker.accept(sentence("GPGSA,A,3,01,02,03,,,,,,,,,,1.6,0.9,1.3"), 0)
        tracker.accept(sentence("GPGGA,123519,4807.038,N,01131.000,E,1,08,0.9,545.4,M,46.9,M,,"), 6_000)
        assertEquals(GnssFix(satellitesUsed = 8, hdop = 90, fixQuality = 1), tracker.fresh(6_000, 5_000))
    }

    @Test
    fun `no sentence yet is no reading`() {
        assertNull(tracker.fresh(0, 5_000))
    }

    @Test
    fun `hundredths truncate past two places without floating point`() {
        assertEquals(123, hundredths("1.234"))
        assertEquals(115, hundredths("1.15"))
        assertEquals(-356, hundredths("-3.567"))
        assertEquals(200, hundredths("2"))
        assertEquals(50, hundredths(".5"))
        assertEquals(300, hundredths("3."))
        assertNull(hundredths(""))
        assertNull(hundredths("x"))
        assertNull(hundredths("."))
        assertNull(hundredths("-"))
        assertNull(hundredths("1.23x"))
        assertNull(hundredths("1.2x"))
        assertNull(hundredths("1x.5"))
    }
}
