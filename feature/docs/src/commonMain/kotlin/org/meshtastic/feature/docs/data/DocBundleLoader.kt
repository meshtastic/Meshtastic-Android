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
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.koin.core.annotation.Single
import org.meshtastic.core.common.util.currentLocaleQualifier
import org.meshtastic.core.resources.doc_keywords_android_auto
import org.meshtastic.core.resources.doc_keywords_app_functions
import org.meshtastic.core.resources.doc_keywords_connections
import org.meshtastic.core.resources.doc_keywords_debug_logs
import org.meshtastic.core.resources.doc_keywords_desktop
import org.meshtastic.core.resources.doc_keywords_discovery
import org.meshtastic.core.resources.doc_keywords_firmware
import org.meshtastic.core.resources.doc_keywords_help
import org.meshtastic.core.resources.doc_keywords_map
import org.meshtastic.core.resources.doc_keywords_messages
import org.meshtastic.core.resources.doc_keywords_mqtt
import org.meshtastic.core.resources.doc_keywords_node_metrics
import org.meshtastic.core.resources.doc_keywords_nodes
import org.meshtastic.core.resources.doc_keywords_onboarding
import org.meshtastic.core.resources.doc_keywords_settings_module
import org.meshtastic.core.resources.doc_keywords_settings_radio
import org.meshtastic.core.resources.doc_keywords_signal_meter
import org.meshtastic.core.resources.doc_keywords_tak
import org.meshtastic.core.resources.doc_keywords_telemetry
import org.meshtastic.core.resources.doc_keywords_translate
import org.meshtastic.core.resources.doc_keywords_units
import org.meshtastic.core.resources.doc_keywords_widget
import org.meshtastic.core.resources.doc_title_android_auto
import org.meshtastic.core.resources.doc_title_app_functions
import org.meshtastic.core.resources.doc_title_connections
import org.meshtastic.core.resources.doc_title_debug_logs
import org.meshtastic.core.resources.doc_title_desktop
import org.meshtastic.core.resources.doc_title_discovery
import org.meshtastic.core.resources.doc_title_firmware
import org.meshtastic.core.resources.doc_title_help
import org.meshtastic.core.resources.doc_title_map
import org.meshtastic.core.resources.doc_title_messages
import org.meshtastic.core.resources.doc_title_mqtt
import org.meshtastic.core.resources.doc_title_node_metrics
import org.meshtastic.core.resources.doc_title_nodes
import org.meshtastic.core.resources.doc_title_onboarding
import org.meshtastic.core.resources.doc_title_settings_module
import org.meshtastic.core.resources.doc_title_settings_radio
import org.meshtastic.core.resources.doc_title_signal_meter
import org.meshtastic.core.resources.doc_title_tak
import org.meshtastic.core.resources.doc_title_telemetry
import org.meshtastic.core.resources.doc_title_translate
import org.meshtastic.core.resources.doc_title_units
import org.meshtastic.core.resources.doc_title_widget
import org.meshtastic.feature.docs.model.DocBundle
import org.meshtastic.feature.docs.model.DocPage
import org.meshtastic.feature.docs.model.DocPageContent
import org.meshtastic.feature.docs.model.DocSection
import org.meshtastic.feature.docs.model.KeywordIndexEntry
import org.meshtastic.core.resources.Res as CoreRes

/** Interface for loading the packaged docs bundle. */
interface DocBundleLoader {
    suspend fun load(): DocBundle

    suspend fun readPage(pageId: String): DocPageContent?

    /** Check whether a Crowdin-translated resource exists for the given page and locale. */
    suspend fun hasTranslatedResource(pageId: String, locale: String): Boolean

    fun pagesBySection(section: DocSection): List<DocPage>
}

/**
 * Default implementation that loads docs from bundled markdown files packaged as Compose Resources under `files/docs/`.
 *
 * User guide titles and keywords are resolved from string resources so Crowdin translations propagate to the in-app
 * index and search. Developer guide entries stay hardcoded English (code-focused audience).
 *
 * No cross-locale caching — the bundle rebuilds on each [load] call (~1 ms) so locale changes take effect immediately.
 */
@Single(binds = [DocBundleLoader::class])
@Suppress("TooManyFunctions")
class DefaultDocBundleLoader : DocBundleLoader {

    private var lastPages: List<DocPage> = emptyList()

    companion object {
        private const val FRONTMATTER_DELIMITER = "---"
    }

    override suspend fun load(): DocBundle {
        val entries = buildUserGuideIndex() + buildDeveloperGuideIndex()
        val pages = entries.map { it.toDocPage() }
        lastPages = pages
        return DocBundle(
            pages = pages,
            pageIndex = pages.associateBy { it.id },
            bundleVersion = "beta",
            generatedAt = "2026-05-07T00:00:00Z",
            totalBytes = 0L,
        )
    }

