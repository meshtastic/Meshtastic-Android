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

package com.geeksville.mesh.service

import com.geeksville.mesh.CoroutineDispatchers
import com.geeksville.mesh.LocalOnlyProtos
import com.geeksville.mesh.repository.datastore.RadioConfigRepository
import com.geeksville.mesh.repository.radio.RadioInterfaceService
import com.geeksville.mesh.repository.radio.RadioServiceConnectionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConnectionRouter
@Inject
constructor(
    private val radioInterface: RadioInterfaceService,
    private val radioConfigRepository: RadioConfigRepository,
    private val dispatchers: CoroutineDispatchers,
) {

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val routerJob = Job()
    private val routerScope = CoroutineScope(dispatchers.io + routerJob)
    private var sleepTimeout: Job? = null

    private var localConfig: LocalOnlyProtos.LocalConfig = LocalOnlyProtos.LocalConfig.getDefaultInstance()

    init {
        // We need to keep our local radio config up to date
        radioConfigRepository.localConfigFlow.onEach { localConfig = it }.launchIn(routerScope)
    }

    fun start() {
        // This is where we will start listening to the radio interface
        radioInterface.connectionState.onEach(::onRadioConnectionState).launchIn(routerScope)
    }

    fun stop() {
        routerJob.cancel()
    }

    fun setDeviceAddress(address: String?): Boolean {
        _connectionState.value = ConnectionState.CONNECTING
        return radioInterface.setDeviceAddress(address)
    }

    private fun onRadioConnectionState(state: RadioServiceConnectionState) {
        // sleep now disabled by default on ESP32, permanent is true unless light sleep enabled
        val isRouter = localConfig.device.role == com.geeksville.mesh.ConfigProtos.Config.DeviceConfig.Role.ROUTER
        val lsEnabled = localConfig.power.isPowerSaving || isRouter
        val connected = state.isConnected
        val permanent = state.isPermanent || !lsEnabled
        onConnectionChanged(
            when {
                connected -> ConnectionState.CONNECTED
                permanent -> ConnectionState.DISCONNECTED
                else -> ConnectionState.DEVICE_SLEEP
            },
        )
    }

    private fun onConnectionChanged(c: ConnectionState) {
        // Cancel any existing timeouts
        sleepTimeout?.cancel()
        sleepTimeout = null

        _connectionState.value = c

        if (c == ConnectionState.DEVICE_SLEEP) {
            // Have our timeout fire in the appropriate number of seconds
            sleepTimeout =
                routerScope.launch {
                    try {
                        // If we have a valid timeout, wait that long (+30 seconds) otherwise, just wait 30 seconds
                        val timeout = (localConfig.power?.lsSecs ?: 0) + 30

                        // Log.d(TAG, "Waiting for sleeping device, timeout=$timeout secs")
                        delay(timeout * 1000L)
                        // Log.w(TAG, "Device timeout out, setting disconnected")
                        onConnectionChanged(ConnectionState.DISCONNECTED)
                    } catch (ex: kotlinx.coroutines.CancellationException) {
                        // Log.d(TAG, "device sleep timeout cancelled")
                    }
                }
        }
    }
}
