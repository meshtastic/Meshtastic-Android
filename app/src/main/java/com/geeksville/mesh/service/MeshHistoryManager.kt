/*
 * Copyright (c) 2025-2026 Meshtastic LLC
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

import android.util.Log
import androidx.annotation.VisibleForTesting
import co.touchlab.kermit.Logger
import com.geeksville.mesh.BuildConfig
import com.geeksville.mesh.model.NO_DEVICE_SELECTED
import okio.ByteString.Companion.toByteString
import org.meshtastic.core.prefs.mesh.MeshPrefs
import org.meshtastic.proto.Data
import org.meshtastic.proto.MeshPacket
import org.meshtastic.proto.ModuleConfig
import org.meshtastic.proto.PortNum
import org.meshtastic.proto.StoreAndForward
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MeshHistoryManager
@Inject
constructor(
    private val meshPrefs: MeshPrefs,
    private val packetHandler: PacketHandler,
) {
    companion object {
        private const val HISTORY_TAG = "HistoryReplay"
        private const val DEFAULT_HISTORY_RETURN_WINDOW_MINUTES = 60 * 24
        private const val DEFAULT_HISTORY_RETURN_MAX_MESSAGES = 100

        @VisibleForTesting
        internal fun buildStoreForwardHistoryRequest(
            lastRequest: Int,
            historyReturnWindow: Int,
            historyReturnMax: Int,
        ): StoreAndForward {
            val history =
                StoreAndForward.History(
                    last_request = lastRequest.coerceAtLeast(0),
                    window = historyReturnWindow.coerceAtLeast(0),
                    history_messages = historyReturnMax.coerceAtLeast(0),
                )
            return StoreAndForward(rr = StoreAndForward.RequestResponse.CLIENT_HISTORY, history = history)
        }

        @VisibleForTesting
        internal fun resolveHistoryRequestParameters(window: Int, max: Int): Pair<Int, Int> {
            val resolvedWindow = if (window > 0) window else DEFAULT_HISTORY_RETURN_WINDOW_MINUTES
            val resolvedMax = if (max > 0) max else DEFAULT_HISTORY_RETURN_MAX_MESSAGES
            return resolvedWindow to resolvedMax
        }
    }

    private fun historyLog(priority: Int = Log.INFO, throwable: Throwable? = null, message: () -> String) {
        if (!BuildConfig.DEBUG) return
        val logger = Logger.withTag(HISTORY_TAG)
        val msg = message()
        when (priority) {
            Log.VERBOSE -> logger.v(throwable) { msg }
            Log.DEBUG -> logger.d(throwable) { msg }
            Log.INFO -> logger.i(throwable) { msg }
            Log.WARN -> logger.w(throwable) { msg }
            Log.ERROR -> logger.e(throwable) { msg }
            else -> logger.i(throwable) { msg }
        }
    }

    private fun activeDeviceAddress(): String? =
        meshPrefs.deviceAddress?.takeIf { !it.equals(NO_DEVICE_SELECTED, ignoreCase = true) && it.isNotBlank() }

    fun requestHistoryReplay(
        trigger: String,
        myNodeNum: Int?,
        storeForwardConfig: ModuleConfig.StoreForwardConfig?,
        transport: String,
    ) {
        val address = activeDeviceAddress()
        if (address == null || myNodeNum == null) {
            val reason = if (address == null) "no_addr" else "no_my_node"
            historyLog { "requestHistory skipped trigger=$trigger reason=$reason" }
            return
        }

        val lastRequest = meshPrefs.getStoreForwardLastRequest(address)
        val (window, max) =
            resolveHistoryRequestParameters(
                storeForwardConfig?.history_return_window ?: 0,
                storeForwardConfig?.history_return_max ?: 0,
            )

        val request = buildStoreForwardHistoryRequest(lastRequest, window, max)

        historyLog {
            "requestHistory trigger=$trigger transport=$transport addr=$address " +
                "lastRequest=$lastRequest window=$window max=$max"
        }

        runCatching {
            packetHandler.sendToRadio(
                MeshPacket(
                    to = myNodeNum,
                    decoded = Data(portnum = PortNum.STORE_FORWARD_APP, payload = request.encode().toByteString()),
                    priority = MeshPacket.Priority.BACKGROUND,
                ),
            )
        }
            .onFailure { ex -> historyLog(Log.WARN, ex) { "requestHistory failed" } }
    }

    fun updateStoreForwardLastRequest(source: String, lastRequest: Int, transport: String) {
        if (lastRequest <= 0) return
        val address = activeDeviceAddress() ?: return
        val current = meshPrefs.getStoreForwardLastRequest(address)
        if (lastRequest != current) {
            meshPrefs.setStoreForwardLastRequest(address, lastRequest)
            historyLog {
                "historyMarker updated source=$source transport=$transport " +
                    "addr=$address from=$current to=$lastRequest"
            }
        }
    }
}
