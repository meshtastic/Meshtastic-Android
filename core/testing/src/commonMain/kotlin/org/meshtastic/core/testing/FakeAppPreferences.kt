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
package org.meshtastic.core.testing

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.meshtastic.core.repository.AnalyticsPrefs
import org.meshtastic.core.repository.AppPreferences
import org.meshtastic.core.repository.CustomEmojiPrefs
import org.meshtastic.core.repository.FilterPrefs
import org.meshtastic.core.repository.HomoglyphPrefs
import org.meshtastic.core.repository.MapConsentPrefs
import org.meshtastic.core.repository.MapPrefs
import org.meshtastic.core.repository.MapTileProviderPrefs
import org.meshtastic.core.repository.MeshPrefs
import org.meshtastic.core.repository.RadioPrefs
import org.meshtastic.core.repository.UiPrefs

class FakeAnalyticsPrefs : AnalyticsPrefs {
    override val analyticsAllowed = MutableStateFlow(true)

    override fun setAnalyticsAllowed(allowed: Boolean) {
        analyticsAllowed.value = allowed
    }

    override val installId = MutableStateFlow("fake-install-id")
}

class FakeHomoglyphPrefs : HomoglyphPrefs {
    override val homoglyphEncodingEnabled = MutableStateFlow(false)

    override fun setHomoglyphEncodingEnabled(enabled: Boolean) {
        homoglyphEncodingEnabled.value = enabled
    }
}

class FakeFilterPrefs : FilterPrefs {
    override val filterEnabled = MutableStateFlow(false)

    override fun setFilterEnabled(enabled: Boolean) {
        filterEnabled.value = enabled
    }

    override val filterWords = MutableStateFlow(emptySet<String>())

    override fun setFilterWords(words: Set<String>) {
        filterWords.value = words
    }
}

class FakeCustomEmojiPrefs : CustomEmojiPrefs {
    override val customEmojiFrequency = MutableStateFlow<String?>(null)

    override fun setCustomEmojiFrequency(frequency: String?) {
        customEmojiFrequency.value = frequency
    }
}

@Suppress("TooManyFunctions")
class FakeUiPrefs : UiPrefs {
    override val appIntroCompleted = MutableStateFlow(false)

    override fun setAppIntroCompleted(completed: Boolean) {
        appIntroCompleted.value = completed
    }

    override val theme = MutableStateFlow(0)

    override fun setTheme(value: Int) {
        theme.value = value
    }

    override val contrastLevel = MutableStateFlow(0)

    override fun setContrastLevel(value: Int) {
        contrastLevel.value = value
    }

    override val locale = MutableStateFlow("en")

    override fun setLocale(languageTag: String) {
        locale.value = languageTag
    }

    override val nodeSort = MutableStateFlow(0)

    override fun setNodeSort(value: Int) {
        nodeSort.value = value
    }

    override val includeUnknown = MutableStateFlow(true)

    override fun setIncludeUnknown(value: Boolean) {
        includeUnknown.value = value
    }

    override val excludeInfrastructure = MutableStateFlow(false)

    override fun setExcludeInfrastructure(value: Boolean) {
        excludeInfrastructure.value = value
    }

    override val onlyOnline = MutableStateFlow(false)

    override fun setOnlyOnline(value: Boolean) {
        onlyOnline.value = value
    }

    override val onlyDirect = MutableStateFlow(false)

    override fun setOnlyDirect(value: Boolean) {
        onlyDirect.value = value
    }

    override val showIgnored = MutableStateFlow(false)

    override fun setShowIgnored(value: Boolean) {
        showIgnored.value = value
    }

    override val excludeMqtt = MutableStateFlow(false)

    override fun setExcludeMqtt(value: Boolean) {
        excludeMqtt.value = value
    }

    override val hasShownNotPairedWarning = MutableStateFlow(false)

    override fun setHasShownNotPairedWarning(shown: Boolean) {
        hasShownNotPairedWarning.value = shown
    }

    override val showQuickChat = MutableStateFlow(true)

    override fun setShowQuickChat(show: Boolean) {
        showQuickChat.value = show
    }

    override val bleAutoScan = MutableStateFlow(false)

    override fun setBleAutoScan(enabled: Boolean) {
        bleAutoScan.value = enabled
    }

    override val networkAutoScan = MutableStateFlow(false)

    override fun setNetworkAutoScan(enabled: Boolean) {
        networkAutoScan.value = enabled
    }

    override val showBleTransport = MutableStateFlow(true)

    override fun setShowBleTransport(enabled: Boolean) {
        showBleTransport.value = enabled
    }

