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

package com.geeksville.mesh.di

import com.geeksville.mesh.repository.map.CustomTileProviderRepository
import com.geeksville.mesh.repository.map.SharedPreferencesCustomTileProviderRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object MapModule {

    // Serialization Provider (from original SerializationModule)
    @Provides @Singleton
    fun provideJson(): Json = Json { prettyPrint = false }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class MapRepositoryModule {

    @Binds
    @Singleton
    abstract fun bindCustomTileProviderRepository(
        impl: SharedPreferencesCustomTileProviderRepository,
    ): CustomTileProviderRepository
}
