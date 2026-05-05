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
import kotlinx.coroutines.CoroutineScope
import org.koin.core.annotation.Named
import org.koin.core.annotation.Single
import org.meshtastic.core.common.util.handledLaunch
import org.meshtastic.core.model.RadioController
import org.meshtastic.core.repository.HistoryManager
import org.meshtastic.core.repository.MeshPrefs
import org.meshtastic.proto.ModuleConfig

@Single
class HistoryManagerImpl(
    private val meshPrefs: MeshPrefs,
    private val radioController: RadioController,
    @Named("ServiceScope") private val scope: CoroutineScope,
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

    private fun historyLog(message: String, throwable: Throwable? = null) {
        logger.i(throwable) { message }
    }

    private fun activeDeviceAddress(): String? =
        meshPrefs.deviceAddress.value?.takeIf { !it.equals(NO_DEVICE_SELECTED, ignoreCase = true) && it.isNotBlank() }

    override fun requestHistoryReplay(
        trigger: String,
        myNodeNum: Int?,
        storeForwardConfig: ModuleConfig.StoreForwardConfig?,
        transport: String,
    ) {
        val address = activeDeviceAddress()
        if (address == null || myNodeNum == null) {
            val reason = if (address == null) "no_addr" else "no_my_node"
            historyLog("requestHistory skipped trigger=$trigger reason=$reason")
            return
        }

        val lastRequest = meshPrefs.getStoreForwardLastRequest(address).value.takeIf { it > 0 }
        val (window, max) =
            resolveHistoryRequestParameters(
                storeForwardConfig?.history_return_window ?: 0,
                storeForwardConfig?.history_return_max ?: 0,
            )

        historyLog(
            "requestHistory trigger=$trigger transport=$transport addr=$address " +
                "since=${lastRequest ?: "all"} window=$window max=$max via=sdk",
        )

        scope.handledLaunch {
            val accepted = radioController.requestStoreForwardHistory(since = lastRequest)
            if (!accepted) {
                logger.w {
                    "requestHistory failed trigger=$trigger transport=$transport addr=$address since=${lastRequest ?: "all"}"
                }
            }
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
                    "addr=$address from=$current to=$lastRequest",
            )
        }
    }
}
