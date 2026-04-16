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
package org.meshtastic.core.network.radio

import org.meshtastic.core.repository.RadioTransport

/**
 * An intentionally inert [RadioTransport] that silently discards all operations.
 *
 * Used as a safe default when no valid device address is configured or when the requested transport type is
 * unsupported. All method calls are no-ops — it never connects, never sends data, and never signals lifecycle events to
 * the service layer.
 */
class NopRadioTransport(val address: String) : RadioTransport {
    override fun handleSendToRadio(p: ByteArray) {
        // No-op
    }

    override suspend fun close() {
        // No-op
    }
}
