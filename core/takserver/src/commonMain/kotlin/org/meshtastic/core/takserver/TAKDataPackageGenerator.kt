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
package org.meshtastic.core.takserver

import nl.adaptivity.xmlutil.XmlDeclMode
import nl.adaptivity.xmlutil.serialization.XML
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Generates TAK data packages (.zip) compatible with ATAK/iTAK import.
 *
 * The data package follows the MissionPackageManifest v2 format:
 * ```
 * Meshtastic_TAK_Server.zip
 * ├── meshtastic-server.pref   (ATAK connection preferences)
 * └── manifest.xml             (MissionPackageManifest v2)
 * ```
 */
object TAKDataPackageGenerator {
    private const val PREF_FILE_NAME = "meshtastic-server.pref"
    private const val PACKAGE_NAME = "Meshtastic_TAK_Server"

    private val xmlSerializer = XML {
        xmlDeclMode = XmlDeclMode.Charset
        indentString = "  "
    }

    /**
     * Generate a complete TAK data package zip.
     *
     * @return zip file contents as a [ByteArray]
     */
    @OptIn(ExperimentalUuidApi::class)
    fun generateDataPackage(
        serverHost: String = "127.0.0.1",
        port: Int = DEFAULT_TAK_PORT,
        description: String = "Meshtastic TAK Server",
    ): ByteArray {
        val prefContent = generateConfigPref(serverHost, port, description)
        val manifestContent = generateManifest(uid = Uuid.random().toString(), description = description)

        val entries =
            mapOf(
                PREF_FILE_NAME to prefContent.encodeToByteArray(),
                "manifest.xml" to manifestContent.encodeToByteArray(),
            )

        return ZipArchiver.createZip(entries)
    }

    internal fun generateConfigPref(
        serverHost: String = "127.0.0.1",
        port: Int = DEFAULT_TAK_PORT,
        description: String = "Meshtastic TAK Server",
    ): String {
        val prefs =
            TAKPreferencesXml(
                preferences =
                listOf(
                    TAKPreferenceXml(
                        version = "1",
                        name = "cot_streams",
                        entries =
                        listOf(
                            TAKEntryXml("count", "class java.lang.Integer", "1"),
                            TAKEntryXml("description0", "class java.lang.String", description),
                            TAKEntryXml("enabled0", "class java.lang.Boolean", "true"),
                            TAKEntryXml("connectString0", "class java.lang.String", "$serverHost:$port:tcp"),
                        ),
                    ),
                    TAKPreferenceXml(
                        version = "1",
                        name = "com.atakmap.app_preferences",
                        entries =
                        listOf(TAKEntryXml("displayServerConnectionWidget", "class java.lang.Boolean", "true")),
                    ),
                ),
            )

        return xmlSerializer
            .encodeToString(TAKPreferencesXml.serializer(), prefs)
            .replace(
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>",
                "<?xml version='1.0' encoding='ASCII' standalone='yes'?>",
            )
    }

    internal fun generateManifest(uid: String, description: String = "Meshtastic TAK Server"): String = buildString {
        appendLine("""<MissionPackageManifest version="2">""")
        appendLine("  <Configuration>")
        appendLine("""    <Parameter name="uid" value="${description.xmlEscaped()}.$uid"/>""")
        appendLine("""    <Parameter name="name" value="$PACKAGE_NAME"/>""")
        appendLine("""    <Parameter name="onReceiveDelete" value="true"/>""")
        appendLine("  </Configuration>")
        appendLine("  <Contents>")
        appendLine("""    <Content ignore="false" zipEntry="$PREF_FILE_NAME"/>""")
        appendLine("  </Contents>")
        append("</MissionPackageManifest>")
    }

    private fun String.xmlEscaped(): String = this.replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")
}
