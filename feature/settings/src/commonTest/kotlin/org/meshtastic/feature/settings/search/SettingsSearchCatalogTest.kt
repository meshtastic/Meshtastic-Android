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
package org.meshtastic.feature.settings.search

import org.jetbrains.compose.resources.StringResource
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.allStringResources
import org.meshtastic.feature.settings.navigation.ConfigRoute
import org.meshtastic.feature.settings.navigation.ModuleRoute
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The catalog names which settings screen owns each schema message. Nothing in the schema carries that mapping, so it
 * is hand-written, and these tests are what keep it honest when the schema moves under it.
 */
class SettingsSearchCatalogTest {

    private val schemaKeys: Set<String> = Res.allStringResources.keys.filter { it.startsWith("schema_") }.toSet()

    /** `schema_lora_hop_limit` and `schema_lora_modempreset_long_fast` both reduce to `lora`. */
    private fun messagePrefixes(): Set<String> =
        schemaKeys.mapNotNull { it.removePrefix("schema_").substringBefore('_').ifEmpty { null } }.toSet()

    @Test
    fun everyLabelledMessageIsEitherSearchableOrExcused() {
        val claimed = SettingsSearchCatalog.declaredMessagePrefixes()
        val excused = SettingsSearchCatalog.excusedMessagePrefixes()
        val unaccounted = messagePrefixes() - claimed - excused

        assertTrue(
            unaccounted.isEmpty(),
            "the schema labels fields on these messages and nothing says which settings screen owns them: " +
                unaccounted.sorted().joinToString() +
                ". Add the message to schemaPrefixes, or to notSearchable with a reason.",
        )
    }

    /**
     * The generated set is pinned against the registry upstream; this checks the other half, that every prefix in it
     * reaches a resource this module can actually see, which is what the index filters on.
     */
    @Test
    fun everyDeclaredEnumStillHasValuesInTheSchema() {
        val stale =
            SettingsSearchCatalog.declaredEnumValuePrefixes().filter { prefix ->
                schemaKeys.none { it.startsWith(prefix) }
            }

        assertEquals(
            emptyList(),
            stale.sorted(),
            "these enum prefixes match no string resource, so the index is filtering on something that is not there. " +
                "The generated set and schema_strings.xml have diverged; re-run :schema-strings:sync.",
        )
    }

    @Test
    fun noEnumValueIsOfferedAsASetting() {
        val enumPrefixes = SettingsSearchCatalog.declaredEnumValuePrefixes()
        val titles = SettingsSearchCatalog.entries().mapNotNull { entry -> keyOf(entry.title) }
        val leaked = titles.filter { key -> enumPrefixes.any { key.startsWith(it) } }

        assertEquals(emptyList(), leaked.sorted(), "these are picker options, not settings, and must not be indexed")
    }

    @Test
    fun noDescriptionIsOfferedAsASetting() {
        val leaked =
            SettingsSearchCatalog.entries().mapNotNull { keyOf(it.title) }.filter { it.endsWith("_description") }

        assertEquals(emptyList(), leaked.sorted(), "a field's explanation is its subtitle, never a result of its own")
    }

    @Test
    fun everyConfigurationScreenIsReachableFromSearch() {
        val routes = SettingsSearchCatalog.entries().map { it.route }.toSet()
        val missing = (ConfigRoute.entries.map { it.route } + ModuleRoute.entries.map { it.route }) - routes

        assertEquals(emptyList(), missing, "these settings screens cannot be found by searching for their own name")
    }

    @Test
    fun onlyThisPhonesOwnSettingsAreMarkedAppLocal() {
        val entries = SettingsSearchCatalog.entries()
        val appLocal = entries.filter { it.isAppLocal }

        // A remote session drops these, so a proto-backed control must never be caught by that filter.
        assertTrue(appLocal.isNotEmpty(), "the app's own settings should be searchable on a local session")
        assertTrue(
            appLocal.none { entry -> keyOf(entry.title)?.startsWith("schema_") == true },
            "a radio setting was marked app-local and would vanish during remote administration",
        )
    }

    /** The resource's registered name, which is what the catalog keys off. */
    private fun keyOf(resource: StringResource): String? =
        Res.allStringResources.entries.firstOrNull { it.value == resource }?.key
}
