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

import kotlinx.coroutines.flow.StateFlow

/** Reactive interface for analytics-related preferences. */
interface AnalyticsPrefs {
    val analyticsAllowed: StateFlow<Boolean>

    fun setAnalyticsAllowed(allowed: Boolean)

    val installId: StateFlow<String>
}

/** Reactive interface for homoglyph encoding preferences. */
interface HomoglyphPrefs {
    val homoglyphEncodingEnabled: StateFlow<Boolean>

    fun setHomoglyphEncodingEnabled(enabled: Boolean)
}

/** Reactive interface for message filtering preferences. */
interface FilterPrefs {
    val filterEnabled: StateFlow<Boolean>

    fun setFilterEnabled(enabled: Boolean)

    val filterWords: StateFlow<Set<String>>

    fun setFilterWords(words: Set<String>)
}

/** Reactive interface for mesh log preferences. */
interface MeshLogPrefs {
    val retentionDays: StateFlow<Int>

    fun setRetentionDays(days: Int)

    val loggingEnabled: StateFlow<Boolean>

    fun setLoggingEnabled(enabled: Boolean)

    companion object {
        const val DEFAULT_RETENTION_DAYS = 30
        const val MIN_RETENTION_DAYS = -1
        const val MAX_RETENTION_DAYS = 365
    }
}

/** Reactive interface for emoji preferences. */
interface CustomEmojiPrefs {
    val customEmojiFrequency: StateFlow<String?>

    fun setCustomEmojiFrequency(frequency: String?)
}

/** Reactive interface for general UI preferences. */
@Suppress("TooManyFunctions")
interface UiPrefs {
    val appIntroCompleted: StateFlow<Boolean>

    fun setAppIntroCompleted(completed: Boolean)

    val theme: StateFlow<Int>

    fun setTheme(value: Int)

    val contrastLevel: StateFlow<Int>

    fun setContrastLevel(value: Int)

    val locale: StateFlow<String>

    fun setLocale(languageTag: String)

    val nodeSort: StateFlow<Int>

    fun setNodeSort(value: Int)

    val includeUnknown: StateFlow<Boolean>

    fun setIncludeUnknown(value: Boolean)

    val excludeInfrastructure: StateFlow<Boolean>

    fun setExcludeInfrastructure(value: Boolean)

    val onlyOnline: StateFlow<Boolean>

    fun setOnlyOnline(value: Boolean)

    val onlyDirect: StateFlow<Boolean>

    fun setOnlyDirect(value: Boolean)

    val showIgnored: StateFlow<Boolean>

    fun setShowIgnored(value: Boolean)

    val excludeMqtt: StateFlow<Boolean>

    fun setExcludeMqtt(value: Boolean)

    val hasShownNotPairedWarning: StateFlow<Boolean>

    fun setHasShownNotPairedWarning(shown: Boolean)

    val showQuickChat: StateFlow<Boolean>

    fun setShowQuickChat(show: Boolean)

    fun shouldProvideNodeLocation(nodeNum: Int): StateFlow<Boolean>

    fun setShouldProvideNodeLocation(nodeNum: Int, provide: Boolean)
}

/** Reactive interface for notification preferences. */
interface NotificationPrefs {
    val messagesEnabled: StateFlow<Boolean>

    fun setMessagesEnabled(enabled: Boolean)

    val nodeEventsEnabled: StateFlow<Boolean>

    fun setNodeEventsEnabled(enabled: Boolean)

    val lowBatteryEnabled: StateFlow<Boolean>

    fun setLowBatteryEnabled(enabled: Boolean)
}

/** Reactive interface for general map preferences. */
interface MapPrefs {
    val mapStyle: StateFlow<Int>

    fun setMapStyle(style: Int)

    val showOnlyFavorites: StateFlow<Boolean>

    fun setShowOnlyFavorites(show: Boolean)

    val showWaypointsOnMap: StateFlow<Boolean>

    fun setShowWaypointsOnMap(show: Boolean)

    val showPrecisionCircleOnMap: StateFlow<Boolean>

    fun setShowPrecisionCircleOnMap(show: Boolean)

    val lastHeardFilter: StateFlow<Long>

    fun setLastHeardFilter(seconds: Long)

    val lastHeardTrackFilter: StateFlow<Long>

    fun setLastHeardTrackFilter(seconds: Long)
}

/** Reactive interface for map consent. */
interface MapConsentPrefs {
    fun shouldReportLocation(nodeNum: Int?): StateFlow<Boolean>

    fun setShouldReportLocation(nodeNum: Int?, report: Boolean)
}

/** Reactive interface for map tile provider settings. */
interface MapTileProviderPrefs {
    val customTileProviders: StateFlow<String?>

    fun setCustomTileProviders(providers: String?)
}

/** Reactive interface for radio settings. */
interface RadioPrefs {
    val devAddr: StateFlow<String?>

    /** The persisted user-visible name of the connected device (e.g. "Meshtastic_1234"). */
    val devName: StateFlow<String?>

    fun setDevAddr(address: String?)

    fun setDevName(name: String?)
}

fun RadioPrefs.isBle() = devAddr.value?.startsWith("x") == true

fun RadioPrefs.isSerial() = devAddr.value?.startsWith("s") == true

fun RadioPrefs.isMock() = devAddr.value?.startsWith("m") == true

fun RadioPrefs.isTcp() = devAddr.value?.startsWith("t") == true

fun RadioPrefs.isNoop() = devAddr.value?.startsWith("n") == true

/** Reactive interface for mesh connection settings. */
interface MeshPrefs {
    val deviceAddress: StateFlow<String?>

    fun setDeviceAddress(address: String?)

    fun getStoreForwardLastRequest(address: String?): StateFlow<Int>

    fun setStoreForwardLastRequest(address: String?, timestamp: Int)
}

/** Reactive interface for TAK server settings. */
interface TakPrefs {
    val isTakServerEnabled: StateFlow<Boolean>

    fun setTakServerEnabled(enabled: Boolean)
}

/** Consolidated interface for all application preferences. */
interface AppPreferences {
    val analytics: AnalyticsPrefs
    val homoglyph: HomoglyphPrefs
    val filter: FilterPrefs
    val meshLog: MeshLogPrefs
    val emoji: CustomEmojiPrefs
    val ui: UiPrefs
    val map: MapPrefs
    val mapConsent: MapConsentPrefs
    val mapTileProvider: MapTileProviderPrefs
    val radio: RadioPrefs
    val mesh: MeshPrefs
    val tak: TakPrefs
}
