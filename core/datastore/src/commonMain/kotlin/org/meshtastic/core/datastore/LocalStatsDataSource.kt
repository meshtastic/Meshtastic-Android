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
package org.meshtastic.core.datastore

import androidx.datastore.core.DataStore
import co.touchlab.kermit.Logger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import okio.IOException
import org.koin.core.annotation.Named
import org.koin.core.annotation.Single
import org.meshtastic.proto.LocalStats

/** Interface that handles saving and retrieving [LocalStats] data. */
interface LocalStatsDataSource {
    val localStatsFlow: Flow<LocalStats>

    suspend fun setLocalStats(stats: LocalStats)

    suspend fun clearLocalStats()
}

/** Implementation of [LocalStatsDataSource] using DataStore. */
@Single
open class LocalStatsDataSourceImpl(
    @Named("CoreLocalStatsDataStore") private val localStatsStore: DataStore<LocalStats>,
) : LocalStatsDataSource {
    override val localStatsFlow: Flow<LocalStats> =
        localStatsStore.data.catch { exception ->
            if (exception is IOException) {
                Logger.e { "Error reading LocalStats: ${exception.message}" }
                emit(LocalStats())
            } else {
                throw exception
            }
        }

    override suspend fun setLocalStats(stats: LocalStats) {
        localStatsStore.updateData { stats }
    }

    override suspend fun clearLocalStats() {
        localStatsStore.updateData { LocalStats() }
    }
}
