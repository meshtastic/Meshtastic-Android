/*
 * Copyright (c) 2025 Meshtastic LLC
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
package org.meshtastic.core.service.filter

import co.touchlab.kermit.Logger
import org.meshtastic.core.prefs.filter.FilterPrefs
import java.util.regex.PatternSyntaxException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service for filtering messages based on user-configured filter words.
 * Supports both plain text word matching and regex patterns.
 */
@Singleton
class MessageFilterService @Inject constructor(
    private val filterPrefs: FilterPrefs,
) {
    private var compiledPatterns: List<Regex> = emptyList()

    init {
        rebuildPatterns()
    }

    /**
     * Determines if a message should be filtered based on the configured filter words.
     *
     * @param message The message text to check.
     * @param contactKey The contact key for the message (optional, for logging).
     * @param isFilteringDisabled Whether filtering is disabled for this contact.
     * @return true if the message should be filtered, false otherwise.
     */
    fun shouldFilter(
        message: String,
        contactKey: String? = null,
        isFilteringDisabled: Boolean = false,
    ): Boolean {
        if (!filterPrefs.filterEnabled) return false
        if (compiledPatterns.isEmpty()) return false
        if (isFilteringDisabled) return false

        val textToCheck = message.take(MAX_CHECK_LENGTH)
        return compiledPatterns.any { it.containsMatchIn(textToCheck) }
    }

    /**
     * Rebuilds the compiled regex patterns from the current filter words.
     * Should be called whenever the filter words are updated.
     */
    fun rebuildPatterns() {
        compiledPatterns = filterPrefs.filterWords.mapNotNull { word ->
            try {
                if (word.startsWith(REGEX_PREFIX)) {
                    Regex(word.removePrefix(REGEX_PREFIX), RegexOption.IGNORE_CASE)
                } else {
                    Regex("\\b${Regex.escape(word)}\\b", RegexOption.IGNORE_CASE)
                }
            } catch (e: PatternSyntaxException) {
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
