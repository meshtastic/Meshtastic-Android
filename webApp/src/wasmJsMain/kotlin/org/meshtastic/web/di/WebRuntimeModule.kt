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
package org.meshtastic.web.di

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.coroutineScope
import io.ktor.client.HttpClient
import io.ktor.client.engine.js.Js
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.url
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import org.koin.core.annotation.Module
import org.koin.core.annotation.Named
import org.koin.core.annotation.Single
import org.meshtastic.core.common.di.PROCESS_LIFECYCLE
import org.meshtastic.core.data.datasource.BundledAssetReader
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.network.HttpClientDefaults
import org.meshtastic.core.network.KermitHttpLogger
import org.meshtastic.core.network.configureDefaultRetry
import org.meshtastic.core.network.service.ApiService
import org.meshtastic.core.network.service.ApiServiceImpl
import org.meshtastic.core.repository.AppIconBadge
import org.meshtastic.core.repository.ConnectionStateProvider
import org.meshtastic.core.repository.MeshNotificationManager
import org.meshtastic.core.repository.MeshWorkerManager
import org.meshtastic.core.repository.MessageQueue
import org.meshtastic.core.repository.NeighborInfoResponseProvider
import org.meshtastic.core.repository.NotificationManager
import org.meshtastic.core.repository.NotificationPrefs
import org.meshtastic.core.repository.PacketRepository
import org.meshtastic.core.repository.RadioController
import org.meshtastic.core.repository.ServiceRepository
import org.meshtastic.core.repository.ServiceStateWriter
import org.meshtastic.core.repository.TracerouteResponseProvider
import org.meshtastic.core.service.DispatchingMeshNotificationManager
import org.meshtastic.core.service.InProcessMessageQueue
import org.meshtastic.core.service.ServiceRepositoryImpl
import org.meshtastic.core.service.UnreadBadgeSync
import org.meshtastic.web.WebBuildConfig
import org.meshtastic.web.platform.WebAppIconBadge
import org.meshtastic.web.platform.WebNotificationManager

/**
 * Web runtime wiring mirroring `DesktopRuntimeModule`: the service repository, notifications and icon badge, queue and
 * HTTP.
 */
@Module
class WebRuntimeModule {

    @Single(
        binds =
        [
            ServiceRepository::class,
            ConnectionStateProvider::class,
            TracerouteResponseProvider::class,
            NeighborInfoResponseProvider::class,
            ServiceStateWriter::class,
        ],
    )
    fun serviceRepository(): ServiceRepository = ServiceRepositoryImpl()

    @Single
    fun notificationManager(
        prefs: NotificationPrefs,
        @Named(PROCESS_LIFECYCLE) processLifecycle: Lifecycle,
    ): NotificationManager = WebNotificationManager(prefs, processLifecycle.coroutineScope)

    @Single
    fun meshNotificationManager(notificationManager: NotificationManager): MeshNotificationManager =
        DispatchingMeshNotificationManager(notificationManager)

    @Single fun appIconBadge(): AppIconBadge = WebAppIconBadge()

    @Single
    fun unreadBadgeSync(packetRepository: PacketRepository, badge: AppIconBadge): UnreadBadgeSync =
        UnreadBadgeSync(packetRepository, badge)

    @Single(binds = [MessageQueue::class, MeshWorkerManager::class])
    fun messageQueue(
        packetRepository: PacketRepository,
        radioController: RadioController,
        dispatchers: CoroutineDispatchers,
    ): InProcessMessageQueue = InProcessMessageQueue(
        packetRepository = packetRepository,
        radioController = radioController,
        dispatchers = dispatchers,
    )

    @Single fun apiService(httpClient: HttpClient): ApiService = ApiServiceImpl(httpClient)

    /** Ktor [HttpClient] on the browser's fetch. A browser sets its own User-Agent. */
    @Single
    fun httpClient(json: Json): HttpClient = HttpClient(Js) {
        install(ContentNegotiation) { json(json) }
        install(DefaultRequest) { url(HttpClientDefaults.API_BASE_URL) }
        install(HttpTimeout) {
            requestTimeoutMillis = HttpClientDefaults.REQUEST_TIMEOUT_MS
            connectTimeoutMillis = HttpClientDefaults.TIMEOUT_MS
            socketTimeoutMillis = HttpClientDefaults.TIMEOUT_MS
        }
        install(HttpRequestRetry) { configureDefaultRetry() }
        if (WebBuildConfig.IS_DEBUG) {
            install(Logging) {
                logger = KermitHttpLogger
                level = LogLevel.INFO
            }
        }
    }

    /** No bundled assets ship with a browser tab; repositories seed from the network instead. */
    @Single fun bundledAssetReader(): BundledAssetReader = BundledAssetReader { null }
}
