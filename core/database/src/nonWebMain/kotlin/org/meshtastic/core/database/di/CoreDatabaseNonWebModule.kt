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
package org.meshtastic.core.database.di

import org.koin.core.annotation.Module
import org.koin.core.annotation.Single
import org.meshtastic.core.database.createDatabaseDataStore

/**
 * android/jvm/iOS-only Koin module for the [DatabaseDataStore]/`DatabaseManager` slice of this module, which cannot
 * exist on wasmJs (`androidx.datastore:datastore-preferences` has no wasmJs variant). Deliberately has no
 * `@ComponentScan`: `nonWebMain`'s other `@Single`/`@Factory`-annotated classes (e.g.
 * [org.meshtastic.core.database.DatabaseManager]) are already reached by the shared `CoreDatabaseModule`'s
 * `@ComponentScan("org.meshtastic.core.database")` in commonMain — re-declaring the same scan here would
 * double-register them for every non-web compilation.
 */
@Module
class CoreDatabaseNonWebModule {
    @Single
    fun provideDatabaseDataStore(): DatabaseDataStore =
        createDatabaseDataStore("db-manager-prefs").asDatabaseDataStore()
}
