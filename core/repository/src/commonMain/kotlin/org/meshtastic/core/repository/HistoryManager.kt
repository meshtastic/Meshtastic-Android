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
package org.meshtastic.core.repository

import org.meshtastic.proto.ModuleConfig

/** Interface for managing store-and-forward history replay requests. */
interface HistoryManager {
    /**
     * Requests a history replay from the radio.
     *
     * @param trigger A string identifying the trigger for the request (for logging).
     * @param myNodeNum The local node number.
     * @param storeForwardConfig The store-and-forward module configuration.
     * @param transport The transport method being used (for logging).
     */
    fun requestHistoryReplay(
        trigger: String,
        myNodeNum: Int?,
        storeForwardConfig: ModuleConfig.StoreForwardConfig?,
        transport: String,
    )

    /**
     * Updates the last requested history marker.
     *
     * @param source A string identifying the source of the update (for logging).
     * @param lastRequest The timestamp or sequence number of the last received history message.
     * @param transport The transport method being used (for logging).
     */
    fun updateStoreForwardLastRequest(source: String, lastRequest: Int, transport: String)
}
