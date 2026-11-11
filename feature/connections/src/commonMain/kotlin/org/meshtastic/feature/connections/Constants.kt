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
package org.meshtastic.feature.connections

import org.meshtastic.core.model.InterfaceId

/**
 * Sentinel value for "no device selected" that mirrors the one-char prefix used by every real
 * [org.meshtastic.feature.connections.model.DeviceListEntry]'s `fullAddress`. Persisted by `radioInterfaceService` so
 * the UI layer can distinguish "user explicitly disconnected" from "first launch".
 */
const val NO_DEVICE_SELECTED: String = "n"

/** One-char prefix marking a TCP/Network device's `fullAddress`. See [DeviceListEntry.Tcp]. */
const val TCP_DEVICE_PREFIX: String = "t"

/** One-char prefix marking a BLE device's `fullAddress`. See [DeviceListEntry.Ble]. */
const val BLE_DEVICE_PREFIX: String = "x"

private const val BLE_PAIRING_PIN_LENGTH = 6

private val BLE_ADDRESS = Regex("[0-9A-Fa-f]{2}(:[0-9A-Fa-f]{2}){5}")

// The forms TcpTransport splits: `host`, `host:port`, or a bracketed IPv6 literal with an optional port.
private val TCP_ADDRESS = Regex("""(\[[0-9A-Za-z:.%_]+\]|[^\s:\[\]]+)(:\d{1,5})?""")

/**
 * Whether a deep link's [address] names a transport this app knows, in a form it can connect to. A link that fails this
 * opens the Connections screen without asking to connect. Demo addresses link only while [virtualAllowed], so a link
 * cannot drop a real radio for a demo transport that would then refuse to start.
 */
fun isLinkableAddress(address: String, virtualAllowed: Boolean = true): Boolean {
    val rest = address.drop(1)
    return when (address.firstOrNull()?.let(InterfaceId::forIdChar)) {
        InterfaceId.BLUETOOTH -> BLE_ADDRESS.matches(rest)

        InterfaceId.NOP -> rest.isEmpty()

        InterfaceId.TCP -> TCP_ADDRESS.matches(rest)

        InterfaceId.MOCK,
        InterfaceId.REPLAY,
        -> virtualAllowed

        InterfaceId.SERIAL -> rest.isNotBlank()

        null -> false
    }
}

/**
 * The pairing PIN a link supplies for [address], or `null` when it is missing, is not six digits from 100000 to 999999
 * (the range firmware draws passkeys from; a leading zero can never match its numeric PIN), or [address] is not a BLE
 * device, so the link falls back to pairing by hand.
 */
fun blePairingPinFor(address: String?, pin: String?): String? = pin?.takeIf {
    address?.startsWith(BLE_DEVICE_PREFIX) == true &&
        it.length == BLE_PAIRING_PIN_LENGTH &&
        it.all { c -> c in '0'..'9' } &&
        it[0] != '0'
}
