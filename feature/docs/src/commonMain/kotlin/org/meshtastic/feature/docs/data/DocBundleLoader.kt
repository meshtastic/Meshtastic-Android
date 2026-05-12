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
package org.meshtastic.feature.docs.data

import meshtasticandroid.feature.docs.generated.resources.Res
import org.koin.core.annotation.Single
import org.meshtastic.feature.docs.model.DocBundle
import org.meshtastic.feature.docs.model.DocPage
import org.meshtastic.feature.docs.model.DocPageContent
import org.meshtastic.feature.docs.model.DocSection
import org.meshtastic.feature.docs.model.KeywordIndexEntry

/** Interface for loading the packaged docs bundle. */
interface DocBundleLoader {
    suspend fun load(): DocBundle

    suspend fun readPage(pageId: String): DocPageContent?

    fun pagesBySection(section: DocSection): List<DocPage>
}

/**
 * Default implementation that loads docs from bundled markdown files packaged as Compose Resources under `files/docs/`.
 */
@Single
class DefaultDocBundleLoader : DocBundleLoader {

    private var cachedBundle: DocBundle? = null

    companion object {
        private const val FRONTMATTER_DELIMITER = "---"
    }

    override suspend fun load(): DocBundle {
        cachedBundle?.let {
            return it
        }

        val entries = buildStaticIndex()
        val pages = entries.map { it.toDocPage() }
        val bundle =
            DocBundle(
                pages = pages,
                pageIndex = pages.associateBy { it.id },
                bundleVersion = "beta",
                generatedAt = "2026-05-07T00:00:00Z",
                totalBytes = 0L,
            )
        cachedBundle = bundle
        return bundle
    }

    override suspend fun readPage(pageId: String): DocPageContent? {
        val bundle = load()
        val page = bundle.pageIndex[pageId] ?: return null

        val markdown = loadMarkdownContent(page)
        return DocPageContent(page = page, markdown = markdown, cssPath = null)
    }

    private suspend fun loadMarkdownContent(page: DocPage): String {
        val section =
            when (page.section) {
                DocSection.UserGuide -> "user"
                DocSection.DeveloperGuide -> "developer"
            }
        val resourcePath = "files/docs/$section/${page.id}.md"

        return try {
            val bytes = Res.readBytes(resourcePath)
            val raw = bytes.decodeToString()
            stripFrontmatter(raw)
        } catch (_: Exception) {
            "# ${page.title}\n\nContent not available. The documentation file could not be loaded."
        }
    }

    @Suppress("ReturnCount")
    private fun stripFrontmatter(content: String): String {
        if (!content.startsWith(FRONTMATTER_DELIMITER)) return content
        val endIndex = content.indexOf(FRONTMATTER_DELIMITER, startIndex = FRONTMATTER_DELIMITER.length)
        if (endIndex < 0) return content
        return content.substring(endIndex + FRONTMATTER_DELIMITER.length).trimStart()
    }

    override fun pagesBySection(section: DocSection): List<DocPage> {
        val bundle = cachedBundle ?: return emptyList()
        return bundle.pages.filter { it.section == section }.sortedWith(compareBy({ it.navOrder }, { it.title }))
    }

