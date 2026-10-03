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
package org.meshtastic.core.konsist

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.declaration.KoFileDeclaration
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Production code takes its coroutine scope from an owner (`ApplicationCoroutineScope`, `ServiceScope`, a lifecycle
 * scope or a constructor parameter) rather than calling `CoroutineScope(...)` itself: a self-made scope has no owner to
 * cancel it, no exception handler, and nothing a test can swap in. The allowlist is the current set of sites, one entry
 * per call, keyed `<module>/<source set>/<file>`. It is pinned exactly, so a removed site comes off the list too. Test
 * source sets and `core:testing` build scopes on purpose and are not scanned.
 */
class CoroutineScopeConstructionTest {

    private val allowed =
        listOf(
            "androidApp/google/AppFunctionStateSync.kt",
            "androidApp/google/GoogleMapsPrefs.kt",
            "androidApp/main/MeshUtilApplication.kt",
            "core/data/commonMain/FromRadioPacketHandlerImpl.kt",
            "core/data/commonMain/SingleFlightRefresher.kt",
            "core/database/commonMain/DatabaseManager.kt",
            "core/datastore/commonMain/CoreDatastoreModule.kt",
            "core/network/androidMain/SerialRadioTransport.kt",
            "core/network/commonMain/BleRadioTransport.kt",
            "core/network/commonMain/BleRadioTransport.kt",
            "core/network/commonMain/MQTTRepositoryImpl.kt",
            "core/network/commonMain/MockRadioTransport.kt",
            "core/network/commonMain/ReplayRadioTransport.kt",
            "core/prefs/androidMain/CorePrefsAndroidModule.kt",
            "core/prefs/commonMain/AnalyticsPrefsImpl.kt",
            "core/prefs/commonMain/AppFunctionsPrefsImpl.kt",
            "core/prefs/commonMain/CustomEmojiPrefsImpl.kt",
            "core/prefs/commonMain/DiscoveryPrefsImpl.kt",
            "core/prefs/commonMain/FilterPrefsImpl.kt",
            "core/prefs/commonMain/HomoglyphPrefsImpl.kt",
            "core/prefs/commonMain/MapConsentPrefsImpl.kt",
            "core/prefs/commonMain/MapPrefsImpl.kt",
            "core/prefs/commonMain/MapTileProviderPrefsImpl.kt",
            "core/prefs/commonMain/MeshBeaconPrefsImpl.kt",
            "core/prefs/commonMain/MeshLogPrefsImpl.kt",
            "core/prefs/commonMain/MeshPrefsImpl.kt",
            "core/prefs/commonMain/NotificationPrefsImpl.kt",
            "core/prefs/commonMain/RadioPrefsImpl.kt",
            "core/prefs/commonMain/TakPrefsImpl.kt",
            "core/prefs/commonMain/UiPrefsImpl.kt",
            "core/service/androidMain/BootCompleteReceiver.kt",
            "core/service/androidMain/ConversationActionService.kt",
            "core/service/androidMain/MeshService.kt",
            "core/service/commonMain/CoreServiceModule.kt",
            "core/service/commonMain/MeshServiceOrchestrator.kt",
            "core/service/commonMain/MeshServiceOrchestrator.kt",
            "core/service/commonMain/SharedRadioInterfaceService.kt",
            "core/service/commonMain/SharedRadioInterfaceService.kt",
            "core/takserver/jvmAndroidMain/TAKClientConnection.kt",
            "desktopApp/main/DesktopMessageQueue.kt",
            "desktopApp/main/DesktopPreferencesDataSource.kt",
            "desktopApp/main/NoopStubs.kt",
            "feature/coverage/commonMain/MapterhornElevation.kt",
            "feature/discovery/commonMain/DiscoveryScanEngine.kt",
            "feature/docs/commonMain/ChirpySessionHolder.kt",
            "feature/firmware/commonMain/BleOtaTransport.kt",
            "feature/firmware/commonMain/LegacyDfuTransport.kt",
            "feature/firmware/commonMain/SecureDfuTransport.kt",
            "feature/map-maplibre/commonMain/OfflineTerrainRepository.kt",
            "feature/map/commonMain/CustomTileProviderRepository.kt",
            "feature/map/commonMain/LayerOpacityStore.kt",
            "feature/map/commonMain/MapLayersManager.kt",
            "feature/widget/main/AndroidAppWidgetUpdater.kt",
            "feature/widget/main/LocalStatsWidgetState.kt",
            "feature/wifi-provision/commonMain/NymeaWifiService.kt",
        )

    /** A call to the `CoroutineScope(context)` factory; `rememberCoroutineScope()` has no word break before it. */
    private val construction = Regex("""\bCoroutineScope\(""")

    private val productionFiles =
        Konsist.scopeFromProject()
            .files
            .filterNot { it.isNestedAgentWorktree() }
            .filterNot { it.isTestSource }
            .filterNot { it.scanPath.startsWith("/core/testing/") }

    private data class Site(val key: String, val location: String)

    private val KoFileDeclaration.allowlistKey: String
        get() = "${scanPath.removePrefix("/").substringBefore("/src/")}/$sourceSet/${scanPath.substringAfterLast('/')}"

    private val sites: List<Site> = productionFiles.flatMap { file ->
        file.text.lines().withIndex().mapNotNull { (index, line) ->
            val code = line.trim()
            val isComment = code.startsWith("//") || code.startsWith("*") || code.startsWith("/*")
            if (!isComment && construction.containsMatchIn(code)) {
                Site(file.allowlistKey, "${file.scanPath}:${index + 1}: $code")
            } else {
                null
            }
        }
    }

    @Test
    fun `the scan actually reaches production sources`() {
        val paths = productionFiles.map { it.scanPath }

        assertTrue(paths.isNotEmpty(), emptyScanMessage("production source scan"))
        assertTrue(
            paths.any { it.endsWith("/core/prefs/src/commonMain/kotlin/org/meshtastic/core/prefs/ui/UiPrefsImpl.kt") },
            "expected core/prefs commonMain sources in scope; got ${paths.size} files, e.g. ${paths.take(3)}",
        )
        assertTrue(sites.isNotEmpty(), "found no CoroutineScope( call at all, so the pattern matches nothing")
    }

    @Test
    fun `no production code constructs a CoroutineScope outside the allowlist`() {
        val allowedCounts = allowed.groupingBy { it }.eachCount()
        val offenders =
            sites.groupBy { it.key }.filter { (key, found) -> found.size > (allowedCounts[key] ?: 0) }.values.flatten()

        assertTrue(
            offenders.isEmpty(),
            "Inject ApplicationCoroutineScope or ServiceScope (or take a CoroutineScope parameter) instead of " +
                "constructing a scope in the class. New CoroutineScope( sites:\n" +
                offenders.joinToString("\n") { it.location },
        )
    }

    @Test
    fun `the allowlist still matches real sites`() {
        val foundCounts = sites.groupingBy { it.key }.eachCount()
        val stale =
            allowed.groupingBy { it }.eachCount().filter { (key, count) -> count > (foundCounts[key] ?: 0) }.keys

        assertTrue(
            stale.isEmpty(),
            "These files construct fewer CoroutineScopes than the allowlist says; take the extra entries off so " +
                "this rule keeps verifying something:\n" +
                stale.joinToString("\n"),
        )
    }
}
