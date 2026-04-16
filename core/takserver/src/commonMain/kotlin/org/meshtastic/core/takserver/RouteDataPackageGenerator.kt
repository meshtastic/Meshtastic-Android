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

/**
 * Converts route CoT XML (b-m-r) into ATAK-importable KML data packages.
 *
 * ATAK silently ignores route CoT events received over TCP streaming
 * connections — it only accepts routes from KML/GPX file import, TAK Server
 * mission sync, or data packages auto-imported from the monitored directory
 * `/sdcard/atak/tools/datapackage/`. This generator bridges the gap by
 * extracting waypoints from the SDK-reconstructed route XML and packaging
 * them as a KML LineString inside a MissionPackageManifest v2 zip.
 */
object RouteDataPackageGenerator {

    private val EVENT_UID_RE = Regex("""<event\s[^>]*\buid="([^"]*)"""")
    private val CONTACT_CALLSIGN_RE = Regex("""<contact\s[^>]*\bcallsign="([^"]*)"""")
    private val LINK_POINT_RE = Regex("""<link\s[^>]*\bpoint="([^"]*)"[^>]*/>""")

    data class RouteKmlResult(
        val kml: String,
        val routeUid: String,
        val routeName: String,
    )

    /**
     * Extract waypoints from route CoT XML and generate a KML LineString.
     * Returns null if fewer than 2 waypoints are found.
     */
    fun generateKml(routeXml: String): RouteKmlResult? {
        val uid = EVENT_UID_RE.find(routeXml)?.groupValues?.getOrNull(1) ?: return null
        val name = CONTACT_CALLSIGN_RE.find(routeXml)?.groupValues?.getOrNull(1) ?: "Mesh Route"

        // Extract all waypoint coordinates from <link ... point="lat,lon,hae" .../> elements
        val waypoints = LINK_POINT_RE.findAll(routeXml).mapNotNull { match ->
            val point = match.groupValues[1] // "lat,lon,hae" or "lat,lon"
            val parts = point.split(",").map { it.trim() }
            if (parts.size >= 2) {
                val lat = parts[0]
                val lon = parts[1]
                val hae = parts.getOrElse(2) { "0" }
                // KML coordinate order is lon,lat,hae (opposite of CoT's lat,lon,hae)
                "$lon,$lat,$hae"
            } else null
        }.toList()

        if (waypoints.size < 2) return null

        val kml = buildString {
            appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
            appendLine("""<kml xmlns="http://www.opengis.net/kml/2.2">""")
            appendLine("  <Document>")
            appendLine("    <name>${name.xmlEscaped()}</name>")
            appendLine("    <Placemark>")
            appendLine("      <name>${name.xmlEscaped()}</name>")
            appendLine("      <Style>")
            appendLine("        <LineStyle><color>ff0000ff</color><width>3</width></LineStyle>")
            appendLine("      </Style>")
            appendLine("      <LineString>")
            appendLine("        <coordinates>")
            for (coord in waypoints) {
                appendLine("          $coord")
            }
            appendLine("        </coordinates>")
            appendLine("      </LineString>")
            appendLine("    </Placemark>")
            appendLine("  </Document>")
            append("</kml>")
        }

        return RouteKmlResult(kml = kml, routeUid = uid, routeName = name)
    }

    /**
     * Generate a complete ATAK data package (zip) containing the route as KML.
     * Returns (fileName, zipBytes) or null if the route XML can't be parsed.
     */
    fun generateDataPackage(routeXml: String): Pair<String, ByteArray>? {
        val result = generateKml(routeXml) ?: return null
        val kmlFileName = "${result.routeUid}.kml"
        val zipFileName = "${result.routeUid}.zip"

        val manifest = buildString {
            appendLine("""<MissionPackageManifest version="2">""")
            appendLine("  <Configuration>")
            appendLine("""    <Parameter name="uid" value="Meshtastic Route.${result.routeUid}"/>""")
            appendLine("""    <Parameter name="name" value="${result.routeName.xmlEscaped()}"/>""")
            appendLine("""    <Parameter name="onReceiveDelete" value="true"/>""")
            appendLine("  </Configuration>")
            appendLine("  <Contents>")
            appendLine("""    <Content ignore="false" zipEntry="$kmlFileName"/>""")
            appendLine("  </Contents>")
            append("</MissionPackageManifest>")
        }

        val zipBytes = ZipArchiver.createZip(
            mapOf(
                kmlFileName to result.kml.encodeToByteArray(),
                "manifest.xml" to manifest.encodeToByteArray(),
            ),
        )

        return zipFileName to zipBytes
    }

    private fun String.xmlEscaped(): String = replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
}