    override suspend fun readPage(pageId: String): DocPageContent? {
        val bundle = load()
        val page = bundle.pageIndex[pageId] ?: return null

        val markdown = loadMarkdownContent(page)
        return DocPageContent(page = page, markdown = markdown, cssPath = null)
    }

    /**
     * Load page content with locale awareness. Tries locale-qualified Crowdin resource first, falls back to English.
     * Returns a pair of (content, wasLocalized) for cascade decision.
     */
    @Suppress("ReturnCount")
    suspend fun readPageLocalized(pageId: String, locale: String): Pair<DocPageContent?, Boolean> {
        val bundle = load()
        val page = bundle.pageIndex[pageId] ?: return null to false

        if (locale != "en") {
            val localizedMarkdown = loadLocalizedMarkdownContent(page, locale)
            if (localizedMarkdown != null) {
                return DocPageContent(page = page, markdown = localizedMarkdown, cssPath = null) to true
            }
        }

        val markdown = loadMarkdownContent(page)
        return DocPageContent(page = page, markdown = markdown, cssPath = null) to false
    }

    private suspend fun loadLocalizedMarkdownContent(page: DocPage, locale: String): String? {
        val section =
            when (page.section) {
                DocSection.UserGuide -> "user"
                DocSection.DeveloperGuide -> "developer"
            }
        // Try qualifiers in specificity order (mirrors Android resource resolution):
        // "pt-rBR" → "pt" → give up
        for (qualifier in localeQualifiers(locale)) {
            val localePath = "files/$qualifier/docs/$section/${page.id}.md"
            try {
                val bytes = Res.readBytes(localePath)
                return stripFrontmatter(bytes.decodeToString())
            } catch (_: Exception) {
                continue
            }
        }
        return null
    }

    override suspend fun hasTranslatedResource(pageId: String, locale: String): Boolean {
        val bundle = load()
        val page = bundle.pageIndex[pageId] ?: return false
        val section =
            when (page.section) {
                DocSection.UserGuide -> "user"
                DocSection.DeveloperGuide -> "developer"
            }
        return localeQualifiers(locale).any { qualifier ->
            val localePath = "files/$qualifier/docs/$section/${page.id}.md"
            try {
                Res.readBytes(localePath)
                true
            } catch (_: Exception) {
                false
            }
        }
    }

