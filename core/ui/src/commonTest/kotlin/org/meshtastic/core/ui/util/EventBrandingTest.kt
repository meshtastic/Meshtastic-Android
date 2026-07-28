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
import org.meshtastic.core.model.EventFirmwareLink
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
    fun safeBrandUrlAcceptsOnlyAbsoluteHttps() {
        assertTrue(isSafeBrandUrl("https://api.meshtastic.org/resource/eventFirmware/defcon34.png"))
        assertTrue(isSafeBrandUrl("HTTPS://defcon.org")) // scheme is case-insensitive
        assertTrue(isSafeBrandUrl("  https://defcon.org  ")) // surrounding whitespace tolerated
        assertTrue(isSafeBrandUrl("https://defcon.org:8443/x.png")) // explicit port
        assertTrue(isSafeBrandUrl("https://[2606:4700::1]/x.png")) // IPv6 literal

        assertFalse(isSafeBrandUrl("http://defcon.org")) // cleartext
        assertFalse(isSafeBrandUrl("//defcon.org")) // protocol-relative
        assertFalse(isSafeBrandUrl("defcon.org")) // no scheme
        assertFalse(isSafeBrandUrl("https://")) // no host
        assertFalse(isSafeBrandUrl("https:///path")) // empty host
        assertFalse(isSafeBrandUrl(null))
        assertFalse(isSafeBrandUrl(""))
    }

    @Test
    fun safeBrandUrlRejectsMalformedAuthorities() {
        // A non-empty authority is not the same as a valid host: each of these has an authority that is present but
        // not a host, so the check has to match the authority whole rather than test it for emptiness.
        assertFalse(isSafeBrandUrl("https://:443")) // port, no host
        assertFalse(isSafeBrandUrl("https://[::1")) // unterminated IPv6 literal
        assertFalse(isSafeBrandUrl("https://[]/x")) // empty IPv6 literal
        assertFalse(isSafeBrandUrl("https://ho st/x")) // whitespace in host
        assertFalse(isSafeBrandUrl("https://defcon.org:port")) // non-numeric port
        assertFalse(isSafeBrandUrl("https://defcon.org:/x")) // empty port
    }

    @Test
    fun safeBrandUrlRejectsNonHttpSchemesAndUserinfo() {
        // These are the cases that make an unchecked URL dangerous: the platform URI handler honours whatever scheme it
        // is handed, and userinfo lets a hostile host masquerade as a trusted one in the visible prefix.
        assertFalse(isSafeBrandUrl("javascript:alert(1)"))
        assertFalse(isSafeBrandUrl("file:///etc/passwd"))
        assertFalse(isSafeBrandUrl("intent://scan/#Intent;scheme=zxing;end"))
        assertFalse(isSafeBrandUrl("meshtastic://settings"))
        assertFalse(isSafeBrandUrl("https://api.meshtastic.org@evil.example/x.png"))
    }

    @Test
    fun safeBrandUrlReturnsTheValidatedFormNotTheOriginal() {
        // Consumers hand this straight to the image loader / URI handler, so they must get the string that was
        // validated — a padded URL that passed validation and then went out untrimmed would fail as a malformed URI.
        assertEquals("https://defcon.org", safeBrandUrlOrNull("  https://defcon.org  "))
        assertEquals("https://defcon.org", safeBrandUrlOrNull("https://defcon.org"))
        assertNull(safeBrandUrlOrNull("http://defcon.org"))
    }

    @Test
    fun safeLinksNormalizesTheUrlItKeeps() {
        val edition =
            EventFirmwareEdition(
                edition = "DEFCON",
                links = listOf(EventFirmwareLink("Padded", "\n https://defcon.org/schedule \t")),
            )
        assertEquals(listOf("https://defcon.org/schedule"), edition.safeLinks().map { it.url })
        assertEquals(listOf("Padded"), edition.safeLinks().map { it.label }) // label preserved
    }

    @Test
    fun safeLinksDropsUnsafeEntriesAndKeepsOrder() {
        val edition =
            EventFirmwareEdition(
                edition = "DEFCON",
                links =
                listOf(
                    EventFirmwareLink("Event Website", "https://defcon.org"),
                    EventFirmwareLink("Bad scheme", "javascript:alert(1)"),
                    EventFirmwareLink("Cleartext", "http://defcon.org"),
                    EventFirmwareLink("Blank", ""),
                    EventFirmwareLink("Mastodon", "https://defcon.social"),
                ),
            )
        assertEquals(listOf("https://defcon.org", "https://defcon.social"), edition.safeLinks().map { it.url })
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
