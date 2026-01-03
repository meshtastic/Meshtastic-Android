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

package org.meshtastic.core.data.datasource

import dagger.Lazy
import org.meshtastic.core.database.dao.FirmwareReleaseDao
import org.meshtastic.core.database.entity.FirmwareReleaseEntity
import org.meshtastic.core.database.entity.FirmwareReleaseType
import org.meshtastic.core.database.entity.asDeviceVersion
import org.meshtastic.core.database.entity.asEntity
import org.meshtastic.core.model.NetworkFirmwareRelease
import javax.inject.Inject

class FirmwareReleaseLocalDataSource @Inject constructor(
    private val firmwareReleaseDaoLazy: Lazy<FirmwareReleaseDao>
) {
    private val firmwareReleaseDao get() = firmwareReleaseDaoLazy.get()

    suspend fun insertFirmwareReleases(
        firmwareReleases: List<NetworkFirmwareRelease>,
        releaseType: FirmwareReleaseType,
    ) {
        firmwareReleases.forEach { firmwareRelease ->
            firmwareReleaseDao.insert(firmwareRelease.asEntity(releaseType))
        }
    }

    suspend fun deleteAllFirmwareReleases() = firmwareReleaseDao.deleteAll()

    suspend fun getLatestRelease(releaseType: FirmwareReleaseType): FirmwareReleaseEntity? {
        val releases = firmwareReleaseDao.getReleasesByType(releaseType)
        return releases.maxByOrNull { it.asDeviceVersion() }
    }
}
