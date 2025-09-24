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

package com.geeksville.mesh.repository.api

import org.meshtastic.core.database.dao.FirmwareReleaseDao
import dagger.Lazy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.meshtastic.core.database.entity.FirmwareReleaseEntity
import org.meshtastic.core.database.entity.FirmwareReleaseType
import org.meshtastic.core.database.entity.asDeviceVersion
import org.meshtastic.core.database.entity.asEntity
import org.meshtastic.core.model.NetworkFirmwareRelease
import javax.inject.Inject

class FirmwareReleaseLocalDataSource @Inject constructor(private val firmwareReleaseDaoLazy: Lazy<FirmwareReleaseDao>) {
    private val firmwareReleaseDao by lazy { firmwareReleaseDaoLazy.get() }

    suspend fun insertFirmwareReleases(
        firmwareReleases: List<NetworkFirmwareRelease>,
        releaseType: FirmwareReleaseType,
    ) = withContext(Dispatchers.IO) {
        if (firmwareReleases.isNotEmpty()) {
            firmwareReleaseDao.deleteAll()
            firmwareReleases.forEach { firmwareRelease ->
                firmwareReleaseDao.insert(firmwareRelease.asEntity(releaseType))
            }
        }
    }

    suspend fun deleteAllFirmwareReleases() = withContext(Dispatchers.IO) { firmwareReleaseDao.deleteAll() }

    suspend fun getLatestRelease(releaseType: FirmwareReleaseType): FirmwareReleaseEntity? =
        withContext(Dispatchers.IO) {
            val releases = firmwareReleaseDao.getReleasesByType(releaseType)
            if (releases.isEmpty()) {
                return@withContext null
            } else {
                val latestRelease = releases.maxBy { it.asDeviceVersion() }
                return@withContext latestRelease
            }
        }
}
