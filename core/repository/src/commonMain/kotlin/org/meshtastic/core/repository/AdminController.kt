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

    /** Signals the start of a batch configuration session. */
    suspend fun beginEditSettings(destNum: Int)

    /** Commits all pending configuration changes in a batch session. */
    suspend fun commitEditSettings(destNum: Int)
}
