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
package org.meshtastic.feature.firmware

import org.meshtastic.core.model.DeviceHardware

/**
 * Which leg of a multi-pass USB/UF2 sequence a file-save prompt belongs to.
 *
 * Also the recomposition key for the instruction dialog in `AwaitingFileSaveState`. Every sequence shipped today is
 * (FactoryErase | BootloaderUpgrade) → Firmware, so the key always changes between passes; a future sequence with two
 * consecutive identical steps would need a pass index added here.
 */
enum class UsbFileSaveStep {
    /** The release firmware image — the only pass in a plain update, and the last pass of every maintenance sequence. */
    Firmware,

    /** The factory-erase image, which wipes the internal filesystem and re-enters UF2 DFU. */
    FactoryErase,

    /** An OTAFIX bootloader self-update image. */
    BootloaderUpgrade,
    ;

    /**
     * True when writing this image destroys the device's application, making the *next* pass mandatory rather than
     * optional. Drives back-navigation gating and the "no abort edge" retry behaviour.
     */
    val isDestructive: Boolean
        get() = this != Firmware
}

/** A maintenance operation the user can start from the Ready state. */
enum class UsbMaintenanceRequest {
    FactoryErase,
    BootloaderUpgrade,
}

/** Why a maintenance action cannot run. Surfaced as explanatory copy rather than silently hiding the action. */
enum class UsbMaintenanceRefusal {
    /**
     * The device's SoftDevice variant could not be resolved, so no erase image can be chosen safely. Covers an absent or
     * malformed metadata asset, an unmapped hardware model, and a device reporting a target we have no mapping for.
     */
    UnknownSoftDevice,

    /** The architecture has no UF2 erase path at all (ESP32, portduino). */
    UnsupportedArchitecture,

    /** No release firmware is selected, so there would be nothing to re-flash after erasing. */
    NoFirmwareRelease,
}

/**
 * Which maintenance affordances the Ready state should show, precomputed by the ViewModel.
 *
 * Follows the `showBootloaderWarning` precedent: the screen stays dumb and the decision stays unit-testable.
 *
 * @property show Whether the maintenance section appears at all.
 * @property eraseRefusal Non-null when erase is shown but cannot run; the reason is displayed and the action disabled.
 * @property showBootloaderUpgrade Whether a bootloader-upgrade action is offered. Absent (not refused) when no image is
 *   mapped for the board — an unmapped board is a coverage gap, not a safety decision the user can act on.
 */
data class UsbMaintenanceGate(
    val show: Boolean = false,
    val eraseRefusal: UsbMaintenanceRefusal? = null,
    val showBootloaderUpgrade: Boolean = false,
)

/**
 * Decides which maintenance actions are available for [hardware] on [updateMethod].
 *
 * Pure and total: every refusal is represented, and nothing here can fall back to a default erase image.
 *
 * @param hasRelease Whether a firmware release is selected. Erasing without one would strand the device with no
 *   application, so the whole section is hidden.
 */
internal fun usbMaintenanceGate(
    hardware: DeviceHardware,
    updateMethod: FirmwareUpdateMethod,
    hasRelease: Boolean,
): UsbMaintenanceGate {
    val uf2Architecture = hardware.isNrf52Arc || hardware.isRp2040Arc
    if (updateMethod !is FirmwareUpdateMethod.Usb || !uf2Architecture || !hasRelease) {
        return UsbMaintenanceGate(show = false)
    }

    val eraseRefusal =
        when {
            eraseUf2For(hardware) != null -> null
            hardware.isNrf52Arc -> UsbMaintenanceRefusal.UnknownSoftDevice
            else -> UsbMaintenanceRefusal.UnsupportedArchitecture
        }

    return UsbMaintenanceGate(
        show = true,
        eraseRefusal = eraseRefusal,
        // nRF-only: RP2040 boards run no Adafruit bootloader, so OTAFIX does not apply. This is a visibility hint only
        // — which image gets written is decided later from the Board-ID the drive reports.
        showBootloaderUpgrade = hardware.isNrf52Arc && otafixSupportsTarget(hardware.effectiveTarget),
    )
}
