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
package org.meshtastic.core.repository

import org.meshtastic.core.model.Position
import org.meshtastic.proto.Channel
import org.meshtastic.proto.Config
import org.meshtastic.proto.HamParameters
import org.meshtastic.proto.ModuleConfig
import org.meshtastic.proto.User

/**
 * Device configuration and control operations.
 *
 * Mirrors the SDK's `AdminApi` interface — local and remote configuration, channel management, owner identity, device
 * lifecycle commands, and batch edit sessions. When the SDK is adopted, this interface becomes the adapter boundary:
 * implementations delegate to `RadioClient.admin`.
 *
 * @see RadioController which extends this interface for backward compatibility
 */
@Suppress("TooManyFunctions")
interface AdminController {

    // ── Local configuration ─────────────────────────────────────────────────

    /**
     * Updates the local radio configuration.
     *
     * Fire-and-forget by design: the device is the source of truth. Local persistence is an optimistic cache that will
     * self-heal on next config refresh.
     */
    suspend fun setLocalConfig(config: Config)

    /** Updates a local radio channel. Same fire-and-forget contract as [setLocalConfig]. */
    suspend fun setLocalChannel(channel: Channel)

    // ── Remote configuration ────────────────────────────────────────────────

    /** Updates the owner (user info) on a remote node. */
    suspend fun setOwner(destNum: Int, user: User, packetId: Int)

    /**
     * Enables amateur-radio (ham) mode on a node via `AdminMessage.set_ham_mode`.
     *
     * Must target only the locally connected node — firmware ham onboarding is a local operation; the implementation
     * ignores requests for any other node. The firmware handler rewrites the owner (long_name = call_sign), flips
     * `is_licensed`, disables encryption, applies [HamParameters.tx_power]/[HamParameters.frequency] to the LoRa config
     * verbatim, and reboots. The implementation echoes the local node's current LoRa values into those two fields so a
     * re-send never wipes the node's overrides; caller-supplied [HamParameters.tx_power]/[HamParameters.frequency] are
     * ignored. Intentionally absent from [AdminEditScope]: ham enablement is not a batch-edit operation.
     */
    suspend fun setHamMode(destNum: Int, hamParameters: HamParameters, packetId: Int)

    /** Updates the general configuration on a remote node. */
    suspend fun setConfig(destNum: Int, config: Config, packetId: Int)

    /** Updates a module configuration on a remote node. */
    suspend fun setModuleConfig(destNum: Int, config: ModuleConfig, packetId: Int)

    /** Updates a channel configuration on a remote node. */
    suspend fun setRemoteChannel(destNum: Int, channel: Channel, packetId: Int)

    /** Sets a fixed position on a remote node. */
    suspend fun setFixedPosition(destNum: Int, position: Position)

    /** Updates the notification ringtone on a remote node. */
    suspend fun setRingtone(destNum: Int, ringtone: String)

    /** Updates the canned messages configuration on a remote node. */
    suspend fun setCannedMessages(destNum: Int, messages: String)

    /**
     * Syncs a node's real-time clock to the phone's current time via `AdminMessage.set_time_only`.
     *
     * Mirrors the Python CLI's `Node.setTime`: an accurate epoch-seconds timestamp is sent so a remote node whose RTC
     * has drifted can be corrected without an on-site visit. Fire-and-forget — the firmware applies the value without
     * an admin response (the routing ACK confirms delivery).
     */
    suspend fun setTime(destNum: Int, packetId: Int)

    // ── Remote queries ──────────────────────────────────────────────────────

    /** Requests the current owner (user info) from a remote node. */
    suspend fun getOwner(destNum: Int, packetId: Int)

    /** Requests a specific configuration section from a remote node. */
    suspend fun getConfig(destNum: Int, configType: Int, packetId: Int)

    /** Requests a module configuration section from a remote node. */
    suspend fun getModuleConfig(destNum: Int, moduleConfigType: Int, packetId: Int)

    /** Requests a specific channel configuration from a remote node. */
    suspend fun getChannel(destNum: Int, index: Int, packetId: Int)

    /** Requests the current ringtone from a remote node. */
    suspend fun getRingtone(destNum: Int, packetId: Int)

    /** Requests the current canned messages from a remote node. */
    suspend fun getCannedMessages(destNum: Int, packetId: Int)

    /** Requests the hardware connection status from a remote node. */
    suspend fun getDeviceConnectionStatus(destNum: Int, packetId: Int)

    // ── Device lifecycle ────────────────────────────────────────────────────

    /** Commands a node to reboot. */
    suspend fun reboot(destNum: Int, packetId: Int)

    /** Commands a node to reboot into DFU mode. */
    suspend fun rebootToDfu(nodeNum: Int)

    /** Initiates an OTA reboot request. */
    suspend fun requestRebootOta(requestId: Int, destNum: Int, mode: Int, hash: ByteArray?)

    /** Commands a node to shut down. */
    suspend fun shutdown(destNum: Int, packetId: Int)

    /** Performs a factory reset on a node. */
    suspend fun factoryReset(destNum: Int, packetId: Int)

    /** Resets the NodeDB on a node. */
    suspend fun nodedbReset(destNum: Int, packetId: Int, preserveFavorites: Boolean)

    // ── Batch edit ──────────────────────────────────────────────────────────

    /**
     * Runs [block] inside a begin/commit edit-settings transaction on [destNum].
     *
     * The session is opened before [block] runs and committed after it returns normally, so callers can neither forget
     * to commit nor leak a half-open session. Operations inside the block target [destNum] implicitly. Mirrors the
     * SDK's `AdminApi.editSettings { }`.
     *
     * All admin packets for the session — begin, the [block]'s writes, and commit — are issued from the calling
     * coroutine, which is required for the firmware to associate them with one transaction.
     */
    suspend fun editSettings(destNum: Int, block: suspend AdminEditScope.() -> Unit)

    /** Runs [block] as an [editSettings] transaction against the local node. */
    suspend fun editLocalSettings(block: suspend AdminEditScope.() -> Unit)
}

/**
 * Configuration operations valid inside an [AdminController.editSettings] transaction, scoped to a single destination
 * node so callers don't repeat the node number or manage packet IDs. Mirrors the SDK's `AdminEdit`.
 */
interface AdminEditScope {
    /** Updates the owner (user info) on the session's node. */
    suspend fun setOwner(user: User)

    /** Updates the general configuration on the session's node. */
    suspend fun setConfig(config: Config)

    /** Updates a module configuration on the session's node. */
    suspend fun setModuleConfig(config: ModuleConfig)

    /** Updates a channel configuration on the session's node. */
    suspend fun setChannel(channel: Channel)

    /** Sets a fixed position on the session's node. */
    suspend fun setFixedPosition(position: Position)
}
