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

import org.meshtastic.core.common.util.CommonUri
import org.meshtastic.core.model.DeviceHardware
import org.meshtastic.core.model.SoftDeviceVariant
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.UiText
import org.meshtastic.core.resources.firmware_maintenance_no_release
import org.meshtastic.core.resources.firmware_maintenance_not_a_bootloader_volume
import org.meshtastic.core.resources.firmware_maintenance_softdevice_conflict
import org.meshtastic.core.resources.firmware_maintenance_unknown_board
import org.meshtastic.core.resources.firmware_maintenance_unknown_softdevice
import org.meshtastic.core.resources.firmware_maintenance_unsupported_device
import org.meshtastic.core.resources.firmware_maintenance_wrong_destination

/**
 * Which leg of a multi-pass USB/UF2 sequence a file-save prompt belongs to.
 *
 * Also the recomposition key for the instruction dialog in `AwaitingFileSaveState`. Every sequence shipped today is
 * (FactoryErase | BootloaderUpgrade) → Firmware, so the key always changes between passes; a future sequence with two
 * consecutive identical steps would need a pass index added here.
 */
enum class UsbFileSaveStep {
    /**
     * The release firmware image — the only pass in a plain update, and the last pass of every maintenance sequence.
     */
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
@Suppress("UndocumentedPublicProperty")
enum class UsbMaintenanceRefusal {
    /**
     * The device's SoftDevice variant could not be resolved, so no erase image can be chosen safely. Covers an absent
     * or malformed metadata asset, an unmapped hardware model, and a device reporting a target we have no mapping for.
     */
    UnknownSoftDevice,

    /** The architecture has no UF2 erase path at all (ESP32, portduino). */
    UnsupportedArchitecture,

    /** No release firmware is selected, so there would be nothing to re-flash after erasing. */
    NoFirmwareRelease,

    /** The picked destination is not on removable storage, so it cannot be a mounted bootloader volume. */
    DestinationNotRemovable,

    /**
     * The picked volume exposes no readable `INFO_UF2.TXT`, so it is not an Adafruit-family UF2 bootloader drive. Also
     * the outcome when a device rebooted into CDC-only bootloader mode, where no mass-storage volume exists at all.
     */
    NotABootloaderVolume,

    /**
     * The volume's reported SoftDevice contradicts the bundled map. One of the two is wrong and we cannot tell which,
     * so refuse — and treat the map row as suspect.
     */
    SoftDeviceConflict,

