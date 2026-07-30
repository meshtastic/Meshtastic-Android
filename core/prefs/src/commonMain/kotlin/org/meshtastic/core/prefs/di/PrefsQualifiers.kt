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
package org.meshtastic.core.prefs.di

// Qualifiers for the per-domain `DataStore<Preferences>` instances this module provides. All are the same erased type,
// so the qualifier is the only thing telling them apart — reference the constant, since a misspelled literal resolves
// to nothing and fails at runtime.

const val ANALYTICS_DATASTORE = "AnalyticsDataStore"

const val APP_DATASTORE = "AppDataStore"

const val CUSTOM_EMOJI_DATASTORE = "CustomEmojiDataStore"

const val FILTER_DATASTORE = "FilterDataStore"

const val HOMOGLYPH_ENCODING_DATASTORE = "HomoglyphEncodingDataStore"

const val MAP_CONSENT_DATASTORE = "MapConsentDataStore"

const val MAP_DATASTORE = "MapDataStore"

const val MAP_TILE_PROVIDER_DATASTORE = "MapTileProviderDataStore"

const val MESH_DATASTORE = "MeshDataStore"

const val MESH_LOG_DATASTORE = "MeshLogDataStore"

const val RADIO_DATASTORE = "RadioDataStore"

const val UI_DATASTORE = "UiDataStore"
