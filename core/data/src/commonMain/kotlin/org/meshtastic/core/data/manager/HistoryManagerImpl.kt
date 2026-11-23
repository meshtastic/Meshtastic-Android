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
package org.meshtastic.core.data.manager

import co.touchlab.kermit.Logger
import org.koin.core.annotation.Single
import org.meshtastic.core.model.util.anonymize
import org.meshtastic.core.repository.ConnectionStateProvider
import org.meshtastic.core.repository.HistoryManager
import org.meshtastic.core.repository.LocalNodeUnavailableException
import org.meshtastic.core.repository.MeshPrefs
import org.meshtastic.core.repository.PacketQueueRejectedException
import org.meshtastic.core.repository.RadioInterfaceService
import org.meshtastic.proto.ModuleConfig
import org.meshtastic.sdk.MeshtasticException

@Single
class HistoryManagerImpl(
    private val meshPrefs: MeshPrefs,
    private val radioInterfaceService: RadioInterfaceService,
    private val connectionStateProvider: ConnectionStateProvider,
) : HistoryManager {

    companion object {
        private const val HISTORY_TAG = "HistoryReplay"
        private const val DEFAULT_HISTORY_RETURN_WINDOW_MINUTES = 60 * 24
        private const val DEFAULT_HISTORY_RETURN_MAX_MESSAGES = 100
        private const val NO_DEVICE_SELECTED = "No device selected"

        fun resolveHistoryRequestParameters(window: Int, max: Int): Pair<Int, Int> {
            val resolvedWindow = if (window > 0) window else DEFAULT_HISTORY_RETURN_WINDOW_MINUTES
            val resolvedMax = if (max > 0) max else DEFAULT_HISTORY_RETURN_MAX_MESSAGES
            return resolvedWindow to resolvedMax
        }
    }

    private val logger = Logger.withTag(HISTORY_TAG)

    private fun historyLog(message: String) = logger.i { message }

    private fun activeDeviceAddress(): String? =
        meshPrefs.deviceAddress.value?.takeIf { !it.equals(NO_DEVICE_SELECTED, ignoreCase = true) && it.isNotBlank() }

    override suspend fun requestHistoryReplay(
        trigger: String,
        myNodeNum: Int?,
        storeForwardConfig: ModuleConfig.StoreForwardConfig?,
        transport: String,
        expectedConnectionVersion: Long,
    ) {
        val address = activeDeviceAddress()
        if (address == null || myNodeNum == null) throw LocalNodeUnavailableException("History replay")

        val lastRequest = meshPrefs.getStoreForwardLastRequest(address).value
        val (window, max) =
            resolveHistoryRequestParameters(
                storeForwardConfig?.history_return_window ?: 0,
                storeForwardConfig?.history_return_max ?: 0,
            )

        historyLog(
            "requestHistory trigger=$trigger transport=$transport addr=${address.anonymize} " +
                "lastRequest=$lastRequest window=$window max=$max",
        )

        sendHistoryRequest(expectedConnectionVersion, lastRequest, window, max)
    }

    /** Asks our own node for its history, only while [expectedConnectionVersion] still owns the connection. */
    private fun sendHistoryRequest(expectedConnectionVersion: Long, lastRequest: Int, window: Int, max: Int) {
        val client =
            radioInterfaceService.activeSession.value
                ?.takeIf { connectionStateProvider.connectionLifecycle.value.version == expectedConnectionVersion }
                ?.let(radioInterfaceService::clientForSession) ?: throw PacketQueueRejectedException("History replay")
        try {
            client.storeForward.requestLocalHistory(lastRequest, window, max)
        } catch (e: MeshtasticException) {
            throw PacketQueueRejectedException("History replay").apply { addSuppressed(e) }
        }
    }

    override fun updateStoreForwardLastRequest(source: String, lastRequest: Int, transport: String) {
        if (lastRequest <= 0) return
        val address = activeDeviceAddress() ?: return
        val current = meshPrefs.getStoreForwardLastRequest(address).value
        if (lastRequest != current) {
            meshPrefs.setStoreForwardLastRequest(address, lastRequest)
            historyLog(
                "historyMarker updated source=$source transport=$transport " +
                    "addr=${address.anonymize} from=$current to=$lastRequest",
            )
        }
    }
}
