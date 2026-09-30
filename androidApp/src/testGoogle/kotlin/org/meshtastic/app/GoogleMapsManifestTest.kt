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
package org.meshtastic.app

import org.w3c.dom.Element
import java.io.File
import java.util.Properties
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Google Maps' Play services module loads into the app's classloader and, on older Play services, resolves
 * org.apache.http from it, so the map screen crashes unless the merged manifest keeps the legacy library.
 */
class GoogleMapsManifestTest {

    @Test
    fun `the merged manifest keeps the Apache HTTP legacy library as optional`() {
        val config = Properties()
        requireNotNull(javaClass.classLoader?.getResourceAsStream(TEST_CONFIG)) { "$TEST_CONFIG missing" }
            .use(config::load)
        val manifest = File(requireNotNull(config.getProperty(MERGED_MANIFEST)) { "$MERGED_MANIFEST missing" })

        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        val libraries = factory.newDocumentBuilder().parse(manifest).getElementsByTagName("uses-library")
        val required =
            (0 until libraries.length)
                .map { libraries.item(it) as Element }
                .associate { it.getAttributeNS(ANDROID_NS, "name") to it.getAttributeNS(ANDROID_NS, "required") }

        assertEquals("false", required[APACHE_HTTP_LEGACY], "$APACHE_HTTP_LEGACY must be declared, not required")
    }

    private companion object {
        const val TEST_CONFIG = "com/android/tools/test_config.properties"
        const val MERGED_MANIFEST = "android_merged_manifest"
        const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
        const val APACHE_HTTP_LEGACY = "org.apache.http.legacy"
    }
}
