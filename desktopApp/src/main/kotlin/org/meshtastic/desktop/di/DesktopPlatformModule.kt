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
package org.meshtastic.desktop.di

import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.core.okio.OkioSerializer
import androidx.datastore.core.okio.OkioStorage
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import okio.FileSystem
import okio.Path.Companion.toPath
import org.koin.core.qualifier.named
import org.koin.dsl.module
import org.meshtastic.core.common.BuildConfigProvider
import org.meshtastic.core.common.di.PROCESS_LIFECYCLE
import org.meshtastic.core.database.desktopDataDir
import org.meshtastic.core.datastore.di.CoreChannelSetDataStore
import org.meshtastic.core.datastore.di.CoreLocalConfigDataStore
import org.meshtastic.core.datastore.di.CoreLocalStatsDataStore
import org.meshtastic.core.datastore.di.CoreModuleConfigDataStore
import org.meshtastic.core.datastore.di.CorePreferencesDataStore
import org.meshtastic.core.datastore.di.DataStoreScope
import org.meshtastic.core.datastore.di.asCoreChannelSetDataStore
import org.meshtastic.core.datastore.di.asCoreLocalConfigDataStore
import org.meshtastic.core.datastore.di.asCoreLocalStatsDataStore
import org.meshtastic.core.datastore.di.asCoreModuleConfigDataStore
import org.meshtastic.core.datastore.di.asCorePreferencesDataStore
import org.meshtastic.core.datastore.di.asDataStoreScope
import org.meshtastic.core.datastore.serializer.ChannelSetSerializer
import org.meshtastic.core.datastore.serializer.LocalConfigSerializer
import org.meshtastic.core.datastore.serializer.LocalStatsSerializer
import org.meshtastic.core.datastore.serializer.ModuleConfigSerializer
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.prefs.di.AnalyticsDataStore
import org.meshtastic.core.prefs.di.AppDataStore
import org.meshtastic.core.prefs.di.CustomEmojiDataStore
import org.meshtastic.core.prefs.di.FilterDataStore
import org.meshtastic.core.prefs.di.HomoglyphEncodingDataStore
import org.meshtastic.core.prefs.di.MapConsentDataStore
import org.meshtastic.core.prefs.di.MapDataStore
import org.meshtastic.core.prefs.di.MapTileProviderDataStore
import org.meshtastic.core.prefs.di.MeshDataStore
import org.meshtastic.core.prefs.di.MeshLogDataStore
import org.meshtastic.core.prefs.di.RadioDataStore
import org.meshtastic.core.prefs.di.UiDataStore
import org.meshtastic.core.prefs.di.asAnalyticsDataStore
import org.meshtastic.core.prefs.di.asAppDataStore
import org.meshtastic.core.prefs.di.asCustomEmojiDataStore
import org.meshtastic.core.prefs.di.asFilterDataStore
import org.meshtastic.core.prefs.di.asHomoglyphEncodingDataStore
import org.meshtastic.core.prefs.di.asMapConsentDataStore
import org.meshtastic.core.prefs.di.asMapDataStore
import org.meshtastic.core.prefs.di.asMapTileProviderDataStore
import org.meshtastic.core.prefs.di.asMeshDataStore
import org.meshtastic.core.prefs.di.asMeshLogDataStore
import org.meshtastic.core.prefs.di.asRadioDataStore
import org.meshtastic.core.prefs.di.asUiDataStore
import org.meshtastic.desktop.DesktopBuildConfig
import org.meshtastic.proto.ChannelSet
import org.meshtastic.proto.LocalConfig
import org.meshtastic.proto.LocalModuleConfig
import org.meshtastic.proto.LocalStats

/** Creates a file-backed [DataStore]<[Preferences]> at the given path under the data directory. */
private fun prefsStore(name: String, scope: DataStoreScope): DataStore<Preferences> {
    val dir = desktopDataDir() + "/datastore"
    FileSystem.SYSTEM.createDirectories(dir.toPath())
    return PreferenceDataStoreFactory.createWithPath(
        corruptionHandler = ReplaceFileCorruptionHandler(produceNewData = { emptyPreferences() }),
        scope = scope,
        produceFile = { "$dir/$name.preferences_pb".toPath() },
    )
}

/**
 * Synthetic [LifecycleOwner] that stays permanently in [Lifecycle.State.RESUMED]. Replaces Android's
 * `ProcessLifecycleOwner` for desktop.
 */
private class DesktopProcessLifecycleOwner : LifecycleOwner {
    private val registry = LifecycleRegistry(this)

    init {
        registry.currentState = Lifecycle.State.RESUMED
    }

    override val lifecycle: Lifecycle
        get() = registry
}

