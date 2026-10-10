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
package org.meshtastic.feature.settings.radio.component

import org.meshtastic.proto.Config.NetworkConfig.ProtocolFlags

/** One switch on the Network screen: a [ProtocolFlags] bit and whether it is set in `enabled_protocols`. */
internal data class FlagSwitch(val flag: ProtocolFlags, val checked: Boolean)

/** Every bearer bit as a switch, in schema order; the zero value is "none" and gets no switch. */
internal fun protocolFlagSwitches(enabled: Int): List<FlagSwitch> =
    ProtocolFlags.entries.filter { it.value != 0 }.map { FlagSwitch(it, enabled and it.value != 0) }

/** [current] with [flag]'s bit set or cleared and every other bit untouched. */
internal fun toggleFlag(current: Int, flag: ProtocolFlags, on: Boolean): Int =
    if (on) current or flag.value else current and flag.value.inv()
