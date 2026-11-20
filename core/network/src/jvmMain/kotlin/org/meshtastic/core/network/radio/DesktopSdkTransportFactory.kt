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
package org.meshtastic.core.network.radio

import org.meshtastic.core.model.InterfaceId
import org.meshtastic.core.repository.RadioInterfaceService
import org.meshtastic.core.repository.RadioTransport

/** Creates a transport that owns the desktop PhoneAPI session, when the SDK accepts the endpoint. */
fun interface DesktopSdkTransportFactory {
    fun create(interfaceId: InterfaceId, endpoint: String, service: RadioInterfaceService): RadioTransport?
}
