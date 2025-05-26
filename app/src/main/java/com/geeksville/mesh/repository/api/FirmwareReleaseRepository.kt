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

import com.geeksville.mesh.android.BuildUtils.debug
import com.geeksville.mesh.android.BuildUtils.warn
import com.geeksville.mesh.database.entity.FirmwareRelease
import com.geeksville.mesh.database.entity.FirmwareReleaseType
import com.geeksville.mesh.database.entity.asExternalModel
import com.geeksville.mesh.network.FirmwareReleaseRemoteDataSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.IOException
import javax.inject.Inject

class FirmwareReleaseRepository @Inject constructor(
    private val apiDataSource: FirmwareReleaseRemoteDataSource,
    private val localDataSource: FirmwareReleaseLocalDataSource,
    private val jsonDataSource: FirmwareReleaseJsonDataSource,
) {

    companion object {
        // 1 hour
        private const val CACHE_EXPIRATION_TIME_MS = 60 * 60 * 1000L
    }

    val stableRelease: Flow<FirmwareRelease?> = getLatestFirmware(FirmwareReleaseType.STABLE)

    val alphaRelease: Flow<FirmwareRelease?> = getLatestFirmware(FirmwareReleaseType.ALPHA)

    private fun getLatestFirmware(
        releaseType: FirmwareReleaseType,
        refresh: Boolean = false
    ): Flow<FirmwareRelease?> = flow {
        if (refresh) {
            invalidateCache()
        } else {
            val cachedRelease = localDataSource.getLatestRelease(releaseType)
            if (cachedRelease != null && !isCacheExpired(cachedRelease.lastUpdated)) {
                debug("Using recent cached firmware release")
                val externalModel = cachedRelease.asExternalModel()
                emit(externalModel)
                return@flow
            }
        }
        try {
            debug("Fetching firmware releases from server")
            val networkFirmwareReleases = apiDataSource.getFirmwareReleases()
                ?: throw IOException("empty response from server")
            val releases = when (releaseType) {
                FirmwareReleaseType.STABLE -> networkFirmwareReleases.releases.stable
                FirmwareReleaseType.ALPHA -> networkFirmwareReleases.releases.alpha
            }
            localDataSource.insertFirmwareReleases(
                releases,
                releaseType
            )
            val cachedRelease = localDataSource.getLatestRelease(releaseType)
            val externalModel = cachedRelease?.asExternalModel()
            emit(externalModel)
        } catch (e: IOException) {
            warn("Failed to fetch firmware releases from server: ${e.message}")
            val jsonFirmwareReleases = jsonDataSource.loadFirmwareReleaseFromJsonAsset()
            val releases = when (releaseType) {
                FirmwareReleaseType.STABLE -> jsonFirmwareReleases.releases.stable
                FirmwareReleaseType.ALPHA -> jsonFirmwareReleases.releases.alpha
            }
            localDataSource.insertFirmwareReleases(
                releases,
                releaseType
            )
            val cachedRelease = localDataSource.getLatestRelease(releaseType)
            val externalModel = cachedRelease?.asExternalModel()
            emit(externalModel)
        }
    }

    suspend fun invalidateCache() {
        localDataSource.deleteAllFirmwareReleases()
    }

    /**
     * Check if the cache is expired
     */
    private fun isCacheExpired(lastUpdated: Long): Boolean {
        return System.currentTimeMillis() - lastUpdated > CACHE_EXPIRATION_TIME_MS
    }
}
