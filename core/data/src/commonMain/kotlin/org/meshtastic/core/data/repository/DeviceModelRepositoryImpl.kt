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
package org.meshtastic.core.data.repository

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.koin.core.annotation.Single
import org.meshtastic.core.network.DeviceModelRemoteDataSource
import org.meshtastic.core.repository.DeviceModelRepository

/**
 * Memoizes per image name for the process lifetime: a model is a few hundred KB to a few MB and the hero re-asks on
 * every node detail open. The flasher's answer is cached either way; a failed request is not, so a later open retries.
 */
@Single
class DeviceModelRepositoryImpl(private val remote: DeviceModelRemoteDataSource) : DeviceModelRepository {
    private val lock = Mutex()
    private val existing = mutableSetOf<String>()
    private val absent = mutableSetOf<String>()
    private val models = mutableMapOf<String, ByteArray>()

    override suspend fun hasModel(imageFile: String): Boolean {
        val cached = lock.withLock {
            when (imageFile) {
                in existing -> true
                in absent -> false
                else -> null
            }
        }
        val answer =
            cached
                ?: remote.exists(imageFile)?.also { exists ->
                    lock.withLock { (if (exists) existing else absent) += imageFile }
                }
        return answer == true
    }

    override suspend fun getModel(imageFile: String): ByteArray? = lock.withLock { models[imageFile] }
        ?: remote.fetch(imageFile)?.also { bytes ->
            lock.withLock {
                models[imageFile] = bytes
                existing += imageFile
            }
        }
}