    @Suppress("LongMethod", "MagicNumber")
    private fun buildStaticIndex(): List<KeywordIndexEntry> = listOf(
        KeywordIndexEntry(
            "onboarding",
            "Getting Started",
            "user",
            "docs/user/onboarding.html",
            1,
            listOf("setup", "welcome", "permissions", "first-launch", "intro"),
            listOf("first-launch", "setup", "intro"),
            3200,
        ),
        KeywordIndexEntry(
            "connections",
            "Connections",
            "user",
            "docs/user/connections.html",
            2,
            listOf("bluetooth", "usb", "tcp", "pairing", "serial", "wifi"),
            listOf("bluetooth", "usb", "tcp", "pairing"),
            4100,
        ),
        KeywordIndexEntry(
            "messages-and-channels",
            "Messages & Channels",
            "user",
            "docs/user/messages-and-channels.html",
            3,
            listOf("message", "channel", "encryption", "direct", "broadcast", "psk", "quick-chat"),
            listOf("channels", "direct-messages", "messaging", "conversations"),
            4500,
        ),
        KeywordIndexEntry(
            "nodes",
            "Nodes",
            "user",
            "docs/user/nodes.html",
            4,
            listOf("node", "mesh", "list", "role", "status", "favorite", "filter"),
            listOf("node-list", "mesh-nodes", "peers"),
            3800,
        ),
        KeywordIndexEntry(
            "node-metrics",
            "Node Metrics",
            "user",
            "docs/user/node-metrics.html",
            5,
            listOf("metrics", "telemetry", "signal", "snr", "rssi", "battery", "traceroute", "environment"),
            listOf("metrics", "telemetry", "device-metrics", "signal"),
            5200,
        ),
        KeywordIndexEntry(
            "map-and-waypoints",
            "Map & Waypoints",
            "user",
            "docs/user/map-and-waypoints.html",
            6,
            listOf("map", "waypoint", "gps", "position", "location", "marker"),
            listOf("map", "waypoints", "gps", "location"),
            3600,
        ),
        KeywordIndexEntry(
            "settings-radio-user",
            "Settings — Radio & User",
            "user",
            "docs/user/settings-radio-user.html",
            7,
            listOf(
                "settings",
                "radio",
                "lora",
                "region",
                "modem",
                "device",
                "display",
                "position",
                "power",
                "network",
                "bluetooth",
                "security",
            ),
            listOf("settings", "radio-config", "user-config", "lora"),
            6800,
        ),
        KeywordIndexEntry(
            "settings-module-admin",
            "Settings — Modules & Admin",
            "user",
            "docs/user/settings-module-admin.html",
            8,
            listOf(
                "module",
                "mqtt",
                "serial",
                "telemetry",
                "canned",
                "store-forward",
                "administration",
                "remote",
                "factory-reset",
            ),
            listOf("modules", "module-config", "administration"),
            5500,
        ),
        KeywordIndexEntry(
            "telemetry-and-sensors",
            "Telemetry & Sensors",
            "user",
            "docs/user/telemetry-and-sensors.html",
            9,
            listOf("telemetry", "sensor", "temperature", "humidity", "pressure", "bme280", "power", "ina"),
            listOf("sensors", "environment", "weather", "power-metrics"),
            4800,
        ),
        KeywordIndexEntry(
            "tak",
            "TAK Integration",
            "user",
            "docs/user/tak.html",
            10,
            listOf("tak", "atak", "cursor-on-target", "team-awareness", "military"),
            listOf("tak", "atak", "team-awareness-kit"),
            2400,
        ),
        KeywordIndexEntry(
            "mqtt",
            "MQTT",
            "user",
            "docs/user/mqtt.html",
            11,
            listOf("mqtt", "broker", "internet", "bridge", "uplink", "downlink", "map-reporting"),
            listOf("mqtt", "internet-bridge", "broker"),
            4200,
        ),
        KeywordIndexEntry(
            "discovery",
            "Discovery",
            "user",
            "docs/user/discovery.html",
            12,
            listOf("discovery", "topology", "network", "scan", "neighbor"),
            listOf("mesh-discovery", "local-discovery", "network-scan"),
            2800,
        ),
        KeywordIndexEntry(
            "firmware",
            "Firmware Updates",
            "user",
            "docs/user/firmware.html",
            13,
            listOf("firmware", "update", "ota", "flash", "version", "recovery"),
            listOf("firmware", "update", "ota", "flash"),
            3400,
        ),
        KeywordIndexEntry(
            "desktop",
            "Desktop App",
            "user",
            "docs/user/desktop.html",
            14,
            listOf("desktop", "linux", "macos", "windows", "jvm", "serial"),
            listOf("desktop", "linux", "macos", "windows", "jvm"),
            3900,
        ),
        KeywordIndexEntry(
            "architecture",
            "Architecture",
            "developer",
            "docs/developer/architecture.html",
            1,
            listOf("architecture", "kmp", "module", "layer", "core", "feature", "compose"),
            listOf("layers", "module-architecture", "kmp"),
            4600,
        ),
        KeywordIndexEntry(
            "codebase",
            "Codebase",
            "developer",
            "docs/developer/codebase.html",
            2,
            listOf("codebase", "repository", "layout", "gradle", "build", "namespace", "convention"),
            listOf("repository-layout", "project-structure", "source-code"),
            3700,
        ),
        KeywordIndexEntry(
            "adding-a-feature-module",
            "Adding a Feature Module",
            "developer",
            "docs/developer/adding-a-feature-module.html",
            3,
            listOf("module", "feature", "new", "create", "plugin", "di", "koin"),
            listOf("new-module", "feature-module", "module-guide"),
            3200,
        ),
        KeywordIndexEntry(
            "navigation-and-deep-links",
            "Navigation & Deep Links",
            "developer",
            "docs/developer/navigation-and-deep-links.html",
            4,
            listOf("navigation", "deeplink", "route", "navkey", "backstack", "typed"),
            listOf("deeplinks", "navigation-3", "routes"),
            4100,
        ),
        KeywordIndexEntry(
            "transport",
            "Transport",
            "developer",
            "docs/developer/transport.html",
            5,
            listOf("transport", "ble", "serial", "tcp", "radio", "connection"),
            listOf("ble", "serial", "tcp", "radio-transport"),
            3000,
        ),
        KeywordIndexEntry(
            "persistence",
            "Persistence",
            "developer",
            "docs/developer/persistence.html",
            6,
            listOf("room", "database", "datastore", "prefs", "storage", "migration"),
            listOf("room", "database", "datastore", "prefs"),
            2800,
        ),
        KeywordIndexEntry(
            "testing",
            "Testing",
            "developer",
            "docs/developer/testing.html",
            7,
            listOf("test", "unit", "screenshot", "compose", "roborazzi", "ci"),
            listOf("tests", "unit-tests", "screenshot-tests"),
            3100,
        ),
        KeywordIndexEntry(
            "contributing",
            "Contributing",
            "developer",
            "docs/developer/contributing.html",
            8,
            listOf("contributing", "pull-request", "branch", "commit", "style", "pr"),
            listOf("contributing", "pull-request", "branch-naming"),
            2900,
        ),
    )

    private fun KeywordIndexEntry.toDocPage(): DocPage = DocPage(
        id = id,
        title = title,
        section = DocSection.fromString(section),
        navOrder = navOrder,
        resourcePath = resourcePath,
        keywords = keywords,
        aliases = aliases,
        charCount = charCount,
    )
}
