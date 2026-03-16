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
package org.meshtastic.app.repository.radio

import org.koin.core.annotation.Single
import org.meshtastic.core.repository.RadioInterfaceService

/** Bluetooth backend implementation. */
@Single
class BleRadioInterfaceSpec(private val factory: BleRadioInterfaceFactory) : InterfaceSpec<BleRadioInterface> {
    override fun createInterface(rest: String, service: RadioInterfaceService): BleRadioInterface =
        factory.create(rest, service)

    /** Return true if this address is still acceptable. For Kable we don't strictly require prior bonding. */
    override fun addressValid(rest: String): Boolean {
        // We no longer strictly require the device to be in the bonded list before attempting connection,
        // as Kable and Android will handle bonding seamlessly during connection/characteristic access if needed.
        return rest.isNotBlank()
    }
}
