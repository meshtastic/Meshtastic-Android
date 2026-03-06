/*
 * Copyright (c) 2025 Meshtastic LLC
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

package org.meshtastic.core.prefs.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.meshtastic.core.prefs.map.GoogleMapsPrefs
import org.meshtastic.core.prefs.map.GoogleMapsPrefsImpl
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
internal annotation class GoogleMapsDataStore

@InstallIn(SingletonComponent::class)
@Module
interface GoogleMapsModule {

    @Binds fun bindGoogleMapsPrefs(googleMapsPrefsImpl: GoogleMapsPrefsImpl): GoogleMapsPrefs

    companion object {
        private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

        @Provides
        @Singleton
        @GoogleMapsDataStore
        fun provideGoogleMapsDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
            PreferenceDataStoreFactory.create(
                migrations = listOf(SharedPreferencesMigration(context, "google_maps_prefs")),
                scope = scope,
                produceFile = { context.preferencesDataStoreFile("google_maps_ds") }
            )
    }
}