/**
 * Desktop platform infrastructure module.
 *
 * Provides all platform-specific bindings that the real KMP `commonMain` implementations need:
 * - Named [DataStore]<[Preferences]> instances (12 preference stores + 1 core preferences store)
 * - Proto [DataStore] instances (LocalConfig, ModuleConfig, ChannelSet, LocalStats)
 * - [Lifecycle] (`ProcessLifecycle`)
 * - [BuildConfigProvider]
 */
fun desktopPlatformModule() = module {
    // Application-lifetime scope shared by all DataStore instances. Per the DataStore docs:
    // "The Job within this context dictates the lifecycle of the DataStore's internal operations.
    // Ensure it is an application-scoped context that is not canceled by UI lifecycle events."
    // DataStore has no close() API — the in-memory cache is released only when this Job is cancelled
    // (at process exit). Using SupervisorJob so a single store's failure doesn't cascade.
    single<DataStoreScope> { CoroutineScope(get<CoroutineDispatchers>().io + SupervisorJob()).asDataStoreScope() }

    includes(desktopPreferencesDataStoreModule(), desktopProtoDataStoreModule())

    // -- Build config (values generated at build time by generateDesktopBuildConfig) --
    single<BuildConfigProvider> {
        object : BuildConfigProvider {
            override val isDebug: Boolean = DesktopBuildConfig.IS_DEBUG
            override val applicationId: String = DesktopBuildConfig.APPLICATION_ID
            override val versionCode: Int = DesktopBuildConfig.VERSION_CODE
            override val versionName: String = DesktopBuildConfig.VERSION_NAME
            override val absoluteMinFwVersion: String = DesktopBuildConfig.ABS_MIN_FW_VERSION
            override val minFwVersion: String = DesktopBuildConfig.MIN_FW_VERSION
        }
    }

    // -- Process Lifecycle (stays RESUMED forever on desktop) --
    single(named(PROCESS_LIFECYCLE)) { DesktopProcessLifecycleOwner().lifecycle }
}

/** Typed preference-datastore singletons for each preference domain. */
private fun desktopPreferencesDataStoreModule() = module {
    single<AnalyticsDataStore> { prefsStore("analytics", get()).asAnalyticsDataStore() }
    single<HomoglyphEncodingDataStore> { prefsStore("homoglyph_encoding", get()).asHomoglyphEncodingDataStore() }
    single<AppDataStore> { prefsStore("app", get()).asAppDataStore() }
    single<CustomEmojiDataStore> { prefsStore("custom_emoji", get()).asCustomEmojiDataStore() }
    single<MapDataStore> { prefsStore("map", get()).asMapDataStore() }
    single<MapConsentDataStore> { prefsStore("map_consent", get()).asMapConsentDataStore() }
    single<MapTileProviderDataStore> { prefsStore("map_tile_provider", get()).asMapTileProviderDataStore() }
    single<MeshDataStore> { prefsStore("mesh", get()).asMeshDataStore() }
    single<RadioDataStore> { prefsStore("radio", get()).asRadioDataStore() }
    single<UiDataStore> { prefsStore("ui", get()).asUiDataStore() }
    single<MeshLogDataStore> { prefsStore("meshlog", get()).asMeshLogDataStore() }
    single<FilterDataStore> { prefsStore("filter", get()).asFilterDataStore() }
    single<CorePreferencesDataStore> { prefsStore("core_preferences", get()).asCorePreferencesDataStore() }
}

/** The path is an on-disk identity — changing it orphans existing user data. */
private fun <T> protoStore(
    serializer: OkioSerializer<T>,
    path: String,
    produceNewData: () -> T,
    scope: DataStoreScope,
): DataStore<T> = DataStoreFactory.create(
    storage = OkioStorage(fileSystem = FileSystem.SYSTEM, serializer = serializer, producePath = { path.toPath() }),
    corruptionHandler = ReplaceFileCorruptionHandler(produceNewData = { produceNewData() }),
    scope = scope,
)

/** Proto [DataStore] instances (OkioStorage-backed). */
private fun desktopProtoDataStoreModule() = module {
    val protoDir = desktopDataDir() + "/datastore"
    FileSystem.SYSTEM.createDirectories(protoDir.toPath())

    single<CoreLocalConfigDataStore> {
        protoStore(LocalConfigSerializer, "$protoDir/local_config.pb", { LocalConfig() }, get())
            .asCoreLocalConfigDataStore()
    }

    single<CoreModuleConfigDataStore> {
        protoStore(ModuleConfigSerializer, "$protoDir/module_config.pb", { LocalModuleConfig() }, get())
            .asCoreModuleConfigDataStore()
    }

    single<CoreChannelSetDataStore> {
        protoStore(ChannelSetSerializer, "$protoDir/channel_set.pb", { ChannelSet() }, get())
            .asCoreChannelSetDataStore()
    }

    single<CoreLocalStatsDataStore> {
        protoStore(LocalStatsSerializer, "$protoDir/local_stats.pb", { LocalStats() }, get())
            .asCoreLocalStatsDataStore()
    }
}
