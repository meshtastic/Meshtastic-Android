/*
 * NTsocial MeshLink original work and modifications:
 * Copyright (c) 2026 LiberaNt LLC
 *
 * Meshtastic Android-derived portions, where present:
 * Copyright (c) 2026 Meshtastic LLC
 *
 * Developed and/or modified for NTsocial MeshLink in 2026.
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
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
package com.ntsocial.meshlink.ios.runtime

import co.touchlab.kermit.Logger
import com.ntsocial.meshlink.core.ble.BluetoothRepository
import com.ntsocial.meshlink.core.common.database.DatabaseManager
import com.ntsocial.meshlink.core.di.CoroutineDispatchers
import com.ntsocial.meshlink.core.radiofleet.RadioFleetManager
import com.ntsocial.meshlink.core.repository.ChannelOperationLock
import com.ntsocial.meshlink.core.repository.NtsocialGatewayRepository
import com.ntsocial.meshlink.core.repository.PacketRepository
import com.ntsocial.meshlink.core.repository.RadioConfigRepository
import com.ntsocial.meshlink.core.repository.RadioInterfaceService
import com.ntsocial.meshlink.core.repository.RadioPrefs
import com.ntsocial.meshlink.core.repository.ServiceRepository
import com.ntsocial.meshlink.core.service.MeshServiceOrchestrator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.koin.core.Koin
import org.koin.core.KoinApplication
import org.koin.dsl.koinApplication

/** Process composition root. Swift owns only Apple lifecycle/entitlements; this owner owns the radio graph. */
internal class IosCompositionRoot {
    init {
        installGatewayDiagnostics()
    }

    val application: KoinApplication = koinApplication { modules(iosCoreModule()) }
    val koin: Koin
        get() = application.koin

    private var started = false
    private var hostActive = false
    private var gatewayConfiguration: AppleGatewayRuntimeConfiguration? = null
    private var gatewayCoordinator: IosAppleGatewayCoordinator? = null
    private val lifecycleScope by lazy { CoroutineScope(SupervisorJob() + koin.get<CoroutineDispatchers>().default) }

    fun configureGateway(sharedContainerPath: String, hmacKeyBase64: String) {
        gatewayConfiguration =
            AppleGatewayRuntimeConfiguration(sharedContainerPath = sharedContainerPath, hmacKeyBase64 = hmacKeyBase64)
        if (started) startGatewayCoordinator()
    }

    fun clearGatewayConfiguration() {
        gatewayCoordinator?.close()
        gatewayCoordinator = null
        gatewayConfiguration = null
    }

    fun processGatewayCommands() {
        gatewayCoordinator?.processCommands()
    }

    fun start() {
        if (started) return
        started = true
        koin.get<IosDurableMessageQueue>().start()
        koin.get<MeshServiceOrchestrator>().start()
        koin.get<IosEndpointConversationSourceCoordinator>().start()
        lifecycleScope.launch {
            val radioPrefs = koin.get<RadioPrefs>()
            koin
                .get<RadioFleetManager>()
                .start(legacyAddress = radioPrefs.readPersistedDevAddr(), legacyName = radioPrefs.devName.value)
        }
        startGatewayCoordinator()
    }

    fun setHostActive(active: Boolean) {
        hostActive = active
        koin.get<IosProcessLifecycleOwner>().setActive(active)
        gatewayCoordinator?.setHostActive(active)
    }

    fun close() {
        if (started) {
            lifecycleScope.cancel()
            runBlocking { koin.get<RadioFleetManager>().stop() }
            koin.get<IosEndpointConversationSourceCoordinator>().close()
            koin.get<MeshServiceOrchestrator>().stop()
            koin.get<IosDurableMessageQueue>().close()
            gatewayCoordinator?.close()
            gatewayCoordinator = null
            started = false
        }
        application.close()
    }

    @Suppress("TooGenericExceptionCaught")
    private fun startGatewayCoordinator() {
        val configuration = gatewayConfiguration ?: return
        gatewayCoordinator?.close()
        gatewayCoordinator =
            try {
                // Deliberately resolve from the process root. Endpoint scopes are never candidates for Apple Gateway,
                // so selecting a secondary radio cannot redirect parent traffic or expose its channel identity.
                IosAppleGatewayCoordinator(
                    configuration = configuration,
                    bluetoothRepository = koin.get<BluetoothRepository>(),
                    serviceRepository = koin.get<ServiceRepository>(),
                    radioInterfaceService = koin.get<RadioInterfaceService>(),
                    radioConfigRepository = koin.get<RadioConfigRepository>(),
                    packetRepository = koin.get<PacketRepository>(),
                    gatewayRepository = koin.get<NtsocialGatewayRepository>(),
                    databaseManager = koin.get<DatabaseManager>(),
                    channelOperationLock = koin.get<ChannelOperationLock>(),
                    dispatchers = koin.get<CoroutineDispatchers>(),
                )
                    .also { coordinator ->
                        coordinator.start()
                        coordinator.setHostActive(hostActive)
                    }
            } catch (error: Exception) {
                Logger.e(error) { "Apple Gateway configuration was rejected" }
                null
            }
    }
}

internal data class AppleGatewayRuntimeConfiguration(val sharedContainerPath: String, val hmacKeyBase64: String)