    /**
     * Produces CMP resource qualifier candidates in specificity order. Tries region-qualified first (e.g. "pt-rBR"),
     * then language-only ("pt"). Deduplicates when device has no region (both would be "fr").
     */
    private fun localeQualifiers(language: String): List<String> {
        val fullQualifier = currentLocaleQualifier()
        return buildList {
            if (fullQualifier != language) add(fullQualifier)
            add(language)
        }
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

    override fun pagesBySection(section: DocSection): List<DocPage> =
        lastPages.filter { it.section == section }.sortedWith(compareBy({ it.navOrder }, { it.title }))

    /** Parse a comma-separated keyword string resource value into a list. */
    private fun parseKeywords(csv: String): List<String> = csv.split(",").map { it.trim() }.filter { it.isNotEmpty() }

    // ---------------------------------------------------------------------------
    // User Guide index — titles and keywords from string resources (translatable)
    // ---------------------------------------------------------------------------

    /**
     * Metadata for a user guide page. [titleRes] and [keywordsRes] are resolved at runtime from the device locale,
     * allowing Crowdin translations to appear in the in-app TOC and search index.
     */
    private data class UserPageDef(
        val id: String,
        val titleRes: StringResource,
        val keywordsRes: StringResource,
        val resourcePath: String,
        val navOrder: Int,
        val aliases: List<String>,
        val charCount: Int,
        val iconId: String,
    )

    @Suppress("MagicNumber")
    private val userPages =
        listOf(
            UserPageDef(
                "onboarding",
                CoreRes.string.doc_title_onboarding,
                CoreRes.string.doc_keywords_onboarding,
                "en/user/onboarding.html",
                1,
                listOf("first-launch", "setup", "intro"),
                3200,
                "onboarding",
            ),
            UserPageDef(
                "connections",
                CoreRes.string.doc_title_connections,
                CoreRes.string.doc_keywords_connections,
                "en/user/connections.html",
                2,
                listOf("bluetooth", "usb", "tcp", "pairing"),
                4100,
                "connections",
            ),
            UserPageDef(
                "messages-and-channels",
                CoreRes.string.doc_title_messages,
                CoreRes.string.doc_keywords_messages,
                "en/user/messages-and-channels.html",
                3,
                listOf("channels", "direct-messages", "messaging", "conversations"),
                4500,
                "messages",
            ),
            UserPageDef(
                "nodes",
                CoreRes.string.doc_title_nodes,
                CoreRes.string.doc_keywords_nodes,
                "en/user/nodes.html",
                4,
                listOf("node-list", "mesh-nodes", "peers"),
                3800,
                "nodes",
            ),
            UserPageDef(
                "node-metrics",
                CoreRes.string.doc_title_node_metrics,
                CoreRes.string.doc_keywords_node_metrics,
                "en/user/node-metrics.html",
                5,
                listOf("metrics", "telemetry", "device-metrics", "signal"),
                5200,
                "node-metrics",
            ),
            UserPageDef(
                "map-and-waypoints",
                CoreRes.string.doc_title_map,
                CoreRes.string.doc_keywords_map,
                "en/user/map-and-waypoints.html",
                6,
                listOf("map", "waypoints", "gps", "location"),
                3600,
                "map",
            ),
            UserPageDef(
                "settings-radio-user",
                CoreRes.string.doc_title_settings_radio,
                CoreRes.string.doc_keywords_settings_radio,
                "en/user/settings-radio-user.html",
                7,
                listOf("settings", "radio-config", "user-config", "lora"),
                6800,
                "settings-radio",
            ),
            UserPageDef(
                "settings-module-admin",
                CoreRes.string.doc_title_settings_module,
                CoreRes.string.doc_keywords_settings_module,
                "en/user/settings-module-admin.html",
                8,
                listOf("modules", "module-config", "administration"),
                5500,
                "settings-module",
            ),
            UserPageDef(
                "telemetry-and-sensors",
                CoreRes.string.doc_title_telemetry,
                CoreRes.string.doc_keywords_telemetry,
                "en/user/telemetry-and-sensors.html",
                9,
                listOf("sensors", "environment", "weather", "power-metrics"),
                4800,
                "telemetry",
            ),
            UserPageDef(
                "tak",
                CoreRes.string.doc_title_tak,
                CoreRes.string.doc_keywords_tak,
                "en/user/tak.html",
                10,
                listOf("tak", "atak", "team-awareness-kit"),
                2400,
                "tak",
            ),
            UserPageDef(
                "mqtt",
                CoreRes.string.doc_title_mqtt,
                CoreRes.string.doc_keywords_mqtt,
                "en/user/mqtt.html",
                11,
                listOf("mqtt", "internet-bridge", "broker"),
                4200,
                "mqtt",
            ),
            UserPageDef(
                "discovery",
                CoreRes.string.doc_title_discovery,
                CoreRes.string.doc_keywords_discovery,
                "en/user/discovery.html",
                12,
                listOf("mesh-discovery", "local-discovery", "network-scan"),
                2800,
                "discovery",
            ),
            UserPageDef(
                "firmware",
                CoreRes.string.doc_title_firmware,
                CoreRes.string.doc_keywords_firmware,
                "en/user/firmware.html",
                13,
                listOf("firmware", "update", "ota", "flash"),
                3400,
                "firmware",
            ),
            UserPageDef(
                "desktop",
                CoreRes.string.doc_title_desktop,
                CoreRes.string.doc_keywords_desktop,
                "en/user/desktop.html",
                14,
                listOf("desktop", "linux", "macos", "windows", "jvm"),
                3900,
                "desktop",
            ),
            UserPageDef(
                "signal-meter",
                CoreRes.string.doc_title_signal_meter,
                CoreRes.string.doc_keywords_signal_meter,
                "en/user/signal-meter.html",
                15,
                listOf("signal-quality", "signal-strength", "rssi", "snr"),
                3500,
                "signal-meter",
            ),
            UserPageDef(
                "units-and-locale",
                CoreRes.string.doc_title_units,
                CoreRes.string.doc_keywords_units,
                "en/user/units-and-locale.html",
                16,
                listOf("measurement", "units", "locale", "metric", "imperial"),
                3800,
                "units-locale",
            ),
            UserPageDef(
                "translate",
                CoreRes.string.doc_title_translate,
                CoreRes.string.doc_keywords_translate,
                "en/user/translate.html",
                17,
                listOf("crowdin", "localization", "language", "i18n", "contribute"),
                3700,
                "translate",
            ),
            UserPageDef(
                "android-auto",
                CoreRes.string.doc_title_android_auto,
                CoreRes.string.doc_keywords_android_auto,
                "en/user/android-auto.html",
                18,
                listOf("android-auto", "car", "head-unit", "auto"),
                2119,
                "android-auto",
            ),
            UserPageDef(
                "app-functions",
                CoreRes.string.doc_title_app_functions,
                CoreRes.string.doc_keywords_app_functions,
                "en/user/app-functions.html",
                19,
                listOf("app-functions", "system-ai", "gemini", "assistant"),
                2750,
                "app-functions",
            ),
            UserPageDef(
                "widget",
                CoreRes.string.doc_title_widget,
                CoreRes.string.doc_keywords_widget,
                "en/user/widget.html",
                20,
                listOf("widget", "home-screen-widget", "local-stats-widget"),
                1700,
                "widget",
            ),
            UserPageDef(
                "help-and-docs",
                CoreRes.string.doc_title_help,
                CoreRes.string.doc_keywords_help,
                "en/user/help-and-docs.html",
                21,
                listOf("help", "docs-browser", "chirpy", "assistant"),
                1900,
                "help",
            ),
            UserPageDef(
                "debug-logs",
                CoreRes.string.doc_title_debug_logs,
                CoreRes.string.doc_keywords_debug_logs,
                "en/user/debug-logs.html",
                22,
                listOf("debug-logs", "logcat", "app-logs", "bug-report"),
                3200,
                "debug-logs",
            ),
        )

    private suspend fun buildUserGuideIndex(): List<KeywordIndexEntry> = userPages.map { def ->
        KeywordIndexEntry(
            id = def.id,
            title = getString(def.titleRes),
            section = "user",
            resourcePath = def.resourcePath,
            navOrder = def.navOrder,
            keywords = parseKeywords(getString(def.keywordsRes)),
            aliases = def.aliases,
            charCount = def.charCount,
            iconId = def.iconId,
        )
    }

    // ---------------------------------------------------------------------------
    // Developer Guide index — hardcoded English (code-focused, not translated)
    // ---------------------------------------------------------------------------

    @Suppress("LongMethod", "MagicNumber")
    private fun buildDeveloperGuideIndex(): List<KeywordIndexEntry> = listOf(
        KeywordIndexEntry(
            "architecture",
            "Architecture",
            "developer",
            "en/developer/architecture.html",
            1,
            listOf("architecture", "kmp", "module", "layer", "core", "feature", "compose"),
            listOf("layers", "module-architecture", "kmp"),
            4600,
            "architecture",
        ),
        KeywordIndexEntry(
            "codebase",
            "Codebase",
            "developer",
            "en/developer/codebase.html",
            2,
            listOf("codebase", "repository", "layout", "gradle", "build", "namespace", "convention"),
            listOf("repository-layout", "project-structure", "source-code"),
            3700,
            "codebase",
        ),
        KeywordIndexEntry(
            "adding-a-feature-module",
            "Adding a Feature Module",
            "developer",
            "en/developer/adding-a-feature-module.html",
            3,
            listOf("module", "feature", "new", "create", "plugin", "di", "koin"),
            listOf("new-module", "feature-module", "module-guide"),
            3200,
            "adding-features",
        ),
        KeywordIndexEntry(
            "navigation-and-deep-links",
            "Navigation & Deep Links",
            "developer",
            "en/developer/navigation-and-deep-links.html",
            4,
            listOf("navigation", "deeplink", "route", "navkey", "backstack", "typed"),
            listOf("deeplinks", "navigation-3", "routes"),
            4100,
            "navigation",
        ),
        KeywordIndexEntry(
            "transport",
            "Transport",
            "developer",
            "en/developer/transport.html",
            5,
            listOf("transport", "ble", "serial", "tcp", "radio", "connection"),
            listOf("ble", "serial", "tcp", "radio-transport"),
            3000,
            "transport",
        ),
        KeywordIndexEntry(
            "persistence",
            "Persistence",
            "developer",
            "en/developer/persistence.html",
            6,
            listOf("room", "database", "datastore", "prefs", "storage", "migration"),
            listOf("room", "database", "datastore", "prefs"),
            2800,
            "persistence",
        ),
        KeywordIndexEntry(
            "testing",
            "Testing",
            "developer",
            "en/developer/testing.html",
            7,
            listOf("test", "unit", "screenshot", "compose", "roborazzi", "ci"),
            listOf("tests", "unit-tests", "screenshot-tests"),
            3100,
            "testing",
        ),
        KeywordIndexEntry(
            "contributing",
            "Contributing",
            "developer",
            "en/developer/contributing.html",
            8,
            listOf("contributing", "pull-request", "branch", "commit", "style", "pr"),
            listOf("contributing", "pull-request", "branch-naming"),
            2900,
            "contributing",
        ),
        KeywordIndexEntry(
            "measurement",
            "Measurement & Formatting",
            "developer",
            "en/developer/measurement.html",
            9,
            listOf("formatter", "metric", "number", "locale", "temperature", "conversion", "api"),
            listOf("metric-formatter", "number-formatter", "measurement"),
            5400,
            "measurement",
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
        iconId = iconId,
    )
}
