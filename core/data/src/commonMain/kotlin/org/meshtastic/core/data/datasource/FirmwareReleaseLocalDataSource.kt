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
package org.meshtastic.core.data.datasource

import org.koin.core.annotation.Single
import org.meshtastic.core.database.DatabaseProvider
import org.meshtastic.core.database.entity.FirmwareReleaseEntity
import org.meshtastic.core.database.entity.FirmwareReleaseType
import org.meshtastic.core.database.entity.asDeviceVersion
import org.meshtastic.core.database.entity.asEntity
import org.meshtastic.core.model.NetworkFirmwareRelease

@Single
class FirmwareReleaseLocalDataSource(private val dbManager: DatabaseProvider) {
    suspend fun deleteAllFirmwareReleases() {
        dbManager.withDb { it.firmwareReleaseDao().deleteAll() }
    }

    /** Transactionally replaces all rows of each given type with its API list; other types are untouched. */
    suspend fun replaceFirmwareReleases(releasesByType: Map<FirmwareReleaseType, List<NetworkFirmwareRelease>>) {
        dbManager.withDb {
            it.firmwareReleaseDao()
                .replaceByTypes(
                    types = releasesByType.keys.toList(),
                    releases = releasesByType.flatMap { (type, releases) -> releases.map { r -> r.asEntity(type) } },
                )
        }
    }

    suspend fun getLatestRelease(releaseType: FirmwareReleaseType): FirmwareReleaseEntity? = dbManager.withDb {
        val releases = it.firmwareReleaseDao().getReleasesByType(releaseType)
        if (releases.isEmpty()) null else releases.maxBy { entity -> entity.asDeviceVersion() }
    }
}
