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
package org.meshtastic.core.network.repository

import android.hardware.usb.UsbDevice

/**
 * Returns a stable identifier for a USB device that survives replug and reboot.
 *
 * Preference order:
 * 1. `VVVV:PPPP:SERIAL` — when the device advertises a USB serial number (most CP210x / CH9102 / CDC-ACM Meshtastic
 *    boards do). `VVVV` / `PPPP` are the USB vendor/product ids formatted as four-digit upper-case hex to match the
 *    industry convention used by `lsusb`/udev and to keep the address visually consistent with the rest of the
 *    hex-formatted identifier.
 * 2. `VVVV:PPPP` — fallback when the serial number is unreadable (permission not yet granted on Android 10+, or the
 *    board doesn't expose one). Stable per device model but conflates multiple identical boards.
 *
 * This replaces the legacy `/dev/bus/usb/NNN/MMM` enumeration path, which changes on every replug and across reboots.
 */
fun UsbDevice.stableUsbId(): String {
    val serial = runCatching { serialNumber }.getOrNull()?.takeIf { it.isNotBlank() }
    val vid = vendorId.toString(HEX_RADIX).uppercase().padStart(HEX_ID_WIDTH, '0')
    val pid = productId.toString(HEX_RADIX).uppercase().padStart(HEX_ID_WIDTH, '0')
    return if (serial != null) {
        "$vid:$pid:$serial"
    } else {
        "$vid:$pid"
    }
}

private const val HEX_RADIX = 16
private const val HEX_ID_WIDTH = 4