    /** No OTAFIX image is published for the Board-ID this volume reports. */
    UnknownBoardId,
}

/**
 * The single mapping from [UsbMaintenanceRefusal] to user-facing copy.
 *
 * Shared by the ViewModel (wraps it in [org.meshtastic.feature.firmware.FirmwareUpdateState.Error] for a refusal raised
 * mid-flow) and the pre-flight card (resolves it via [UiText.asString] next to the disabled button) so the two call
 * sites can never drift out of sync with each other.
 */
internal fun usbMaintenanceRefusalMessage(reason: UsbMaintenanceRefusal): UiText = when (reason) {
    UsbMaintenanceRefusal.UnknownSoftDevice -> UiText.Resource(Res.string.firmware_maintenance_unknown_softdevice)

    UsbMaintenanceRefusal.UnsupportedArchitecture ->
        UiText.Resource(Res.string.firmware_maintenance_unsupported_device)

    UsbMaintenanceRefusal.NoFirmwareRelease -> UiText.Resource(Res.string.firmware_maintenance_no_release)

    UsbMaintenanceRefusal.DestinationNotRemovable ->
        UiText.Resource(Res.string.firmware_maintenance_wrong_destination)

    UsbMaintenanceRefusal.NotABootloaderVolume ->
        UiText.Resource(Res.string.firmware_maintenance_not_a_bootloader_volume)

    UsbMaintenanceRefusal.SoftDeviceConflict -> UiText.Resource(Res.string.firmware_maintenance_softdevice_conflict)

    UsbMaintenanceRefusal.UnknownBoardId -> UiText.Resource(Res.string.firmware_maintenance_unknown_board)
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

/**
 * What a mounted UF2 bootloader volume says about itself, read from its `INFO_UF2.TXT`.
 *
 * @property boardId The `Board-ID:` line — unique per board, and stable across bootloader vintages.
 * @property softDevice The installed SoftDevice, when the bootloader reports one. `null` on RP2040 (no SoftDevice
 *   exists) and on bootloaders predating the `uf2_init` that appends the line.
 */
internal data class MaintenanceVolume(val boardId: String, val softDevice: SoftDeviceVariant?)

/** Outcome of vetting a user-picked volume before anything is written to it. */
internal sealed interface VolumeInspection {
    data class Accepted(val volume: MaintenanceVolume) : VolumeInspection

    data class Rejected(val reason: UsbMaintenanceRefusal) : VolumeInspection
}

/**
 * Vets [treeUri] as a UF2 bootloader volume before it is written to.
 *
 * Two independent checks, cheapest first: the volume must be removable, and it must expose a readable `INFO_UF2.TXT`
 * with a `Board-ID:` line. The second is the load-bearing one — it is positive proof of an Adafruit-family bootloader
 * drive, where a removability heuristic only makes "somewhere on internal storage" less likely. This is what stops the
 * common mis-tap (saving into Downloads) from being indistinguishable from a successful flash.
 */
@Suppress("ReturnCount") // two independent vetting gates, each of which must reject immediately
internal suspend fun inspectMaintenanceVolume(treeUri: CommonUri, fileHandler: FirmwareFileHandler): VolumeInspection {
    if (!fileHandler.isRemovableDestination(treeUri)) {
        return VolumeInspection.Rejected(UsbMaintenanceRefusal.DestinationNotRemovable)
    }
    val info =
        fileHandler.readSiblingText(treeUri, INFO_UF2_FILE_NAME)
            ?: return VolumeInspection.Rejected(UsbMaintenanceRefusal.NotABootloaderVolume)
    val boardId = parseUf2BoardId(info) ?: return VolumeInspection.Rejected(UsbMaintenanceRefusal.NotABootloaderVolume)

    return VolumeInspection.Accepted(MaintenanceVolume(boardId = boardId, softDevice = parseUf2SoftDevice(info)))
}

/** Which image to write, or why not. */
internal sealed interface MaintenanceImageChoice {
    data class Resolved(val asset: MaintenanceUf2) : MaintenanceImageChoice

    data class Refused(val reason: UsbMaintenanceRefusal) : MaintenanceImageChoice
}

/**
 * Chooses the image for [request], preferring what the mounted [volume] reports over what the bundled map predicted.
 *
 * Total over both requests and every refusal, with no default image on any path. Called only after
 * [inspectMaintenanceVolume] accepts, and always before anything is written — so a refusal here costs the user a
 * message, never a half-flashed device.
 */
internal fun chooseMaintenanceImage(
    request: UsbMaintenanceRequest,
    hardware: DeviceHardware,
    volume: MaintenanceVolume,
): MaintenanceImageChoice = when (request) {
    UsbMaintenanceRequest.FactoryErase ->
        if (hardware.isNrf52Arc) {
            when (val resolution = resolveNrfEraseImage(hardware.softDeviceVariant, volume.softDevice)) {
                is EraseImageResolution.Resolved -> MaintenanceImageChoice.Resolved(resolution.asset)

                is EraseImageResolution.Conflict ->
                    MaintenanceImageChoice.Refused(UsbMaintenanceRefusal.SoftDeviceConflict)

                EraseImageResolution.Unresolved ->
                    MaintenanceImageChoice.Refused(UsbMaintenanceRefusal.UnknownSoftDevice)
            }
        } else {
            // RP2040: pico_erase is board-agnostic and there is no SoftDevice to reconcile.
            eraseUf2For(hardware)?.let { MaintenanceImageChoice.Resolved(it) }
                ?: MaintenanceImageChoice.Refused(UsbMaintenanceRefusal.UnsupportedArchitecture)
        }

    UsbMaintenanceRequest.BootloaderUpgrade ->
        otafixUf2ForBoardId(volume.boardId)?.let { MaintenanceImageChoice.Resolved(it) }
            ?: MaintenanceImageChoice.Refused(UsbMaintenanceRefusal.UnknownBoardId)
}
