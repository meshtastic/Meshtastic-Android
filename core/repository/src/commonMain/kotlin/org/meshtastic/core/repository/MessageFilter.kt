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
package org.meshtastic.core.repository

/** Interface for filtering messages based on user-configured filter words. */
interface MessageFilter {
    /**
     * Determines if a message should be filtered.
     *
     * @param message The message text to check.
     * @param isFilteringDisabled Whether filtering is disabled for the current contact.
     * @return true if the message should be filtered, false otherwise.
     */
    fun shouldFilter(message: String, isFilteringDisabled: Boolean = false): Boolean

    /** Rebuilds the internal filter patterns. Should be called after filter words are updated. */
    fun rebuildPatterns()
}
