/*
 * Copyright (c) 2025-2026 Meshtastic LLC
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okio.FileSystem
import okio.Path.Companion.toPath
import org.koin.core.qualifier.named
import org.koin.dsl.module
import org.meshtastic.core.common.BuildConfigProvider
import org.meshtastic.core.datastore.serializer.ChannelSetSerializer
import org.meshtastic.core.datastore.serializer.LocalConfigSerializer
import org.meshtastic.core.datastore.serializer.LocalStatsSerializer
import org.meshtastic.core.datastore.serializer.ModuleConfigSerializer
import org.meshtastic.proto.ChannelSet
import org.meshtastic.proto.LocalConfig
import org.meshtastic.proto.LocalModuleConfig
import org.meshtastic.proto.LocalStats

/**
 * Resolves the desktop data directory for persistent storage (DataStore files, Room database). Defaults to
 * `~/.meshtastic/`. Override via `MESHTASTIC_DATA_DIR` environment variable.
 */
private fun desktopDataDir(): String {
    val override = System.getenv("MESHTASTIC_DATA_DIR")
    if (!override.isNullOrBlank()) return override
    return System.getProperty("user.home") + "/.meshtastic"
}

/** Creates a file-backed [DataStore]<[Preferences]> at the given path under the data directory. */
private fun createPreferencesDataStore(name: String, scope: CoroutineScope): DataStore<Preferences> {
    val dir = desktopDataDir() + "/datastore"
    FileSystem.SYSTEM.createDirectories(dir.toPath())
    return PreferenceDataStoreFactory.create(
        corruptionHandler = ReplaceFileCorruptionHandler(produceNewData = { emptyPreferences() }),
        scope = scope,
        produceFile = { (dir + "/$name.preferences_pb").toPath().toNioPath().toFile() },
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
@Suppress("InjectDispatcher")
fun desktopPlatformModule() = module {
    includes(desktopPreferencesDataStoreModule(), desktopProtoDataStoreModule())

    // -- Build config --
    single<BuildConfigProvider> {
        object : BuildConfigProvider {
            override val isDebug: Boolean = true
            override val applicationId: String = "org.meshtastic.desktop"
            override val versionCode: Int = 1
            override val versionName: String = "2.7.14"
            override val absoluteMinFwVersion: String = "2.3.15"
            override val minFwVersion: String = "2.5.14"
        }
    }

    // -- Process Lifecycle (stays RESUMED forever on desktop) --
    single(named("ProcessLifecycle")) { DesktopProcessLifecycleOwner().lifecycle }
}

/** Named [DataStore]<[Preferences]> instances for all preference domains. */
@Suppress("InjectDispatcher")
private fun desktopPreferencesDataStoreModule() = module {
    val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    single<DataStore<Preferences>>(named("AnalyticsDataStore")) { createPreferencesDataStore("analytics", scope) }
    single<DataStore<Preferences>>(named("HomoglyphEncodingDataStore")) {
        createPreferencesDataStore("homoglyph_encoding", scope)
    }
    single<DataStore<Preferences>>(named("AppDataStore")) { createPreferencesDataStore("app", scope) }
    single<DataStore<Preferences>>(named("CustomEmojiDataStore")) { createPreferencesDataStore("custom_emoji", scope) }
    single<DataStore<Preferences>>(named("MapDataStore")) { createPreferencesDataStore("map", scope) }
    single<DataStore<Preferences>>(named("MapConsentDataStore")) { createPreferencesDataStore("map_consent", scope) }
    single<DataStore<Preferences>>(named("MapTileProviderDataStore")) {
        createPreferencesDataStore("map_tile_provider", scope)
    }
    single<DataStore<Preferences>>(named("MeshDataStore")) { createPreferencesDataStore("mesh", scope) }
    single<DataStore<Preferences>>(named("RadioDataStore")) { createPreferencesDataStore("radio", scope) }
    single<DataStore<Preferences>>(named("UiDataStore")) { createPreferencesDataStore("ui", scope) }
    single<DataStore<Preferences>>(named("MeshLogDataStore")) { createPreferencesDataStore("meshlog", scope) }
    single<DataStore<Preferences>>(named("FilterDataStore")) { createPreferencesDataStore("filter", scope) }
    single<DataStore<Preferences>>(named("CorePreferencesDataStore")) {
        createPreferencesDataStore("core_preferences", scope)
    }
}

/** Proto [DataStore] instances (OkioStorage-backed). */
@Suppress("InjectDispatcher")
private fun desktopProtoDataStoreModule() = module {
    val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
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
            scope = scope,
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
            scope = scope,
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
            scope = scope,
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
            scope = scope,
        )
    }
}
