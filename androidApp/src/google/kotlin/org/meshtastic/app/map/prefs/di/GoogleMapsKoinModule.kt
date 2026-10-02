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
package org.meshtastic.app.map.prefs.di

import android.content.Context
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Configuration
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.prefs.di.createPreferencesDataStore

@Module
@Configuration
@ComponentScan("org.meshtastic.app.map")
class GoogleMapsKoinModule {

    @Single
    fun provideGoogleMapsDataStore(context: Context, dispatchers: CoroutineDispatchers): GoogleMapsDataStore =
        createPreferencesDataStore(context, dispatchers, legacyName = "google_maps_prefs", fileName = "google_maps_ds")
            .asGoogleMapsDataStore()
}
