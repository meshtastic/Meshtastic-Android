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
package org.meshtastic.core.ui.util

import androidx.compose.ui.graphics.Color
import org.meshtastic.core.model.EventFirmwareEdition
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EventBrandingTest {

    private fun accent(hex: String?) = EventFirmwareEdition(edition = "X", accentColor = hex).accentColorOrNull()

    private fun ended(end: String?, tz: String? = null) =
        EventFirmwareEdition(edition = "X", eventEnd = end, timeZone = tz).hasEnded()

    @Test
    fun parsesRrggbbWithHash() {
        assertEquals(Color(red = 0x00, green = 0x5D, blue = 0xAA), accent("#005DAA"))
    }

    @Test
    fun parsesWithoutHashAndCaseInsensitively() {
        assertEquals(Color(red = 0xE9, green = 0x4F, blue = 0x1D), accent("e94f1d"))
    }

    @Test
    fun nullForMissingOrMalformed() {
        assertNull(accent(null))
        assertNull(accent("#12345")) // too short
        assertNull(accent("#GGGGGG")) // not hex
    }

    @Test
    fun hasEndedTrueForPastDate() {
        assertTrue(ended("2000-01-01"))
        assertTrue(ended("2000-01-01", tz = "America/New_York"))
    }

    @Test
    fun hasEndedFalseForFutureDate() {
        assertFalse(ended("9999-01-01"))
    }

    @Test
    fun hasEndedFalseWhenEndDateMissingOrUnparseable() {
        assertFalse(ended(null))
        assertFalse(ended("not-a-date"))
    }

    @Test
    fun hasEndedFallsBackToSystemZoneWhenTimeZoneUnparseable() {
        // Bad IANA id must not throw — it falls back to the device zone, and a long-past date is still ended.
        assertTrue(ended("2000-01-01", tz = "Not/AZone"))
    }
}
