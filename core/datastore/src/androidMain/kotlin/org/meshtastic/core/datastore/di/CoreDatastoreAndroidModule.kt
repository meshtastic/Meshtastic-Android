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
package org.meshtastic.core.datastore.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.core.okio.OkioStorage
import androidx.datastore.dataStoreFile
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStoreFile
import kotlinx.coroutines.CoroutineScope
import okio.FileSystem
import okio.Path.Companion.toOkioPath
import org.koin.core.annotation.Module
import org.koin.core.annotation.Named
import org.koin.core.annotation.Single
import org.meshtastic.core.datastore.serializer.ChannelSetSerializer
import org.meshtastic.core.datastore.serializer.LocalConfigSerializer
import org.meshtastic.core.datastore.serializer.LocalStatsSerializer
import org.meshtastic.core.datastore.serializer.ModuleConfigSerializer
import org.meshtastic.proto.ChannelSet
import org.meshtastic.proto.LocalConfig
import org.meshtastic.proto.LocalModuleConfig
import org.meshtastic.proto.LocalStats

private const val USER_PREFERENCES_NAME = "user_preferences"

@Module
class PreferencesDataStoreModule {
    @Single
    @Named("CorePreferencesDataStore")
    fun providePreferencesDataStore(
        context: Context,
        @Named("DataStoreScope") scope: CoroutineScope,
    ): DataStore<Preferences> = PreferenceDataStoreFactory.create(
        corruptionHandler = ReplaceFileCorruptionHandler(produceNewData = { emptyPreferences() }),
        migrations =
        listOf(SharedPreferencesMigration(context = context, sharedPreferencesName = USER_PREFERENCES_NAME)),
        scope = scope,
        produceFile = { context.preferencesDataStoreFile(USER_PREFERENCES_NAME) },
    )
}

@Module
class LocalConfigDataStoreModule {
    @Single
    @Named("CoreLocalConfigDataStore")
    fun provideLocalConfigDataStore(
        context: Context,
        @Named("DataStoreScope") scope: CoroutineScope,
    ): DataStore<LocalConfig> = DataStoreFactory.create(
        storage =
        OkioStorage(
            fileSystem = FileSystem.SYSTEM,
            serializer = LocalConfigSerializer,
            producePath = { context.dataStoreFile("local_config.pb").toOkioPath() },
        ),
        corruptionHandler = ReplaceFileCorruptionHandler(produceNewData = { LocalConfig() }),
        scope = scope,
    )
}

@Module
class ModuleConfigDataStoreModule {
    @Single
    @Named("CoreModuleConfigDataStore")
    fun provideModuleConfigDataStore(
        context: Context,
        @Named("DataStoreScope") scope: CoroutineScope,
    ): DataStore<LocalModuleConfig> = DataStoreFactory.create(
        storage =
        OkioStorage(
            fileSystem = FileSystem.SYSTEM,
            serializer = ModuleConfigSerializer,
            producePath = { context.dataStoreFile("module_config.pb").toOkioPath() },
        ),
        corruptionHandler = ReplaceFileCorruptionHandler(produceNewData = { LocalModuleConfig() }),
        scope = scope,
    )
}

@Module
class ChannelSetDataStoreModule {
    @Single
    @Named("CoreChannelSetDataStore")
    fun provideChannelSetDataStore(
        context: Context,
        @Named("DataStoreScope") scope: CoroutineScope,
    ): DataStore<ChannelSet> = DataStoreFactory.create(
        storage =
        OkioStorage(
            fileSystem = FileSystem.SYSTEM,
            serializer = ChannelSetSerializer,
            producePath = { context.dataStoreFile("channel_set.pb").toOkioPath() },
        ),
        corruptionHandler = ReplaceFileCorruptionHandler(produceNewData = { ChannelSet() }),
        scope = scope,
    )
}

@Module
class LocalStatsDataStoreModule {
    @Single
    @Named("CoreLocalStatsDataStore")
    fun provideLocalStatsDataStore(
        context: Context,
        @Named("DataStoreScope") scope: CoroutineScope,
    ): DataStore<LocalStats> = DataStoreFactory.create(
        storage =
        OkioStorage(
            fileSystem = FileSystem.SYSTEM,
            serializer = LocalStatsSerializer,
            producePath = { context.dataStoreFile("local_stats.pb").toOkioPath() },
        ),
        corruptionHandler = ReplaceFileCorruptionHandler(produceNewData = { LocalStats() }),
        scope = scope,
    )
}

@Module(
    includes =
    [
        PreferencesDataStoreModule::class,
        LocalConfigDataStoreModule::class,
        ModuleConfigDataStoreModule::class,
        ChannelSetDataStoreModule::class,
        LocalStatsDataStoreModule::class,
    ],
)
class CoreDatastoreAndroidModule
