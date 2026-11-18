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
package org.meshtastic.feature.connections.domain.usecase

/**
 * A USB serial ID the web chooser filters to. [productId] null matches every product of [vendorId]. [isBoard] names a
 * board, shown as is; otherwise [name] is a bridge chip or a family, shown as "<name> USB serial".
 */
internal class MeshtasticUsbId(val vendorId: Int, val productId: Int?, val name: String, val isBoard: Boolean = false) {
    fun matches(vendorId: Int, productId: Int): Boolean =
        this.vendorId == vendorId && (this.productId == null || this.productId == productId)
}

/** The ports Android's USB device filter (`androidApp/src/main/res/xml/device_filter.xml`) opens the app for. */
@Suppress("MagicNumber")
internal val MESHTASTIC_USB_IDS: List<MeshtasticUsbId> =
    listOf(
        MeshtasticUsbId(0x0403, 0x6001, "FT232R"),
        MeshtasticUsbId(0x0403, 0x6010, "FT2232H"),
        MeshtasticUsbId(0x0403, 0x6011, "FT4232H"),
        MeshtasticUsbId(0x0403, 0x6014, "FT232H"),
        MeshtasticUsbId(0x0403, 0x6015, "FT231X"),
        MeshtasticUsbId(0x10C4, 0xEA60, "CP2102"),
        MeshtasticUsbId(0x10C4, 0xEA70, "CP2105"),
        MeshtasticUsbId(0x10C4, 0xEA71, "CP2108"),
        MeshtasticUsbId(0x10C4, 0xEA80, "CP2110"),
        MeshtasticUsbId(0x067B, 0x2303, "PL2303"),
        MeshtasticUsbId(0x067B, 0x23A3, "PL2303GC"),
        MeshtasticUsbId(0x067B, 0x23B3, "PL2303GB"),
        MeshtasticUsbId(0x067B, 0x23C3, "PL2303GT"),
        MeshtasticUsbId(0x067B, 0x23D3, "PL2303GL"),
        MeshtasticUsbId(0x067B, 0x23E3, "PL2303GE"),
        MeshtasticUsbId(0x067B, 0x23F3, "PL2303GS"),
        MeshtasticUsbId(0x1A86, 0x7523, "CH340"),
        MeshtasticUsbId(0x1A86, 0x55D4, "CH9102F"),
        MeshtasticUsbId(0x2341, null, "Arduino"),
        MeshtasticUsbId(0x16C0, 0x0483, "Teensyduino"),
        MeshtasticUsbId(0x03EB, 0x2044, "LUFA"),
        MeshtasticUsbId(0x1EAF, 0x0004, "Maple"),
        MeshtasticUsbId(0x0D28, 0x0204, "mbed"),
        MeshtasticUsbId(0x239A, 0x8029, "RAK4631", isBoard = true),
        MeshtasticUsbId(0x239A, 0x4405, "T-Echo", isBoard = true),
        MeshtasticUsbId(0x239A, 0x810B, "Seeed XIAO nRF52840", isBoard = true),
        MeshtasticUsbId(0x239A, 0x00B3, "Pro Micro nRF52840", isBoard = true),
        MeshtasticUsbId(0x239A, 0xCAFE, "muzi BASE", isBoard = true),
        MeshtasticUsbId(0x303A, 0x1001, "ESP32-S3"),
        MeshtasticUsbId(0x2E8A, null, "RP2040"),
    )

/** [MESHTASTIC_USB_IDS] as the `filters` array `navigator.serial.requestPort()` takes. */
internal val MESHTASTIC_USB_FILTERS_JSON: String =
    MESHTASTIC_USB_IDS.joinToString(prefix = "[", postfix = "]") { id ->
        val product = id.productId?.let { ""","usbProductId":$it""" }.orEmpty()
        """{"usbVendorId":${id.vendorId}$product}"""
    }
