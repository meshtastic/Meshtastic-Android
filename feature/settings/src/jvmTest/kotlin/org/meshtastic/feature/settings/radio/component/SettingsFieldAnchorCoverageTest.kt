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
package org.meshtastic.feature.settings.radio.component

import org.meshtastic.feature.settings.navigation.ConfigRoute
import org.meshtastic.feature.settings.navigation.ModuleRoute
import org.meshtastic.feature.settings.search.SettingsSearchCatalog
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Every field search can name has a row marked with `settingsField`, or is listed below with a reason, so a search
 * result lands on a row rather than only on its card. Reads the config screens' source, since the anchors are only
 * attached at composition.
 *
 * JVM-only: it reads files relative to the module directory.
 */
class SettingsFieldAnchorCoverageTest {

    /** Fields this scan cannot see anchored, with the reason. A field with no row has its card tinted instead. */
    private val unanchored =
        mapOf(
            "schema_lora_ignore_incoming" to "no control on the LoRa screen",
            "schema_position_gps_en_gpio" to "anchored through the GPIO rows' data, not a literal",
            "schema_position_rx_gpio" to "anchored through the GPIO rows' data, not a literal",
            "schema_position_tx_gpio" to "anchored through the GPIO rows' data, not a literal",
        )

    private val sourceDir = File("src/commonMain/kotlin/org/meshtastic/feature/settings/radio/component")

    private val anchorCall = Regex("""settingsField\(([^)]*)\)""")
    private val schemaKey = Regex("""schema_[a-z0-9_]+""")

    private fun anchoredKeys(): Set<String> {
        assertTrue(sourceDir.isDirectory, "run from the module directory: ${sourceDir.absolutePath}")
        return sourceDir
            .walk()
            .filter { it.extension == "kt" }
            .flatMap { file -> anchorCall.findAll(file.readText()).map { it.groupValues[1] } }
            .flatMap { args -> schemaKey.findAll(args).map { it.value } }
            .toSet()
    }

    @Test
    fun everySearchableFieldHasAnAnchoredRow() {
        val routes = ConfigRoute.entries.map { it.route } + ModuleRoute.entries.map { it.route }
        val searchable = routes.flatMap { SettingsSearchCatalog.fieldKeys(it) }.toSortedSet()
        val missing = searchable - anchoredKeys()

        assertEquals(unanchored.keys.toSortedSet(), missing.toSortedSet(), "fields with no anchored row")
    }

    @Test
    fun everyAnchorNamesASearchableField() {
        val routes = ConfigRoute.entries.map { it.route } + ModuleRoute.entries.map { it.route }
        val searchable = routes.flatMap { SettingsSearchCatalog.fieldKeys(it) }.toSet()

        assertEquals(emptySet(), anchoredKeys() - searchable, "anchors search never targets")
    }
}
