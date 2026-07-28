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
import org.meshtastic.core.model.EventFirmwareTheme
import org.meshtastic.core.model.EventFirmwareThemeColors
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

    @Test
    fun brandPalettePrefersAuthoredPaletteAndDropsMalformedEntries() {
        val edition =
            EventFirmwareEdition(
                edition = "DEFCON",
                accentColor = "#0D294A",
                theme = EventFirmwareTheme(palette = listOf("#0D294A", "nope", "#E0004E")),
            )
        assertEquals(
            listOf(Color(red = 0x0D, green = 0x29, blue = 0x4A), Color(red = 0xE0, green = 0x00, blue = 0x4E)),
            edition.brandPalette(),
        )
    }

    @Test
    fun brandPaletteDeDupesAuthoredEntriesPreservingOrder() {
        // A repeated hex would become a repeated gradient stop, flattening the gradient over that span.
        val edition =
            EventFirmwareEdition(
                edition = "X",
                theme = EventFirmwareTheme(palette = listOf("#E0004E", "#0D294A", "#e0004e")),
            )
        assertEquals(
            listOf(Color(red = 0xE0, green = 0x00, blue = 0x4E), Color(red = 0x0D, green = 0x29, blue = 0x4A)),
            edition.brandPalette(),
        )
    }

    @Test
    fun brandPaletteFallsBackToNamedColorsThenAccent() {
        val named =
            EventFirmwareEdition(
                edition = "X",
                accentColor = "#0D294A",
                // No palette; the named colors carry the brand. The accent duplicates primary and must not repeat.
                theme = EventFirmwareTheme(colors = EventFirmwareThemeColors(primary = "#0D294A", accent = "#E0004E")),
            )
        assertEquals(
            listOf(Color(red = 0x0D, green = 0x29, blue = 0x4A), Color(red = 0xE0, green = 0x00, blue = 0x4E)),
            named.brandPalette(),
        )

        val accentOnly = EventFirmwareEdition(edition = "X", accentColor = "#EC8819")
        assertEquals(listOf(Color(red = 0xEC, green = 0x88, blue = 0x19)), accentOnly.brandPalette())
    }

    @Test
    fun brandPaletteEmptyWhenEditionPublishesNoColors() {
        assertTrue(EventFirmwareEdition(edition = "X").brandPalette().isEmpty())
    }

    @Test
    fun brandHighlightPrefersAccentThenSecondary() {
        val withAccent =
            EventFirmwareEdition(
                edition = "X",
                theme = EventFirmwareTheme(colors = EventFirmwareThemeColors(primary = "#0D294A", accent = "#E0004E")),
            )
        assertEquals(Color(red = 0xE0, green = 0x00, blue = 0x4E), withAccent.brandHighlightOrNull())

        val secondaryOnly =
            EventFirmwareEdition(
                edition = "X",
                theme =
                EventFirmwareTheme(colors = EventFirmwareThemeColors(primary = "#0D294A", secondary = "#017FA4")),
            )
        assertEquals(Color(red = 0x01, green = 0x7F, blue = 0xA4), secondaryOnly.brandHighlightOrNull())

        // A primary alone is the wash color, not a highlight — it must not be promoted into one.
        val primaryOnly =
            EventFirmwareEdition(
                edition = "X",
                accentColor = "#BF1E2E",
                theme = EventFirmwareTheme(colors = EventFirmwareThemeColors(primary = "#BF1E2E")),
            )
        assertNull(primaryOnly.brandHighlightOrNull())
    }
}
