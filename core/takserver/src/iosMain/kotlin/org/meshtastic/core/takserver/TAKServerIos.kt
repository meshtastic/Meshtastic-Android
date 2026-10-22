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
package org.meshtastic.core.takserver

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.repository.TakCertificateStore

/**
 * iOS KMP stub. The real iOS TAK server lives in Meshtastic-Apple (`Meshtastic/Helpers/TAK/TAKServerManager.swift`) and
 * uses Apple's `Network.framework` / `NWListener` + mTLS directly, not this KMP module.
 *
 * We provide a no-op implementation here so that the shared `core:takserver` module still compiles for the iOS KMP
 * targets. Any iOS-side consumer of this module would never actually call into this path — iOS bypasses the KMP
 * `TAKServer` interface entirely.
 */
private class NoopTAKServer : TAKServer {
    override val isSupported = false
    override val certificates: TakCertificateProvider = NoopTakCertificateProvider
    private val _connectionCount = MutableStateFlow(0)
    override val connectionCount: StateFlow<Int> = _connectionCount.asStateFlow()
    override val rejectedConnectionCount: StateFlow<Int> = MutableStateFlow(0).asStateFlow()
    override var onMessage: ((CoTMessage, TAKClientInfo?) -> Unit)? = null
    override var onClientConnected: (() -> Unit)? = null

    override suspend fun start(scope: CoroutineScope): Result<Unit> = Result.success(Unit)

    override fun stop() = Unit

    override suspend fun broadcast(cotMessage: CoTMessage) = Unit

    override suspend fun broadcastRawXml(xml: String) = Unit

    override suspend fun hasConnections(): Boolean = false
}

private object NoopTakCertificateProvider : TakCertificateProvider {
    override suspend fun dataPackageCredentials(): TakDataPackageCredentials? = null

    override suspend fun regenerate(): Result<Unit> =
        Result.failure(UnsupportedOperationException("Local TAK server is not available on this platform"))
}

actual fun createTAKServer(
    dispatchers: CoroutineDispatchers,
    certificateStore: TakCertificateStore,
    port: Int,
): TAKServer = NoopTAKServer()
