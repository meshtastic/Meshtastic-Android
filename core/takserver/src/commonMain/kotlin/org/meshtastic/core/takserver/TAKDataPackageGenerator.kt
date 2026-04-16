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
 * ├── meshtastic-server.pref   (ATAK connection preferences)
 * ├── truststore.p12           (server cert — matches iOS "truststore.p12")
 * ├── client.p12               (client identity for mTLS)
 * └── manifest.xml             (MissionPackageManifest v2)
 * ```
 *
 * The bundled certificates / password match Meshtastic-Apple so a single
 * exported package works on both ATAK (Android) and iTAK (iOS) without
 * reconfiguration.
 *
 * Override [bundledCertBytesProvider] in tests to avoid touching the real classpath
 * resources. In production the default reads from [TakCertLoader].
 */
object TAKDataPackageGenerator {
    private const val PREF_FILE_NAME = "meshtastic-server.pref"
    private const val TRUSTSTORE_FILE_NAME = "truststore.p12"
    private const val CLIENT_P12_FILE_NAME = "client.p12"
    private const val PACKAGE_NAME = "Meshtastic_TAK_Server"

    private val xmlSerializer = XML {
        xmlDeclMode = XmlDeclMode.Charset
        indentString = "  "
    }

    /**
     * Platform-specific hook for reading the bundled TLS certificate bytes. Default
     * implementation lives in `jvmAndroidMain` and reads them from classpath resources
     * via [TakCertLoader].
     */
    var bundledCertBytesProvider: BundledCertBytesProvider = DefaultBundledCertBytesProvider

    /**
     * Generate a complete TAK data package zip.
     *
     * @param useTls when true, package includes `truststore.p12` + `client.p12` and
     *               the pref file uses `ssl`; when false, package is TCP-only (legacy).
     *
     * @return zip file contents as a [ByteArray]
     */
    fun generateDataPackage(
        serverHost: String = "127.0.0.1",
        port: Int = DEFAULT_TAK_PORT,
        useTls: Boolean = true,
        description: String = "Meshtastic TAK Server",
    ): ByteArray {
        val prefContent = generateConfigPref(serverHost, port, useTls, description)
        val manifestContent = generateManifest(uid = Uuid.random().toString(), description = description, useTls = useTls)

        val entries = mutableMapOf<String, ByteArray>()
        entries[PREF_FILE_NAME] = prefContent.encodeToByteArray()
        entries["manifest.xml"] = manifestContent.encodeToByteArray()

        if (useTls) {
            bundledCertBytesProvider.serverP12Bytes()?.let { entries[TRUSTSTORE_FILE_NAME] = it }
            bundledCertBytesProvider.clientP12Bytes()?.let { entries[CLIENT_P12_FILE_NAME] = it }
        }

        return ZipArchiver.createZip(entries)
    }

    internal fun generateConfigPref(
        serverHost: String = "127.0.0.1",
        port: Int = DEFAULT_TAK_PORT,
        useTls: Boolean = true,
        description: String = "Meshtastic TAK Server",
    ): String {
        val protocolType = if (useTls) "ssl" else "tcp"
        val prefs = if (useTls) {
            // TLS / mTLS mode — matches the iOS data package format exactly.
            TAKPreferencesXml(
                preferences = listOf(
                    TAKPreferenceXml(
                        version = "1",
                        name = "cot_streams",
                        entries = listOf(
                            TAKEntryXml("count", "class java.lang.Integer", "1"),
                            TAKEntryXml("description0", "class java.lang.String", description),
                            TAKEntryXml("enabled0", "class java.lang.Boolean", "true"),
                            TAKEntryXml(
                                "connectString0",
                                "class java.lang.String",
                                "$serverHost:$port:$protocolType",
                            ),
                        ),
                    ),
                    TAKPreferenceXml(
                        version = "1",
                        name = "com.atakmap.app_preferences",
                        entries = listOf(
                            TAKEntryXml(
                                "displayServerConnectionWidget",
                                "class java.lang.Boolean",
                                "true",
                            ),
                            TAKEntryXml(
                                "caLocation",
                                "class java.lang.String",
                                "cert/$TRUSTSTORE_FILE_NAME",
                            ),
                            TAKEntryXml(
                                "caPassword",
                                "class java.lang.String",
                                TAK_BUNDLED_CERT_PASSWORD,
                            ),
                            TAKEntryXml(
                                "certificateLocation",
                                "class java.lang.String",
                                "cert/$CLIENT_P12_FILE_NAME",
                            ),
                            TAKEntryXml(
                                "clientPassword",
                                "class java.lang.String",
                                TAK_BUNDLED_CERT_PASSWORD,
                            ),
                        ),
                    ),
                ),
            )
        } else {
            // Legacy plain-TCP mode (not used in production, kept for tests / fallback)
            TAKPreferencesXml(
                preferences = listOf(
                    TAKPreferenceXml(
                        version = "1",
                        name = "cot_streams",
                        entries = listOf(
                            TAKEntryXml("count", "class java.lang.Integer", "1"),
                            TAKEntryXml("description0", "class java.lang.String", description),
                            TAKEntryXml("enabled0", "class java.lang.Boolean", "true"),
                            TAKEntryXml(
                                "connectString0",
                                "class java.lang.String",
                                "$serverHost:$port:$protocolType",
                            ),
                        ),
                    ),
                    TAKPreferenceXml(
                        version = "1",
                        name = "com.atakmap.app_preferences",
                        entries = listOf(
                            TAKEntryXml("displayServerConnectionWidget", "class java.lang.Boolean", "true"),
                        ),
                    ),
                ),
            )
        }

        return xmlSerializer
            .encodeToString(TAKPreferencesXml.serializer(), prefs)
            .replace(
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>",
                "<?xml version='1.0' encoding='ASCII' standalone='yes'?>",
            )
    }

    internal fun generateManifest(
        uid: String,
        description: String = "Meshtastic TAK Server",
        useTls: Boolean = true,
    ): String = buildString {
        appendLine("""<MissionPackageManifest version="2">""")
        appendLine("  <Configuration>")
        appendLine("""    <Parameter name="uid" value="${description.xmlEscaped()}.$uid"/>""")
        appendLine("""    <Parameter name="name" value="$PACKAGE_NAME"/>""")
        appendLine("""    <Parameter name="onReceiveDelete" value="true"/>""")
        appendLine("  </Configuration>")
        appendLine("  <Contents>")
        appendLine("""    <Content ignore="false" zipEntry="$PREF_FILE_NAME"/>""")
        if (useTls) {
            appendLine("""    <Content ignore="false" zipEntry="$TRUSTSTORE_FILE_NAME"/>""")
            appendLine("""    <Content ignore="false" zipEntry="$CLIENT_P12_FILE_NAME"/>""")
        }
        appendLine("  </Contents>")
        append("</MissionPackageManifest>")
    }
}

/**
 * Supplies the bundled server / client PKCS#12 bytes for [TAKDataPackageGenerator].
 * Platform implementations live in `jvmAndroidMain`.
 */
interface BundledCertBytesProvider {
    fun serverP12Bytes(): ByteArray?
    fun clientP12Bytes(): ByteArray?
}

/**
 * Default provider that returns `null` on platforms without a real implementation.
 * Overridden at startup on JVM / Android by pointing
 * [TAKDataPackageGenerator.bundledCertBytesProvider] at [TakCertLoader].
 */
private object DefaultBundledCertBytesProvider : BundledCertBytesProvider {
    override fun serverP12Bytes(): ByteArray? = null
    override fun clientP12Bytes(): ByteArray? = null
}
