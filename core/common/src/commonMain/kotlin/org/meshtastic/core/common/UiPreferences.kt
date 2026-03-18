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
package org.meshtastic.core.common

import kotlinx.coroutines.flow.StateFlow

@Suppress("TooManyFunctions")
interface UiPreferences {
    val appIntroCompleted: StateFlow<Boolean>
    val theme: StateFlow<Int>
    val locale: StateFlow<String>
    val nodeSort: StateFlow<Int>
    val includeUnknown: StateFlow<Boolean>
    val excludeInfrastructure: StateFlow<Boolean>
    val onlyOnline: StateFlow<Boolean>
    val onlyDirect: StateFlow<Boolean>
    val showIgnored: StateFlow<Boolean>
    val excludeMqtt: StateFlow<Boolean>

    fun setLocale(languageTag: String)

    fun setAppIntroCompleted(completed: Boolean)

    fun setTheme(value: Int)

    fun setNodeSort(value: Int)

    fun setIncludeUnknown(value: Boolean)

    fun setExcludeInfrastructure(value: Boolean)

    fun setOnlyOnline(value: Boolean)

    fun setOnlyDirect(value: Boolean)

    fun setShowIgnored(value: Boolean)

    fun setExcludeMqtt(value: Boolean)

    fun shouldProvideNodeLocation(nodeNum: Int): StateFlow<Boolean>

    fun setShouldProvideNodeLocation(nodeNum: Int, provide: Boolean)
}
