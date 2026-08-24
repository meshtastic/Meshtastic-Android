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

/** Implementation of [MessageFilter] that uses regex and plain text matching. */
@Single
class MessageFilterImpl(private val filterPrefs: FilterPrefs) : MessageFilter {
    private var compiledPatterns: List<Regex> = emptyList()

    init {
        rebuildPatterns()
    }

    override fun shouldFilter(message: String, isFilteringDisabled: Boolean): Boolean {
        if (!filterPrefs.filterEnabled.value || compiledPatterns.isEmpty() || isFilteringDisabled) {
            return false
        }
        val textToCheck = message.take(MAX_CHECK_LENGTH)
        return compiledPatterns.any { it.containsMatchIn(textToCheck) }
    }

    override fun rebuildPatterns() {
        compiledPatterns =
            filterPrefs.filterWords.value.mapNotNull { word ->
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
    }

    companion object {
        private const val MAX_CHECK_LENGTH = 10_000
        private const val REGEX_PREFIX = "regex:"
    }
}
