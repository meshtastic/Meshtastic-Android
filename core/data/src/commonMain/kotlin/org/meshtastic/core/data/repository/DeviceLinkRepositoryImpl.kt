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

import co.touchlab.kermit.Logger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.koin.core.annotation.Single
import org.meshtastic.core.common.util.safeCatching
import org.meshtastic.core.data.datasource.DeviceHardwareLocalDataSource
import org.meshtastic.core.data.datasource.DeviceLinkLocalDataSource
import org.meshtastic.core.data.datasource.MshToLinksJsonDataSource
import org.meshtastic.core.database.entity.asEntity
import org.meshtastic.core.database.entity.asExternalModel
import org.meshtastic.core.model.DeviceLink
import org.meshtastic.core.model.MshToMarketplace
import org.meshtastic.core.repository.DeviceLinkRepository

@Single
class DeviceLinkRepositoryImpl(
    private val jsonDataSource: MshToLinksJsonDataSource,
    private val localDataSource: DeviceLinkLocalDataSource,
    private val deviceHardwareLocalDataSource: DeviceHardwareLocalDataSource,
) : DeviceLinkRepository {

    /** Guards the import so concurrent collectors don't run it more than once at a time. */
    private val importMutex = Mutex()

    override suspend fun ensureImported() {
        if (localDataSource.count() > 0) return
        importMutex.withLock { if (localDataSource.count() == 0) doImport() }
    }

    override suspend fun reconcile() {
        importMutex.withLock { doImport() }
    }

    override suspend fun getLinksForTarget(platformioTarget: String, regionCode: String): List<DeviceLink> {
        if (platformioTarget.isBlank()) return emptyList()
        ensureImported()
        val links = localDataSource.getAll().map { it.asExternalModel() }
        val marketplaceKeys = jsonDataSource.loadMarketplaces().keys
        val deviceTargets = deviceHardwareLocalDataSource.getAllTargets().toSet()
        return DeviceLinkMatcher.match(
            links = links,
            marketplaceKeys = marketplaceKeys,
            deviceTargets = deviceTargets,
            target = platformioTarget,
            region = regionCode,
        )
    }

    override fun observeAllLinks(): Flow<List<DeviceLink>> = flow {
        ensureImported()
        emitAll(localDataSource.observeAll().map { entities -> entities.map { it.asExternalModel() } })
    }

    /** Loads bundled `urls.json`, classifies each short code, upserts, and prunes orphans. Mirrors Apple's import. */
    private suspend fun doImport() {
        safeCatching {
            val routes = jsonDataSource.loadRoutes()
            if (routes.isEmpty()) {
                Logger.w { "DeviceLinkRepository: no routes in bundled urls.json; skipping import" }
                return@safeCatching
            }
            val marketplaces = jsonDataSource.loadMarketplaces()
            val deviceTargets = deviceHardwareLocalDataSource.getAllTargets().toSet()

            val links =
                routes.map { route ->
                    val isVendor = route.shortCode in deviceTargets
                    DeviceLink(
                        shortCode = route.shortCode,
                        originalUrl = route.originalUrl,
                        description = route.description,
                        isVendor = isVendor,
                        regions = if (isVendor) null else marketplaceRegions(route.shortCode, marketplaces),
                    )
                }

            localDataSource.upsertAll(links.map { it.asEntity() })
            localDataSource.deleteNotIn(links.map { it.shortCode })
            Logger.i { "DeviceLinkRepository: imported ${links.size} msh.to links" }
        }
            .onFailure { Logger.w(it) { "DeviceLinkRepository: device links import failed" } }
    }

    /**
     * Shipping regions for a marketplace short code, or null when it is not a marketplace link. Uses the same
     * delimiter-aware classifier as the matcher/UI so a code's classification (vendor/variant vs marketplace) is
     * consistent everywhere — independent of the `match` hint in `marketplaces.json`, which is unreliable in practice
     * (e.g. AliExpress is declared `suffix` yet most codes use the `aliexpress-<target>` prefix form).
     */
    private fun marketplaceRegions(code: String, marketplaces: Map<String, MshToMarketplace>): List<String>? =
        DeviceLinkMatcher.marketplaceKeyFor(code, marketplaces.keys)?.let { marketplaces.getValue(it).regions }
}
