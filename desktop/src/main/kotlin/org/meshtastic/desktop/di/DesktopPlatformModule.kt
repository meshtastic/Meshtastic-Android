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
import org.meshtastic.core.database.desktopDataDir
import org.meshtastic.core.datastore.di.DATASTORE_SCOPE
import org.meshtastic.core.datastore.serializer.ChannelSetSerializer
import org.meshtastic.core.datastore.serializer.LocalConfigSerializer
import org.meshtastic.core.datastore.serializer.LocalStatsSerializer
import org.meshtastic.core.datastore.serializer.ModuleConfigSerializer
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.desktop.DesktopBuildConfig
import org.meshtastic.proto.ChannelSet
import org.meshtastic.proto.LocalConfig
import org.meshtastic.proto.LocalModuleConfig
import org.meshtastic.proto.LocalStats

/** Creates a file-backed [DataStore]<[Preferences]> at the given path under the data directory. */
private fun createPreferencesDataStore(name: String, scope: CoroutineScope): DataStore<Preferences> {
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
    single<CoroutineScope>(named(DATASTORE_SCOPE)) { CoroutineScope(get<CoroutineDispatchers>().io + SupervisorJob()) }

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
    single(named("ProcessLifecycle")) { DesktopProcessLifecycleOwner().lifecycle }
}

/** Named [DataStore]<[Preferences]> instances for all preference domains. */
private fun desktopPreferencesDataStoreModule() = module {
    single<DataStore<Preferences>>(named("AnalyticsDataStore")) {
        createPreferencesDataStore("analytics", get(named(DATASTORE_SCOPE)))
    }
    single<DataStore<Preferences>>(named("HomoglyphEncodingDataStore")) {
        createPreferencesDataStore("homoglyph_encoding", get(named(DATASTORE_SCOPE)))
    }
    single<DataStore<Preferences>>(named("AppDataStore")) {
        createPreferencesDataStore("app", get(named(DATASTORE_SCOPE)))
    }
    single<DataStore<Preferences>>(named("CustomEmojiDataStore")) {
        createPreferencesDataStore("custom_emoji", get(named(DATASTORE_SCOPE)))
    }
    single<DataStore<Preferences>>(named("MapDataStore")) {
        createPreferencesDataStore("map", get(named(DATASTORE_SCOPE)))
    }
    single<DataStore<Preferences>>(named("MapConsentDataStore")) {
        createPreferencesDataStore("map_consent", get(named(DATASTORE_SCOPE)))
    }
    single<DataStore<Preferences>>(named("MapTileProviderDataStore")) {
        createPreferencesDataStore("map_tile_provider", get(named(DATASTORE_SCOPE)))
    }
    single<DataStore<Preferences>>(named("MeshDataStore")) {
        createPreferencesDataStore("mesh", get(named(DATASTORE_SCOPE)))
    }
    single<DataStore<Preferences>>(named("RadioDataStore")) {
        createPreferencesDataStore("radio", get(named(DATASTORE_SCOPE)))
    }
    single<DataStore<Preferences>>(named("UiDataStore")) {
        createPreferencesDataStore("ui", get(named(DATASTORE_SCOPE)))
    }
    single<DataStore<Preferences>>(named("MeshLogDataStore")) {
        createPreferencesDataStore("meshlog", get(named(DATASTORE_SCOPE)))
    }
    single<DataStore<Preferences>>(named("FilterDataStore")) {
        createPreferencesDataStore("filter", get(named(DATASTORE_SCOPE)))
    }
    single<DataStore<Preferences>>(named("CorePreferencesDataStore")) {
        createPreferencesDataStore("core_preferences", get(named(DATASTORE_SCOPE)))
    }
}

/** Proto [DataStore] instances (OkioStorage-backed). */
private fun desktopProtoDataStoreModule() = module {
    val protoDir = desktopDataDir() + "/datastore"

    single<DataStore<LocalConfig>>(named("CoreLocalConfigDataStore")) {
        DataStoreFactory.create(
            storage =
            OkioStorage(
                fileSystem = FileSystem.SYSTEM,
                serializer = LocalConfigSerializer,
                producePath = { "$protoDir/local_config.pb".toPath() },
            ),
            corruptionHandler = ReplaceFileCorruptionHandler(produceNewData = { LocalConfig() }),
            scope = get(named(DATASTORE_SCOPE)),
        )
    }

    single<DataStore<LocalModuleConfig>>(named("CoreModuleConfigDataStore")) {
        DataStoreFactory.create(
            storage =
            OkioStorage(
                fileSystem = FileSystem.SYSTEM,
                serializer = ModuleConfigSerializer,
                producePath = { "$protoDir/module_config.pb".toPath() },
            ),
            corruptionHandler = ReplaceFileCorruptionHandler(produceNewData = { LocalModuleConfig() }),
            scope = get(named(DATASTORE_SCOPE)),
        )
    }

    single<DataStore<ChannelSet>>(named("CoreChannelSetDataStore")) {
        DataStoreFactory.create(
            storage =
            OkioStorage(
                fileSystem = FileSystem.SYSTEM,
                serializer = ChannelSetSerializer,
                producePath = { "$protoDir/channel_set.pb".toPath() },
            ),
            corruptionHandler = ReplaceFileCorruptionHandler(produceNewData = { ChannelSet() }),
            scope = get(named(DATASTORE_SCOPE)),
        )
    }

    single<DataStore<LocalStats>>(named("CoreLocalStatsDataStore")) {
        DataStoreFactory.create(
            storage =
            OkioStorage(
                fileSystem = FileSystem.SYSTEM,
                serializer = LocalStatsSerializer,
                producePath = { "$protoDir/local_stats.pb".toPath() },
            ),
            corruptionHandler = ReplaceFileCorruptionHandler(produceNewData = { LocalStats() }),
            scope = get(named(DATASTORE_SCOPE)),
        )
    }
}
