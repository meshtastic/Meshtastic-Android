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
package org.meshtastic.core.prefs.di

import javax.inject.Qualifier

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AnalyticsDataStore

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class HomoglyphEncodingDataStore

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AppDataStore

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class CustomEmojiDataStore

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class MapDataStore

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class MapConsentDataStore

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class MapTileProviderDataStore

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class MeshDataStore

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class RadioDataStore

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class UiDataStore

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class MeshLogDataStore

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class FilterDataStore

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class NodeDisplayNameDataStore
