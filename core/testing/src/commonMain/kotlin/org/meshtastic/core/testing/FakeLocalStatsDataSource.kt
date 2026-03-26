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
package org.meshtastic.core.testing

import kotlinx.coroutines.flow.StateFlow
import org.meshtastic.core.datastore.LocalStatsDataSource
import org.meshtastic.proto.LocalStats

/** A test double for [LocalStatsDataSource] that provides an in-memory implementation. */
class FakeLocalStatsDataSource :
    BaseFake(),
    LocalStatsDataSource {
    private val _localStatsFlow = mutableStateFlow(LocalStats())
    override val localStatsFlow: StateFlow<LocalStats> = _localStatsFlow

    override suspend fun setLocalStats(stats: LocalStats) {
        _localStatsFlow.value = stats
    }

    override suspend fun clearLocalStats() {
        _localStatsFlow.value = LocalStats()
    }
}
