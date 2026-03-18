
package org.meshtastic.core.common

import kotlinx.coroutines.flow.StateFlow

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
