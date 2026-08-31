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

import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Factory
import org.koin.core.annotation.Module
import org.meshtastic.core.database.DatabaseProvider
import org.meshtastic.core.database.dao.DiscoveryDao
import org.meshtastic.core.database.dao.SwitchingDiscoveryDao

/**
 * Common (all-platform) Koin module. `@ComponentScan("org.meshtastic.core.database")` reaches whatever compilation this
 * module lives in — for android/jvm/iOS that includes `nonWebMain` (e.g. `DatabaseManager`), for wasmJs it includes
 * `wasmJsMain` (e.g. `SingleDatabaseProvider`) — so no per-target duplicate of this module is needed. This module used
 * to also provide `DatabaseDataStore`; that provider moved to `nonWebMain`'s `CoreDatabaseNonWebModule` because
 * `DatabaseDataStore` is `Preferences`-coupled, which has no wasmJs variant. That new module deliberately does *not*
 * re-declare `@ComponentScan` of the same package, to avoid double-registering everything this one already scans for
 * android/jvm/iOS.
 */
@Module
@ComponentScan("org.meshtastic.core.database")
class CoreDatabaseModule {
    /**
     * Long-lived consumers (discovery ViewModels, the scan engine) hold this DAO across device/DB switches, so hand
     * them the switch-aware delegate — never a DAO pinned to the injection-time `currentDb.value`, which would keep
     * reading a stale DB and crash once a cross-transport merge retires it. See [SwitchingDiscoveryDao].
     */
    @Factory
    fun provideDiscoveryDao(databaseProvider: DatabaseProvider): DiscoveryDao = SwitchingDiscoveryDao(databaseProvider)
}
