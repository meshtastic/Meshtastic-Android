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

import kotlinx.coroutines.flow.Flow
import org.meshtastic.core.model.EventFirmwareEdition

/** Event-firmware display metadata, seeded from the bundled `event_firmware.json` snapshot. */
interface EventFirmwareRepository {
    /** Metadata for [editionName] (a `FirmwareEdition` enum name), or `null` if it is not a known event edition. */
    suspend fun getEdition(editionName: String): EventFirmwareEdition?

    /**
     * Metadata for [editionName], re-emitting when the cache is refreshed from the network.
     *
     * Prefer this over [getEdition] for anything long-lived on screen. A one-shot read resolves once — when the device
     * connects — so an edition added to the hosted manifest after that point stays invisible until the user reconnects.
     * The flow triggers a refresh on collection and then re-emits whatever lands.
     */
    fun observeEdition(editionName: String): Flow<EventFirmwareEdition?>
}
