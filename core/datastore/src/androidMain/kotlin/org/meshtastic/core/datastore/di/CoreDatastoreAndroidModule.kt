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
import androidx.datastore.core.okio.OkioSerializer
import androidx.datastore.core.okio.OkioStorage
import androidx.datastore.dataStoreFile
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStoreFile
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toOkioPath
import org.koin.core.annotation.Module
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
    fun providePreferencesDataStore(context: Context, scope: DataStoreScope): CorePreferencesDataStore =
        PreferenceDataStoreFactory.create(
            corruptionHandler = ReplaceFileCorruptionHandler(produceNewData = { emptyPreferences() }),
            migrations =
            listOf(
                SharedPreferencesMigration(context = context, sharedPreferencesName = USER_PREFERENCES_NAME),
            ),
            scope = scope,
            produceFile = { context.preferencesDataStoreFile(USER_PREFERENCES_NAME) },
        )
            .asCorePreferencesDataStore()
}

@Module
class LocalConfigDataStoreModule {
    @Single
    fun provideLocalConfigDataStore(context: Context, scope: DataStoreScope): CoreLocalConfigDataStore = protoStore(
        serializer = LocalConfigSerializer,
        producePath = { context.dataStoreFile("local_config.pb").toOkioPath() },
        produceNewData = { LocalConfig() },
        scope = scope,
    )
        .asCoreLocalConfigDataStore()
}

@Module
class ModuleConfigDataStoreModule {
    @Single
    fun provideModuleConfigDataStore(context: Context, scope: DataStoreScope): CoreModuleConfigDataStore = protoStore(
        serializer = ModuleConfigSerializer,
        producePath = { context.dataStoreFile("module_config.pb").toOkioPath() },
        produceNewData = { LocalModuleConfig() },
        scope = scope,
    )
        .asCoreModuleConfigDataStore()
}

@Module
class ChannelSetDataStoreModule {
    @Single
    fun provideChannelSetDataStore(context: Context, scope: DataStoreScope): CoreChannelSetDataStore = protoStore(
        serializer = ChannelSetSerializer,
        producePath = { context.dataStoreFile("channel_set.pb").toOkioPath() },
        produceNewData = { ChannelSet() },
        scope = scope,
    )
        .asCoreChannelSetDataStore()
}

@Module
class LocalStatsDataStoreModule {
    @Single
    fun provideLocalStatsDataStore(context: Context, scope: DataStoreScope): CoreLocalStatsDataStore = protoStore(
        serializer = LocalStatsSerializer,
        producePath = { context.dataStoreFile("local_stats.pb").toOkioPath() },
        produceNewData = { LocalStats() },
        scope = scope,
    )
        .asCoreLocalStatsDataStore()
}

/** [producePath] is an on-disk identity — changing it orphans existing user data. */
private fun <T> protoStore(
    serializer: OkioSerializer<T>,
    producePath: () -> Path,
    produceNewData: () -> T,
    scope: DataStoreScope,
): DataStore<T> = DataStoreFactory.create(
    storage = OkioStorage(fileSystem = FileSystem.SYSTEM, serializer = serializer, producePath = producePath),
    corruptionHandler = ReplaceFileCorruptionHandler(produceNewData = { produceNewData() }),
    scope = scope,
)

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
