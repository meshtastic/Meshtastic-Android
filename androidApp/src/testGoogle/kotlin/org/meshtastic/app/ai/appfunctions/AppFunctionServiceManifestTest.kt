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
package org.meshtastic.app.ai.appfunctions

import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.NodeList
import java.io.File
import java.io.InputStream
import java.util.Properties
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * AppSearch reads the v2 XML only when it supports dynamic schemas and falls back to the v1 XML otherwise, so the
 * service must name both and each must list every function the state sync writes.
 *
 * Robolectric cannot resolve `<property>` tags, so this reads the merged manifest AGP hands to unit tests.
 */
class AppFunctionServiceManifestTest {

    @Test
    fun `v1 index names an asset listing every synced function`() {
        assertIndexListsSyncedFunctions("android.app.appfunctions", idTag = "function_id")
    }

    @Test
    fun `v2 index names an asset listing every synced function`() {
        assertIndexListsSyncedFunctions("android.app.appfunctions.v2", idTag = "id")
    }

    /** Only the direct [idTag] child of each `<appfunction>` is the id AppSearch indexes; v2 repeats it deeper down. */
    private fun assertIndexListsSyncedFunctions(property: String, idTag: String) {
        val assetName = assertNotNull(serviceProperties()[property], "$SERVICE declares no $property property")
        val stream =
            assertNotNull(
                javaClass.classLoader?.getResourceAsStream("assets/$assetName"),
                "$property names $assetName, which is not packaged",
            )
        val indexedIds =
            stream
                .use(::parseXml)
                .getElementsByTagName("appfunction")
                .elements()
                .mapNotNull { function ->
                    function.childNodes.elements().firstOrNull { it.tagName == idTag }?.textContent?.trim()
                }
                .toSet()
        SYNCED_FUNCTION_IDS.forEach { id ->
            assertTrue(id in indexedIds, "$assetName has no <$idTag>$id</$idTag> entry")
        }
    }

    private fun serviceProperties(): Map<String, String> {
        val config = Properties()
        requireNotNull(javaClass.classLoader?.getResourceAsStream(TEST_CONFIG)) {
            "$TEST_CONFIG is not on the classpath"
        }
            .use(config::load)
        val manifest = File(config.getProperty("android_merged_manifest")).inputStream().use(::parseXml)
        val service =
            manifest.getElementsByTagName("service").elements().single {
                it.getAttributeNS(ANDROID_NS, "name") == SERVICE
            }
        return service.getElementsByTagName("property").elements().associate {
            it.getAttributeNS(ANDROID_NS, "name") to it.getAttributeNS(ANDROID_NS, "value")
        }
    }

    private fun parseXml(input: InputStream): Document =
        DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }.newDocumentBuilder().parse(input)

    private fun NodeList.elements(): List<Element> = (0 until length).map(::item).filterIsInstance<Element>()

    private companion object {
        const val TEST_CONFIG = "com/android/tools/test_config.properties"
        const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
        const val SERVICE = "org.meshtastic.app.ai.appfunctions.MeshtasticAppFunctionService"

        val SYNCED_FUNCTION_IDS =
            listOf(
                AppFunctionStateSync.SEND_MESSAGE_ID,
                AppFunctionStateSync.GET_MESH_STATUS_ID,
                AppFunctionStateSync.GET_NODE_LIST_ID,
                AppFunctionStateSync.GET_CHANNEL_INFO_ID,
                AppFunctionStateSync.GET_DEVICE_STATUS_ID,
                AppFunctionStateSync.GET_NODE_DETAILS_ID,
                AppFunctionStateSync.GET_MESH_METRICS_ID,
                AppFunctionStateSync.GET_RECENT_MESSAGES_ID,
                AppFunctionStateSync.GET_UNREAD_SUMMARY_ID,
            )
    }
}
