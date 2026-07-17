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
import org.meshtastic.core.model.DeviceType

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
    val preferredSkinToneIndex: StateFlow<Int>

    fun setCustomEmojiFrequency(frequency: String?)

    fun setPreferredSkinToneIndex(index: Int)
}

/** Reactive interface for general UI preferences. */
@Suppress("TooManyFunctions")
interface UiPrefs {
    val appIntroCompleted: StateFlow<Boolean>

    fun setAppIntroCompleted(completed: Boolean)

    val theme: StateFlow<Int>

    fun setTheme(value: Int)

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

    /**
     * Whether to apply an event edition's ambient theme (accent wash + custom typeface) app-wide (opt-out; default on).
     */
    val eventThemeEnabled: StateFlow<Boolean>

    fun setEventThemeEnabled(enabled: Boolean)

    /** Whether BLE scanning should auto-start when the Connections screen is opened. */
    val bleAutoScan: StateFlow<Boolean>

    fun setBleAutoScan(enabled: Boolean)

    /** Whether NSD network scanning should auto-start when the Connections screen is opened. */
    val networkAutoScan: StateFlow<Boolean>

    fun setNetworkAutoScan(enabled: Boolean)

    /** User-selected Connections transport pane, or null when the screen should derive a default. */
    val selectedConnectionTransport: StateFlow<DeviceType?>

    fun setSelectedConnectionTransport(type: DeviceType)

    /** Keys for firmware-update notifications already scheduled on this device. */
    val firmwareUpdateNotificationKeys: StateFlow<Set<String>>

    /** Records a notification key after the platform notification has been scheduled successfully. */
    fun recordFirmwareUpdateNotificationKey(key: String)

    fun shouldProvideNodeLocation(nodeNum: Int): StateFlow<Boolean>

    fun setShouldProvideNodeLocation(nodeNum: Int, provide: Boolean)

    // Node list layout preferences

    /** Active density mode stored as the enum name (e.g. "COMPLETE", "COMPACT"). */
    val nodeListDensity: StateFlow<String>

    fun setNodeListDensity(value: String)

    val shouldShowPower: StateFlow<Boolean>

    fun setShouldShowPower(value: Boolean)

    val shouldShowLastHeard: StateFlow<Boolean>

    fun setShouldShowLastHeard(value: Boolean)

    val lastHeardIsRelative: StateFlow<Boolean>

    fun setLastHeardIsRelative(value: Boolean)

    val shouldShowLocation: StateFlow<Boolean>

    fun setShouldShowLocation(value: Boolean)

    val shouldShowHops: StateFlow<Boolean>

    fun setShouldShowHops(value: Boolean)

    val shouldShowSignal: StateFlow<Boolean>

    fun setShouldShowSignal(value: Boolean)

    val shouldShowChannel: StateFlow<Boolean>

    fun setShouldShowChannel(value: Boolean)

    val shouldShowRole: StateFlow<Boolean>

    fun setShouldShowRole(value: Boolean)

    val shouldShowTelemetry: StateFlow<Boolean>

    fun setShouldShowTelemetry(value: Boolean)
}

/** Reactive interface for notification preferences. */
interface NotificationPrefs {
    val messagesEnabled: StateFlow<Boolean>

    fun setMessagesEnabled(enabled: Boolean)

    val nodeEventsEnabled: StateFlow<Boolean>

    fun setNodeEventsEnabled(enabled: Boolean)

    val nodeEventsAutoDisabledForEvent: StateFlow<Boolean>

    fun setNodeEventsAutoDisabledForEvent(disabled: Boolean)

    val lowBatteryEnabled: StateFlow<Boolean>

    fun setLowBatteryEnabled(enabled: Boolean)

    /**
     * Waypoint ids of foreign (not locally-created) geofences the user has opted in to receiving crossing alerts for.
     * Geofences are mesh-broadcast, so by default only the creator is alerted; this is the per-geofence opt-in.
     */
    val geofenceAlertOptIns: StateFlow<Set<Int>>

