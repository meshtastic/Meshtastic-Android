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
package org.meshtastic.core.data.manager

import co.touchlab.kermit.Logger
import org.koin.core.annotation.Single
import org.meshtastic.core.repository.FilterPrefs
import org.meshtastic.core.repository.MessageFilter
import kotlin.concurrent.Volatile

/** Implementation of [MessageFilter] that uses regex and plain text matching. */
@Single
class MessageFilterImpl(private val filterPrefs: FilterPrefs) : MessageFilter {
    private class Compiled(val words: Set<String>, val patterns: List<Regex>)

    // Keyed on the words it was built from, so a DataStore load or write after construction is picked up.
    @Volatile private var compiled = Compiled(emptySet(), emptyList())

    override fun shouldFilter(message: String, isFilteringDisabled: Boolean): Boolean {
        if (!filterPrefs.filterEnabled.value || isFilteringDisabled) return false
        val textToCheck = message.take(MAX_CHECK_LENGTH)
        return currentPatterns().any { it.containsMatchIn(textToCheck) }
    }

    override fun rebuildPatterns() {
        val words = filterPrefs.filterWords.value
        compiled = Compiled(words, compile(words))
    }

    private fun currentPatterns(): List<Regex> {
        val words = filterPrefs.filterWords.value
        val current = compiled
        if (current.words == words) return current.patterns
        return compile(words).also { compiled = Compiled(words, it) }
    }

    private fun compile(words: Set<String>): List<Regex> = words.mapNotNull { word ->
        try {
            if (word.startsWith(REGEX_PREFIX)) {
                Regex(word.removePrefix(REGEX_PREFIX), RegexOption.IGNORE_CASE)
            } else {
                Regex("\\b${Regex.escape(word)}\\b", RegexOption.IGNORE_CASE)
            }
        } catch (e: IllegalArgumentException) {
            Logger.w { "Invalid filter pattern: $word - ${e.message}" }
            null
        }
    }

    companion object {
        private const val MAX_CHECK_LENGTH = 10_000
        private const val REGEX_PREFIX = "regex:"
    }
}
