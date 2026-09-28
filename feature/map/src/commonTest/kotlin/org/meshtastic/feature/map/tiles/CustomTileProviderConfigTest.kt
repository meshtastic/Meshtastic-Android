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
package org.meshtastic.feature.map.tiles

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CustomTileProviderConfigTest {
    // Every http case passes its policy explicitly: the Android actual needs a real framework and these also run as
    // host tests.
    private val cleartextAllowed: (String) -> Boolean = { true }
    private val cleartextRefused: (String) -> Boolean = { false }

    @Test
    fun `http is accepted where the platform permits plain http to the host`() {
        assertTrue("http://tiles.example.org/{z}/{x}/{y}.png".isValidTileUrlTemplate(cleartextAllowed))
        assertFalse("http://tiles.example.org/{z}/{x}/{y}.png".isRefusedCleartextTileUrl(cleartextAllowed))
    }

    @Test
    fun `http is refused where the platform refuses plain http to the host`() {
        assertFalse("http://tiles.example.org/{z}/{x}/{y}.png".isValidTileUrlTemplate(cleartextRefused))
        assertTrue("http://tiles.example.org/{z}/{x}/{y}.png".isRefusedCleartextTileUrl(cleartextRefused))
    }

    @Test
    fun `https never consults the cleartext policy`() {
        val failIfAsked: (String) -> Boolean = { error("asked about $it") }
        assertTrue("https://{s}.example.org/{Z}/{X}/{Y}.jpg".isValidTileUrlTemplate(failIfAsked))
        assertFalse("https://tiles.example.org/{z}/{x}/{y}.png".isRefusedCleartextTileUrl(failIfAsked))
    }

    @Test
    fun `the cleartext policy is asked about the bare host`() {
        val asked = mutableListOf<String>()
        val recordAndAllow: (String) -> Boolean = { host -> true.also { asked += host } }

        assertTrue("http://127.0.0.1:8080/{z}/{x}/{y}.png".isValidTileUrlTemplate(recordAndAllow))
        assertTrue("http://[::1]:8080/{z}/{x}/{y}.png".isValidTileUrlTemplate(recordAndAllow))
        assertTrue("HTTP://localhost/{z}/{x}/{y}.png?v=1".isValidTileUrlTemplate(recordAndAllow))

        assertEquals(listOf("127.0.0.1", "::1", "localhost"), asked)
    }

    @Test
    fun `a malformed http template is not reported as refused cleartext`() {
        // The form reports these as malformed instead, which is the fix the user actually needs.
        assertFalse("http://tiles.example.org/{z}/{x}.png".isRefusedCleartextTileUrl(cleartextRefused))
        assertFalse("http://token@tiles.example.org/{z}/{x}/{y}.png".isRefusedCleartextTileUrl(cleartextRefused))
    }

    @Test
    fun `a template missing any of the three coordinates is rejected`() {
        assertFalse("https://tiles.example.org/{z}/{x}.png".isValidTileUrlTemplate())
        assertFalse("https://tiles.example.org/static.png".isValidTileUrlTemplate())
    }

    @Test
    fun `validation refuses what is not an http url at all`() {
        // These are the shapes a hand-written parser gets wrong: no scheme, a scheme we do not fetch, and whitespace
        // that a URL type would have thrown on.
        assertFalse("tiles.example.org/{z}/{x}/{y}.png".isValidTileUrlTemplate(cleartextAllowed))
        assertFalse("file:///tiles/{z}/{x}/{y}.png".isValidTileUrlTemplate(cleartextAllowed))
        assertFalse("javascript:alert('{z}{x}{y}')".isValidTileUrlTemplate(cleartextAllowed))
        assertFalse("https://tiles example.org/{z}/{x}/{y}.png".isValidTileUrlTemplate(cleartextAllowed))
    }

    @Test
    fun `a port and a query string are both fine`() {
        assertTrue("https://tiles.example.org:8443/{z}/{x}/{y}.png?v=2".isValidTileUrlTemplate())
    }

    @Test
    fun `unsafe and unresolved templates are rejected even where plain http is allowed`() {
        assertFalse("http://token@tiles.example.org/{z}/{x}/{y}.png".isValidTileUrlTemplate(cleartextAllowed))
        assertFalse("http:///tiles/{z}/{x}/{y}.png".isValidTileUrlTemplate(cleartextAllowed))
        assertFalse("http://tiles.example.org/static#{z}/{x}/{y}".isValidTileUrlTemplate(cleartextAllowed))
        assertFalse("http://tiles.example.org/{z}/{x}/{y}.png?token={apiKey}".isValidTileUrlTemplate(cleartextAllowed))
    }
}