    fun setGeofenceAlertOptIn(waypointId: Int, enabled: Boolean)
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

    /** URIs of imported map layers the user has toggled off; a layer is visible unless its URI is in this set. */
    val hiddenLayerUrls: StateFlow<Set<String>>

    /** Atomically mutate [hiddenLayerUrls]; [transform] runs against the persisted value, avoiding lost updates. */
    fun updateHiddenLayerUrls(transform: (Set<String>) -> Set<String>)

    /** Persisted [hiddenLayerUrls]; suspends for the first disk load to avoid a cold-start empty default. */
    suspend fun awaitHiddenLayerUrls(): Set<String>

    /** Persisted network (URL-backed) map layers, each encoded as `id|:|name|:|uri`. */
    val networkMapLayers: StateFlow<Set<String>>

    /** Atomically mutate [networkMapLayers]; [transform] runs against the persisted value, avoiding lost updates. */
    fun updateNetworkMapLayers(transform: (Set<String>) -> Set<String>)

    /** Persisted [networkMapLayers]; suspends for the first disk load to avoid a cold-start empty default. */
    suspend fun awaitNetworkMapLayers(): Set<String>
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

/** Reactive interface for App Functions (system AI integration) preferences. */
interface AppFunctionsPrefs {
    val masterEnabled: StateFlow<Boolean>

    fun setMasterEnabled(enabled: Boolean)

    val sendMessageEnabled: StateFlow<Boolean>

    fun setSendMessageEnabled(enabled: Boolean)

    val getMeshStatusEnabled: StateFlow<Boolean>

    fun setGetMeshStatusEnabled(enabled: Boolean)

    val getNodeListEnabled: StateFlow<Boolean>

    fun setGetNodeListEnabled(enabled: Boolean)

    val getChannelInfoEnabled: StateFlow<Boolean>

    fun setGetChannelInfoEnabled(enabled: Boolean)

    val getDeviceStatusEnabled: StateFlow<Boolean>

    fun setGetDeviceStatusEnabled(enabled: Boolean)

    val getNodeDetailsEnabled: StateFlow<Boolean>

    fun setGetNodeDetailsEnabled(enabled: Boolean)

    val getMeshMetricsEnabled: StateFlow<Boolean>

    fun setGetMeshMetricsEnabled(enabled: Boolean)

    val getRecentMessagesEnabled: StateFlow<Boolean>

    fun setGetRecentMessagesEnabled(enabled: Boolean)

    val getUnreadSummaryEnabled: StateFlow<Boolean>

    fun setGetUnreadSummaryEnabled(enabled: Boolean)
}

/** Consolidated interface for all application preferences. */
interface AppPreferences {
    val analytics: AnalyticsPrefs
    val appFunctions: AppFunctionsPrefs
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
    val discovery: DiscoveryPrefs
}

/** Reactive interface for Local Mesh Discovery scan preferences. */
interface DiscoveryPrefs {
    val dwellMinutes: StateFlow<Int>

    fun setDwellMinutes(minutes: Int)

    val selectedPresets: StateFlow<Set<String>>

    fun setSelectedPresets(presets: Set<String>)

    val aiEnabled: StateFlow<Boolean>

    fun setAiEnabled(enabled: Boolean)

    val topologyOverlayEnabled: StateFlow<Boolean>

    fun setTopologyOverlayEnabled(enabled: Boolean)

    companion object {
        const val DEFAULT_DWELL_MINUTES = 15
    }
}

/**
 * Reactive persistence for received Mesh Beacon invitations. Records are opaque, self-describing strings (see
 * `MeshBeaconOffer.encode`) so this prefs layer stays free of proto/model types.
 */
interface MeshBeaconPrefs {
    val storedBeacons: StateFlow<List<String>>

    fun setStoredBeacons(records: List<String>)
}
