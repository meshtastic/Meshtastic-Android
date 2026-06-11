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
package org.meshtastic.feature.docs

import kotlinx.coroutines.test.runTest
import meshtasticandroid.feature.docs.generated.resources.Res
import org.meshtastic.feature.docs.data.DefaultDocBundleLoader
import org.meshtastic.feature.docs.ui.resolveDocImageResourcePath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Guards the docs image pipeline end to end: every image referenced by a bundled markdown page must resolve (via the
 * same path mapping the in-app renderer uses) to a real bundled compose resource. Catches broken aliases, missing
 * screenshot assets, and regressions in the markdown-path-to-resource mapping.
 */
class DocImageWiringTest {

    private val loader = DefaultDocBundleLoader()

    @Test
    fun `relative asset links resolve to the bundled screenshots directory`() {
        assertEquals(
            "files/docs/assets/screenshots/nodes_node_list.png",
            resolveDocImageResourcePath("../../assets/screenshots/nodes_node_list.png"),
        )
    }

    @Test
    fun `root-relative asset links resolve to the bundled screenshots directory`() {
        assertEquals(
            "files/docs/assets/screenshots/foo.png",
            resolveDocImageResourcePath("/assets/screenshots/foo.png"),
        )
    }

    @Test
    fun `external links are not transformed`() {
        assertNull(resolveDocImageResourcePath("https://example.com/x.png"))
        assertNull(resolveDocImageResourcePath("http://example.com/x.png"))
    }

    @Test
    fun `every image referenced by a bundled page resolves to a bundled resource`() = runTest {
        val bundle = loader.load()
        val imagePattern = Regex("""!\[[^\]]*]\(([^)\s]+)\)""")
        val missing = mutableListOf<String>()
        for (page in bundle.pages) {
            val markdown = loader.readPage(page.id)?.markdown ?: continue
            for (match in imagePattern.findAll(markdown)) {
                val link = match.groupValues[1]
                val resourcePath = resolveDocImageResourcePath(link) ?: continue
                val exists = runCatching { Res.readBytes(resourcePath).isNotEmpty() }.getOrDefault(false)
                if (!exists) missing += "${page.id}: $link -> $resourcePath"
            }
        }
        assertTrue(missing.isEmpty(), "Unresolvable doc images:\n${missing.joinToString("\n")}")
    }
}
