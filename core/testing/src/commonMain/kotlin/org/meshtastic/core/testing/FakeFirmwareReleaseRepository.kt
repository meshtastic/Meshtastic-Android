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

import kotlinx.coroutines.flow.Flow
import org.meshtastic.core.database.entity.FirmwareRelease
import org.meshtastic.core.repository.FirmwareReleaseRepository

/**
 * A test double for [FirmwareReleaseRepository] that exposes stable and alpha releases as
 * [kotlinx.coroutines.flow.MutableStateFlow]s.
 *
 * Use [setStableRelease] and [setAlphaRelease] to drive the emitted values.
 */
class FakeFirmwareReleaseRepository :
    BaseFake(),
    FirmwareReleaseRepository {

    private val _stableRelease = mutableStateFlow<FirmwareRelease?>(null)
    private val _alphaRelease = mutableStateFlow<FirmwareRelease?>(null)

    override val stableRelease: Flow<FirmwareRelease?> = _stableRelease
    override val alphaRelease: Flow<FirmwareRelease?> = _alphaRelease

    var invalidateCacheCalls: Int = 0
        private set

    init {
        registerResetAction { invalidateCacheCalls = 0 }
    }

    override suspend fun invalidateCache() {
        invalidateCacheCalls++
    }

    fun setStableRelease(release: FirmwareRelease?) {
        _stableRelease.value = release
    }

    fun setAlphaRelease(release: FirmwareRelease?) {
        _alphaRelease.value = release
    }
}
