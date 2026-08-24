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
package org.meshtastic.app.ui

import org.meshtastic.core.common.util.CommonUri
import org.meshtastic.core.navigation.DeepLinkRouter
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Guards against drift between [DeepLinkRouter] and the https App Links intent-filter (`android:autoVerify="true"`,
 * host `meshtastic.org`) in `androidApp/src/main/AndroidManifest.xml`.
 *
 * Every top-level path segment routed by [DeepLinkRouter.route] must be declared as an `android:pathPrefix` in that
 * filter — otherwise `https://meshtastic.org/{path}` links open in the browser instead of the app, even though the
 * `meshtastic://` scheme works. The segments come straight from [DeepLinkRouter.topLevelPathSegments], the set
 * [DeepLinkRouter.route] gates its dispatch on, so a new router segment fails here until the manifest declares it.
 */
class DeepLinkManifestConsistencyTest {

    @Test
    fun `every canonical segment is actually routed by DeepLinkRouter`() {
        DeepLinkRouter.topLevelPathSegments.forEach { segment ->
            assertNotNull(
                DeepLinkRouter.route(CommonUri.parse("https://meshtastic.org/$segment")),
                "DeepLinkRouter.topLevelPathSegments lists /$segment but route() has no branch for it — " +
                    "add the branch or remove the segment from the set and the manifest",
            )
        }
    }

    @Test
    fun `app links intent filter declares a pathPrefix for every routed segment`() {
        val prefixes = appLinkPathPrefixes()
        DeepLinkRouter.topLevelPathSegments.forEach { segment ->
            assertTrue(
                "/$segment" in prefixes,
                "AndroidManifest.xml autoVerify filter is missing <data android:pathPrefix=\"/$segment\" /> — " +
                    "https://meshtastic.org/$segment will open in the browser instead of the app",
            )
        }
    }

    /** Collects the pathPrefix values of the autoVerify (App Links) intent-filter for meshtastic.org. */
    private fun appLinkPathPrefixes(): Set<String> {
        val manifest = manifestFile()
        val factory =
            DocumentBuilderFactory.newInstance().apply {
                // Harden against XXE even though we only parse our own manifest.
                setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
                setFeature("http://xml.org/sax/features/external-general-entities", false)
                setFeature("http://xml.org/sax/features/external-parameter-entities", false)
                isXIncludeAware = false
                isExpandEntityReferences = false
            }
        val document = factory.newDocumentBuilder().parse(manifest)
        val filters = document.getElementsByTagName("intent-filter")
        val prefixes = mutableSetOf<String>()
        for (i in 0 until filters.length) {
            val filter = filters.item(i) as Element
            if (filter.getAttribute("android:autoVerify") != "true") continue
            val dataElements = filter.getElementsByTagName("data")
            val attrs = (0 until dataElements.length).map { dataElements.item(it) as Element }
            if (attrs.none { it.getAttribute("android:host") == "meshtastic.org" }) continue
            if (attrs.none { it.getAttribute("android:scheme") == "https" }) continue
            attrs.mapNotNullTo(prefixes) { it.getAttribute("android:pathPrefix").ifEmpty { null } }
        }
        if (prefixes.isEmpty()) fail("No https App Links intent-filter for meshtastic.org found in ${manifest.path}")
        return prefixes
    }

    private fun manifestFile(): File = listOf("src/main/AndroidManifest.xml", "androidApp/src/main/AndroidManifest.xml")
        .map(::File)
        .firstOrNull(File::exists)
        ?: fail("Could not locate AndroidManifest.xml from working directory ${File(".").absolutePath}")
}
