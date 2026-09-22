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
package org.meshtastic.core.model

import org.meshtastic.core.model.util.isDebug
import org.meshtastic.proto.FieldMetadata
import org.meshtastic.proto.ModuleConfig
import org.meshtastic.proto.mesh_beacon
import org.meshtastic.proto.statusmessage
import org.meshtastic.proto.tak

/**
 * Defines the capabilities and feature support based on the device firmware version.
 *
 * This class provides a centralized way to check if specific features are supported by the connected node's firmware.
 * Add new features here to ensure consistency across the app.
 *
 * Note: Properties are calculated once during initialization for efficiency.
 */
data class Capabilities(val firmwareVersion: String?, internal val forceEnableAll: Boolean = isDebug) {
    private val version = firmwareVersion?.let { DeviceVersion(it) }

    private fun atLeast(min: DeviceVersion): Boolean = forceEnableAll || (version != null && version >= min)

    /**
     * Whether a config field is worth offering on this firmware, from the version gates its schema declares. Below
     * `since_firmware` the node ignores the field. At or above `deprecated_since` it is shown only while [isSet], so a
     * value the node still holds stays visible instead of being silently kept.
     */
    fun offers(field: FieldMetadata, isSet: Boolean = false): Boolean {
        val arrived = field.since_firmware?.let(::gate)?.let(::atLeast) ?: true
        val retired =
            field.deprecated_since?.let(::gate)?.let { !forceEnableAll && version != null && version >= it } ?: false
        return arrived && (!retired || isSet)
    }

    // The schema declares these; an unparseable one must fail here rather than silently pass every gate.
    private fun gate(declared: String): DeviceVersion =
        DeviceVersion(declared).also { require(it.isValid) { "Unparseable firmware version in schema: $declared" } }

    /** Ability to mute notifications from specific nodes via admin messages. */
    val canMuteNode = atLeast(V2_7_18)

    /** Ability to request neighbor information from other nodes. Gated to [UNRELEASED] until working reliably. */
    val canRequestNeighborInfo = atLeast(UNRELEASED)

    /** Ability to send verified shared contacts. Supported since firmware v2.7.12. */
    val canSendVerifiedContacts = atLeast(V2_7_12)

    /** Ability to toggle the 'is_unmessageable' flag in user config. Supported since firmware v2.6.9. */
    val canToggleUnmessageable = atLeast(V2_6_9)

    /** Support for sharing contact information via QR codes. Supported since firmware v2.6.8. */
    val supportsQrCodeSharing = atLeast(V2_6_8)

    /** Support for the Status Message module, from the `since_firmware` its `ModuleConfig` field declares. */
    val supportsStatusMessage = offers(ModuleConfig.statusmessage)

    /**
     * Support for TAK (ATAK) module configuration, from the `since_firmware` its `ModuleConfig` field declares.
     *
     * The schema says 2.8.0 because that is where the firmware gained the write, persist and remote-admin read paths
     * (meshtastic/firmware#11216). Before it, v2.7.x `AdminModule::handleSetModuleConfig()` had no case for the `tak`
     * submessage: the node ACKed the write and rebooted without storing anything, so the editor appeared to save and
     * always read back as unspecified (Meshtastic-Android#6430).
     */
    val supportsTakConfig = offers(ModuleConfig.tak)

    /**
     * Support for the v2 TAK port (ATAK_PLUGIN_V2 = 78) with TAKPacketV2 + zstd dictionary compression. Supported since
     * firmware v2.8.0. Firmware v2.7.x and earlier only support the legacy ATAK_PLUGIN port (72) with the original
     * TAKPacket schema (PLI + GeoChat only, no compression), so the bridge falls back to that path for older nodes.
     */
    val supportsTakV2 = atLeast(V2_8_0)

    /** Support for location sharing on secondary channels. Supported since firmware v2.6.10. */
    val supportsSecondaryChannelLocation = atLeast(V2_6_10)

    /** Support for ESP32 Unified OTA. Supported since firmware v2.7.18. */
    val supportsEsp32Ota = atLeast(V2_7_18)

    /**
     * Support for the LoRa region→preset compatibility map. Supported since firmware v2.8.0. Older firmware never sends
     * the map, so the UI keeps the preset list unconstrained (preset *availability* is [supportsPreset]).
     */
    val supportsLoraRegionPresetMap = atLeast(V2_8_0)

    /**
     * Support for runtime lockdown mode (per-connection passphrase auth). Supported since firmware v2.8.0. Note:
     * lockdown is also hardware-gated (nRF52 only) — the device advertises real support by sending a `LockdownStatus`,
     * which is the authoritative signal and drives the actual UI state.
     */
    val supportsLockdown = atLeast(V2_8_0)

    /**
     * Support for the Mesh Beacon module (`ModuleConfig.MeshBeaconConfig` broadcast/listen), from the `since_firmware`
     * its `ModuleConfig` field declares. The proto is upstream but the firmware module traces to a community fork, so
     * an older radio would silently ignore the config the editor writes.
     */
    val supportsMeshBeacon = offers(ModuleConfig.mesh_beacon)

    /**
     * Whether the node reports [NodeInfo.heard_on_current_lora] - whether it has heard each node over RF on the LoRa
     * configuration it is using now. Gated to [UNRELEASED] until the firmware side ships. Older firmware never sends
     * the field, and a proto3 bool defaults to false, so an ungated read marks every node as unheard.
     *
     * Deliberately outside [forceEnableAll]: every other capability being wrong in a debug build shows a UI the
     * firmware ignores, but this one being wrong persists false into the node DB and offers real nodes for removal.
     */
    val supportsHeardOnCurrentLora = version != null && version >= UNRELEASED

    /**
     * Whether this firmware's region table contains [region]. Regions declare the release that introduced them via
     * [RegionInfo.minFirmware]; older firmware would treat an unknown region code as UNSET, so the picker hides it.
     */
    fun supportsRegion(region: RegionInfo): Boolean = region.minFirmware?.let { atLeast(it) } ?: true

    /**
     * Whether this firmware's preset table contains [preset] ([ChannelOption.minFirmware]); older firmware silently
     * falls back to LONG_FAST when sent an unknown preset, so the picker hides it.
     */
    fun supportsPreset(preset: ChannelOption): Boolean = preset.minFirmware?.let { atLeast(it) } ?: true

    companion object {
        private val V2_6_8 = DeviceVersion("2.6.8")
        private val V2_6_9 = DeviceVersion("2.6.9")
        private val V2_6_10 = DeviceVersion("2.6.10")
        private val V2_7_12 = DeviceVersion("2.7.12")
        private val V2_7_18 = DeviceVersion("2.7.18")
        private val V2_8_0 = DeviceVersion("2.8.0")
        private val UNRELEASED = DeviceVersion("9.9.9")
    }
}
