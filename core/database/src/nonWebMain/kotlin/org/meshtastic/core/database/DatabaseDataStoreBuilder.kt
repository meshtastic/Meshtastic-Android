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
package org.meshtastic.core.database

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences

/**
 * Creates a platform-specific [DataStore] for database-related preferences.
 *
 * Declared here rather than in commonMain's `DatabaseBuilder.kt` because `androidx.datastore:datastore-preferences` —
 * the `Preferences` type itself — publishes no wasmJs (or even plain js) variant. `nonWebMain` is already an ancestor
 * of the android/jvm/iOS source sets that provide the `actual`, so this is a source-location move only.
 */
expect fun createDatabaseDataStore(name: String): DataStore<Preferences>
