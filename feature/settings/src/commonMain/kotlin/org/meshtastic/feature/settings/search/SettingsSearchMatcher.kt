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

import org.meshtastic.core.navigation.Route

/** A catalog entry with its text resolved in the user's language, which is what the matcher reads. */
data class ResolvedSettingsEntry(
    val title: String,
    val description: String?,
    val screenTitle: String,
    val route: Route,
    /** True for settings that belong to this phone rather than to a radio. See [SettingsSearchEntry.isAppLocal]. */
    val isAppLocal: Boolean = false,
)

/**
 * Ranks settings results by where the query landed, following the weights Meshtastic-Apple's settings search shipped
 * (spec 019, FR-011): a hit on the name outranks one in the explanation, and an exact name outranks a prefix.
 *
 * Case folding is Unicode-aware, so "lora" finds "LoRa" and "prasa" does not have to be typed as "Přáša" in the wrong
 * case. Diacritics are not folded: "prasa" will not find "Přáša". That matches node search, which has the same limit.
 */
object SettingsSearchMatcher {

    private const val EXACT_TITLE = 100
    private const val TITLE_PREFIX = 75
    private const val TITLE_CONTAINS = 50
    private const val DESCRIPTION_CONTAINS = 25
    private const val SCREEN_CONTAINS = 10

    fun rank(entries: List<ResolvedSettingsEntry>, query: String): List<ResolvedSettingsEntry> {
        val needle = query.trim().lowercase()
        if (needle.isEmpty()) return emptyList()

        return entries
            .mapNotNull { entry -> score(entry, needle).takeIf { it > 0 }?.let { entry to it } }
            // Descending score, then by name, so a term matching many entries orders the same way every time.
            .sortedWith(compareByDescending<Pair<ResolvedSettingsEntry, Int>> { it.second }.thenBy { it.first.title })
            .map { it.first }
    }

    private fun score(entry: ResolvedSettingsEntry, needle: String): Int {
        val title = entry.title.lowercase()
        var score =
            when {
                title == needle -> EXACT_TITLE
                title.startsWith(needle) -> TITLE_PREFIX
                title.contains(needle) -> TITLE_CONTAINS
                else -> 0
            }
        // A hit in the explanation and a hit in the name add, so an entry matching both outranks one matching either.
        if (entry.description?.lowercase()?.contains(needle) == true) score += DESCRIPTION_CONTAINS
        if (entry.screenTitle.lowercase().contains(needle)) score += SCREEN_CONTAINS
        return score
    }
}