    override val showNetworkTransport = MutableStateFlow(true)

    override fun setShowNetworkTransport(enabled: Boolean) {
        showNetworkTransport.value = enabled
    }

    override val showUsbTransport = MutableStateFlow(true)

    override fun setShowUsbTransport(enabled: Boolean) {
        showUsbTransport.value = enabled
    }

    private val nodeLocationEnabled = mutableMapOf<Int, MutableStateFlow<Boolean>>()

    override fun shouldProvideNodeLocation(nodeNum: Int): StateFlow<Boolean> =
        nodeLocationEnabled.getOrPut(nodeNum) { MutableStateFlow(true) }

    override fun setShouldProvideNodeLocation(nodeNum: Int, provide: Boolean) {
        nodeLocationEnabled.getOrPut(nodeNum) { MutableStateFlow(provide) }.value = provide
    }
}

class FakeMapPrefs : MapPrefs {
    override val mapStyle = MutableStateFlow(0)

    override fun setMapStyle(style: Int) {
        mapStyle.value = style
    }

    override val showOnlyFavorites = MutableStateFlow(false)

    override fun setShowOnlyFavorites(show: Boolean) {
        showOnlyFavorites.value = show
    }

    override val showWaypointsOnMap = MutableStateFlow(true)

    override fun setShowWaypointsOnMap(show: Boolean) {
        showWaypointsOnMap.value = show
    }

    override val showPrecisionCircleOnMap = MutableStateFlow(true)

    override fun setShowPrecisionCircleOnMap(show: Boolean) {
        showPrecisionCircleOnMap.value = show
    }

    override val lastHeardFilter = MutableStateFlow(0L)

    override fun setLastHeardFilter(seconds: Long) {
        lastHeardFilter.value = seconds
    }

    override val lastHeardTrackFilter = MutableStateFlow(0L)

    override fun setLastHeardTrackFilter(seconds: Long) {
        lastHeardTrackFilter.value = seconds
    }
}

class FakeMapConsentPrefs : MapConsentPrefs {
    private val consent = mutableMapOf<Int?, MutableStateFlow<Boolean>>()

    override fun shouldReportLocation(nodeNum: Int?): StateFlow<Boolean> =
        consent.getOrPut(nodeNum) { MutableStateFlow(false) }

    override fun setShouldReportLocation(nodeNum: Int?, report: Boolean) {
        consent.getOrPut(nodeNum) { MutableStateFlow(report) }.value = report
    }
}

class FakeMapTileProviderPrefs : MapTileProviderPrefs {
    override val customTileProviders = MutableStateFlow<String?>(null)

    override fun setCustomTileProviders(providers: String?) {
        customTileProviders.value = providers
    }
}

class FakeRadioPrefs : RadioPrefs {
    override val devAddr = MutableStateFlow<String?>(null)
    override val devName = MutableStateFlow<String?>(null)

    override fun setDevAddr(address: String?) {
        devAddr.value = address
    }

    override fun setDevName(name: String?) {
        devName.value = name
    }
}

class FakeMeshPrefs : MeshPrefs {
    override val deviceAddress = MutableStateFlow<String?>(null)

    override fun setDeviceAddress(address: String?) {
        deviceAddress.value = address
    }

    private val lastRequest = mutableMapOf<String?, MutableStateFlow<Int>>()

    override fun getStoreForwardLastRequest(address: String?): StateFlow<Int> =
        lastRequest.getOrPut(address) { MutableStateFlow(0) }

    override fun setStoreForwardLastRequest(address: String?, timestamp: Int) {
        lastRequest.getOrPut(address) { MutableStateFlow(timestamp) }.value = timestamp
    }
}

class FakeAppPreferences : AppPreferences {
    override val analytics = FakeAnalyticsPrefs()
    override val homoglyph = FakeHomoglyphPrefs()
    override val filter = FakeFilterPrefs()
    override val meshLog = FakeMeshLogPrefs()
    override val emoji = FakeCustomEmojiPrefs()
    override val ui = FakeUiPrefs()
    override val map = FakeMapPrefs()
    override val mapConsent = FakeMapConsentPrefs()
    override val mapTileProvider = FakeMapTileProviderPrefs()
    override val radio = FakeRadioPrefs()
    override val mesh = FakeMeshPrefs()
    override val tak = FakeTakPrefs()
}

class FakeTakPrefs : org.meshtastic.core.repository.TakPrefs {
    override val isTakServerEnabled = MutableStateFlow(false)

    override fun setTakServerEnabled(enabled: Boolean) {
        isTakServerEnabled.value = enabled
    }
}
