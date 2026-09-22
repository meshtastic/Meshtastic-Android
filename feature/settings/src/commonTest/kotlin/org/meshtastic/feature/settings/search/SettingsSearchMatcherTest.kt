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

import org.meshtastic.core.navigation.SettingsRoute
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SettingsSearchMatcherTest {

    private fun entry(title: String, description: String? = null, screen: String = "LoRa") = ResolvedSettingsEntry(
        title = title,
        description = description,
        screenTitle = screen,
        route = SettingsRoute.LoRa,
    )

    @Test
    fun blankQueryMatchesNothing() {
        assertEquals(emptyList(), SettingsSearchMatcher.rank(listOf(entry("Hop limit")), "   "))
    }

    @Test
    fun anExactNameOutranksAPrefixWhichOutranksAMentionInTheExplanation() {
        val entries =
            listOf(
                entry("Rebroadcast mode", description = "How this node handles hops"),
                entry("Hops"),
                entry("Hop limit"),
            )

        val titles = SettingsSearchMatcher.rank(entries, "hops").map { it.title }

        assertEquals(listOf("Hops", "Rebroadcast mode"), titles)
    }

    @Test
    fun aNameAndExplanationHitOutranksANameHitAlone() {
        val both = entry("Hop limit", description = "The maximum hop limit to use")
        val nameOnly = entry("Hop behaviour")

        val ranked = SettingsSearchMatcher.rank(listOf(nameOnly, both), "hop limit")

        assertEquals("Hop limit", ranked.first().title)
    }

    @Test
    fun matchingFoldsCase() {
        assertTrue(SettingsSearchMatcher.rank(listOf(entry("LoRa region")), "lora").isNotEmpty())
        assertTrue(SettingsSearchMatcher.rank(listOf(entry("lora region")), "LORA").isNotEmpty())
    }

    @Test
    fun theScreenNameIsMatchedSoAControlIsFoundByItsScreen() {
        val ranked = SettingsSearchMatcher.rank(listOf(entry("Enabled", screen = "Bluetooth")), "bluetooth")

        assertEquals(listOf("Enabled"), ranked.map { it.title })
    }

    @Test
    fun equalScoresOrderByNameSoResultsDoNotShuffle() {
        val entries = listOf(entry("Zulu mode"), entry("Alpha mode"), entry("Mike mode"))

        val titles = SettingsSearchMatcher.rank(entries, "mode").map { it.title }

        assertEquals(listOf("Alpha mode", "Mike mode", "Zulu mode"), titles)
    }
}
