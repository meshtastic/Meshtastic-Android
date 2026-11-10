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
import kotlin.uuid.Uuid

/**
 * Generates TAK data packages (.zip) compatible with ATAK/iTAK/WinTAK import.
 *
 * The data package follows the MissionPackageManifest v2 format:
 * ```
 * Meshtastic_TAK_Server.zip
 * ├── meshtastic-server.pref   (ATAK connection preferences, incl. the container password)
 * ├── truststore.p12           (this install's CA certificate only, no private key)
 * ├── client.p12               (this install's client identity for mTLS)
 * └── manifest.xml             (MissionPackageManifest v2)
 * ```
 *
 * The certificates come from [TakCertificateProvider.dataPackageCredentials] and are unique to the exporting install,
 * so the package only authenticates against the server that produced it.
 */
object TAKDataPackageGenerator {
    private const val PREF_FILE_NAME = "meshtastic-server.pref"
    private const val TRUSTSTORE_FILE_NAME = "truststore.p12"
    private const val CLIENT_P12_FILE_NAME = "client.p12"
    private const val PACKAGE_NAME = "Meshtastic_TAK_Server"

    // XML.compat preserves xmlutil's pre-1.0 serialization defaults. The non-deprecated 1.0 builders
    // (XML.recommended/XML.V1) intentionally change serialization defaults, which would alter the CoT
    // XML wire format consumed by ATAK/TAK servers. Staying on the compat policy until that migration
    // can be validated against real TAK interop; suppress the soft-deprecation on the factory itself.
    @Suppress("DEPRECATION")
    private val xmlSerializer = XML.compat {
        xmlDeclMode = XmlDeclMode.Charset
        indentString = "  "
    }

    /**
     * Generate a complete TAK data package zip for [credentials].
     *
     * There is deliberately no certificate-less variant: the server only ever speaks mTLS, so a package without this
     * install's client identity could never connect. Callers must fail the export when credentials are unavailable.
     *
     * @return zip file contents as a [ByteArray]
     */
    fun generateDataPackage(
        credentials: TakDataPackageCredentials,
        serverHost: String = "127.0.0.1",
        port: Int = DEFAULT_TAK_PORT,
        description: String = "Meshtastic TAK Server",
    ): ByteArray {
        val prefContent = generateConfigPref(serverHost, port, credentials.password, description)
        val manifestContent = generateManifest(uid = Uuid.random().toString(), description = description)

        val entries = mutableMapOf<String, ByteArray>()
        entries[PREF_FILE_NAME] = prefContent.encodeToByteArray()
        entries["manifest.xml"] = manifestContent.encodeToByteArray()
        entries[TRUSTSTORE_FILE_NAME] = credentials.truststoreP12
        entries[CLIENT_P12_FILE_NAME] = credentials.clientP12

        return ZipArchiver.createZip(entries)
    }

    /** @param certPassword password of both PKCS#12 containers, written to the pref so ATAK can open them. */
    internal fun generateConfigPref(
        serverHost: String = "127.0.0.1",
        port: Int = DEFAULT_TAK_PORT,
        certPassword: String,
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
                            TAKEntryXml("connectString0", "class java.lang.String", "$serverHost:$port:ssl"),
                        ),
                    ),
                    TAKPreferenceXml(
                        version = "1",
                        name = "com.atakmap.app_preferences",
                        entries =
                        listOf(
                            TAKEntryXml("displayServerConnectionWidget", "class java.lang.Boolean", "true"),
                            TAKEntryXml("caLocation", "class java.lang.String", "cert/$TRUSTSTORE_FILE_NAME"),
                            TAKEntryXml("caPassword", "class java.lang.String", certPassword),
                            TAKEntryXml(
                                "certificateLocation",
                                "class java.lang.String",
                                "cert/$CLIENT_P12_FILE_NAME",
                            ),
                            TAKEntryXml("clientPassword", "class java.lang.String", certPassword),
                        ),
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
        appendLine("""    <Content ignore="false" zipEntry="$TRUSTSTORE_FILE_NAME"/>""")
        appendLine("""    <Content ignore="false" zipEntry="$CLIENT_P12_FILE_NAME"/>""")
        appendLine("  </Contents>")
        append("</MissionPackageManifest>")
    }
}
