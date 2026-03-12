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
import androidx.room.Room
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okio.FileSystem
import okio.Path.Companion.toPath
import org.koin.core.qualifier.named
import org.koin.dsl.module
import org.meshtastic.core.common.BuildConfigProvider
import org.meshtastic.core.common.database.DatabaseManager
import org.meshtastic.core.database.DatabaseProvider
import org.meshtastic.core.database.MeshtasticDatabase
import org.meshtastic.core.database.MeshtasticDatabase.Companion.configureCommon
import org.meshtastic.core.database.MeshtasticDatabaseConstructor
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
 * Desktop Room KMP database provider. Builds a single file-backed SQLite database using [MeshtasticDatabaseConstructor]
 * and [BundledSQLiteDriver] (both KMP-ready).
 */
class DesktopDatabaseManager :
    DatabaseProvider,
    DatabaseManager {
    private val dir = desktopDataDir()
    private val dbName = "$dir/meshtastic.db"

    private val db: MeshtasticDatabase by lazy {
        FileSystem.SYSTEM.createDirectories(dir.toPath())
        Room.databaseBuilder<MeshtasticDatabase>(name = dbName) { MeshtasticDatabaseConstructor.initialize() }
            .configureCommon()
            .build()
    }

    override val currentDb: StateFlow<MeshtasticDatabase> by lazy { MutableStateFlow(db) }

    override suspend fun <T> withDb(block: suspend (MeshtasticDatabase) -> T): T? = block(db)

    private val _cacheLimit = MutableStateFlow(DEFAULT_CACHE_LIMIT)
    override val cacheLimit: StateFlow<Int> = _cacheLimit

    override fun getCurrentCacheLimit(): Int = _cacheLimit.value

    override fun setCacheLimit(limit: Int) {
        _cacheLimit.value = limit.coerceIn(MIN_LIMIT, MAX_LIMIT)
    }

    override suspend fun switchActiveDatabase(address: String?) {
        // Desktop uses a single database — no per-device switching
    }

    override fun hasDatabaseFor(address: String?): Boolean {
        // Desktop always has the single database available
        return !address.isNullOrBlank() && address != "n"
    }

    companion object {
        private const val DEFAULT_CACHE_LIMIT = 100
        private const val MIN_LIMIT = 1
        private const val MAX_LIMIT = 100
    }
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
 * - [DatabaseProvider] and [DatabaseManager] via Room KMP
 * - [Lifecycle] (`ProcessLifecycle`)
 * - [BuildConfigProvider]
 */
@Suppress("InjectDispatcher")
fun desktopPlatformModule() = module {
    includes(desktopPreferencesDataStoreModule(), desktopProtoDataStoreModule())

    val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // -- Build config --
    single<BuildConfigProvider> {
        object : BuildConfigProvider {
            override val isDebug: Boolean = true
            override val applicationId: String = "org.meshtastic.desktop"
            override val versionCode: Int = 1
            override val versionName: String = "0.1.0-desktop"
            override val absoluteMinFwVersion: String = "2.0.0"
            override val minFwVersion: String = "2.5.0"
        }
    }

    // -- Process Lifecycle (stays RESUMED forever on desktop) --
    single(named("ProcessLifecycle")) { DesktopProcessLifecycleOwner().lifecycle }

    // -- Database (Room KMP with BundledSQLiteDriver) --
    single { DesktopDatabaseManager() }
    single<DatabaseProvider> { get<DesktopDatabaseManager>() }
    single<DatabaseManager> { get<DesktopDatabaseManager>() }
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
