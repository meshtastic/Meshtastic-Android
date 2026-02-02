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
package org.meshtastic.core.datastore.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.dataStoreFile
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStoreFile
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.meshtastic.core.datastore.KEY_APP_INTRO_COMPLETED
import org.meshtastic.core.datastore.KEY_INCLUDE_UNKNOWN
import org.meshtastic.core.datastore.KEY_NODE_SORT
import org.meshtastic.core.datastore.KEY_ONLY_DIRECT
import org.meshtastic.core.datastore.KEY_ONLY_ONLINE
import org.meshtastic.core.datastore.KEY_SHOW_IGNORED
import org.meshtastic.core.datastore.KEY_THEME
import org.meshtastic.core.datastore.serializer.ChannelSetSerializer
import org.meshtastic.core.datastore.serializer.LocalConfigSerializer
import org.meshtastic.core.datastore.serializer.ModuleConfigSerializer
import org.meshtastic.proto.ChannelSet
import org.meshtastic.proto.LocalConfig
import org.meshtastic.proto.LocalModuleConfig
import javax.inject.Qualifier
import javax.inject.Singleton

private const val USER_PREFERENCES_NAME = "user_preferences"

@Retention(AnnotationRetention.BINARY)
@Qualifier
annotation class DataStoreScope

@InstallIn(SingletonComponent::class)
@Module
object DataStoreModule {

    @Provides
    @Singleton
    @DataStoreScope
    fun provideDataStoreScope(): CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @Singleton
    @Provides
    fun providePreferencesDataStore(
        @ApplicationContext appContext: Context,
        @DataStoreScope scope: CoroutineScope,
    ): DataStore<Preferences> = PreferenceDataStoreFactory.create(
        corruptionHandler = ReplaceFileCorruptionHandler(produceNewData = { emptyPreferences() }),
        migrations =
        listOf(
            SharedPreferencesMigration(context = appContext, sharedPreferencesName = USER_PREFERENCES_NAME),
            SharedPreferencesMigration(
                context = appContext,
                sharedPreferencesName = "ui-prefs",
                keysToMigrate =
                setOf(
                    KEY_APP_INTRO_COMPLETED,
                    KEY_THEME,
                    KEY_NODE_SORT,
                    KEY_INCLUDE_UNKNOWN,
                    KEY_ONLY_ONLINE,
                    KEY_ONLY_DIRECT,
                    KEY_SHOW_IGNORED,
                ),
            ),
        ),
        scope = scope,
        produceFile = { appContext.preferencesDataStoreFile(USER_PREFERENCES_NAME) },
    )

    @Singleton
    @Provides
    fun provideLocalConfigDataStore(
        @ApplicationContext appContext: Context,
        @DataStoreScope scope: CoroutineScope,
    ): DataStore<LocalConfig> = DataStoreFactory.create(
        serializer = LocalConfigSerializer,
        produceFile = { appContext.dataStoreFile("local_config.pb") },
        corruptionHandler = ReplaceFileCorruptionHandler(produceNewData = { LocalConfig() }),
        scope = scope,
    )

    @Singleton
    @Provides
    fun provideModuleConfigDataStore(
        @ApplicationContext appContext: Context,
        @DataStoreScope scope: CoroutineScope,
    ): DataStore<LocalModuleConfig> = DataStoreFactory.create(
        serializer = ModuleConfigSerializer,
        produceFile = { appContext.dataStoreFile("module_config.pb") },
        corruptionHandler = ReplaceFileCorruptionHandler(produceNewData = { LocalModuleConfig() }),
        scope = scope,
    )

    @Singleton
    @Provides
    fun provideChannelSetDataStore(
        @ApplicationContext appContext: Context,
        @DataStoreScope scope: CoroutineScope,
    ): DataStore<ChannelSet> = DataStoreFactory.create(
        serializer = ChannelSetSerializer,
        produceFile = { appContext.dataStoreFile("channel_set.pb") },
        corruptionHandler = ReplaceFileCorruptionHandler(produceNewData = { ChannelSet() }),
        scope = scope,
    )
}
