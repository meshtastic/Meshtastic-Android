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
package org.meshtastic.feature.docs.ui

import androidx.compose.ui.platform.UriHandler
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DocsLinkUriHandlerTest {

    private val navigatedPages = mutableListOf<String>()
    private val externalUris = mutableListOf<String>()

    private val fallback =
        object : UriHandler {
            override fun openUri(uri: String) {
                externalUris += uri
            }
        }

    private val handler = DocsLinkUriHandler(onNavigateToPage = { navigatedPages += it }, fallback = fallback)

    @Test
    fun httpLink_delegatesToFallback() {
        handler.openUri("https://meshtastic.org/docs/faq")
        assertTrue(externalUris.contains("https://meshtastic.org/docs/faq"))
        assertTrue(navigatedPages.isEmpty())
    }

    @Test
    fun httpLink_delegatesToFallbackForHttp() {
        handler.openUri("http://example.com")
        assertTrue(externalUris.contains("http://example.com"))
        assertTrue(navigatedPages.isEmpty())
    }

    @Test
    fun anchorOnlyLink_isIgnored() {
        handler.openUri("#permissions")
        assertTrue(navigatedPages.isEmpty())
        assertTrue(externalUris.isEmpty())
    }

    @Test
    fun simpleName_navigatesToPage() {
        handler.openUri("connections")
        assertEquals(listOf("connections"), navigatedPages)
    }

    @Test
    fun relativePathWithParent_extractsFilename() {
        handler.openUri("../developer/architecture")
        assertEquals(listOf("architecture"), navigatedPages)
    }

    @Test
    fun htmlExtension_isStripped() {
        handler.openUri("mqtt.html")
        assertEquals(listOf("mqtt"), navigatedPages)
    }

    @Test
    fun mdExtension_isStripped() {
        handler.openUri("settings-radio-user.md")
        assertEquals(listOf("settings-radio-user"), navigatedPages)
    }

    @Test
    fun linkWithAnchor_stripsAnchorAndNavigates() {
        handler.openUri("nodes#signal-quality")
        assertEquals(listOf("nodes"), navigatedPages)
    }

    @Test
    fun relativePathWithHtmlAndAnchor_extractsCleanPageId() {
        handler.openUri("../user/mqtt.html#encryption")
        assertEquals(listOf("mqtt"), navigatedPages)
    }

    @Test
    fun blankUri_isIgnored() {
        handler.openUri("")
        assertTrue(navigatedPages.isEmpty())
        assertTrue(externalUris.isEmpty())
    }

    @Test
    fun anchorOnly_emptyAnchor_isIgnored() {
        handler.openUri("#")
        assertTrue(navigatedPages.isEmpty())
    }
}
