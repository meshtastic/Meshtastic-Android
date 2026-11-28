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

import kotlinx.coroutines.NonCancellable
import org.meshtastic.core.model.Position
import org.meshtastic.proto.Channel
import org.meshtastic.proto.Config
import org.meshtastic.proto.DeviceConnectionStatus
import org.meshtastic.proto.DeviceMetadata
import org.meshtastic.proto.HamParameters
import org.meshtastic.proto.ModuleConfig
import org.meshtastic.proto.SharedContact
import org.meshtastic.proto.User
import org.meshtastic.sdk.AdminResult
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Device configuration and control operations.
 *
 * Mirrors the SDK's `AdminApi` interface — local and remote configuration, channel management, owner identity, device
 * lifecycle commands, and batch edit sessions. Implementations delegate to the session's `RadioClient.admin`.
 * Operations that enqueue outbound admin packets can throw [PacketQueueRejectedException] when the current transport
 * cannot admit the handoff. “Fire-and-forget” means no device response is awaited, not that local queue rejection is
 * silently ignored.
 *
 * @see RadioController
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

    /**
     * Sends [config] to the local node like [setLocalConfig], without the optimistic cache write, for a caller that
     * read the config on one connection and reads it back. It is sent only while [expectedNodeNum] is still the local
     * node on connection lifecycle `version` [expectedConnectionVersion]; otherwise nothing is sent.
     *
     * @throws PacketQueueRejectedException when the local node or the connection is no longer the expected one.
     */
    suspend fun sendLocalConfig(config: Config, expectedNodeNum: Int, expectedConnectionVersion: Long)

    /**
     * Sends [config] to the local node as request [packetId] with the optimistic cache write of [setConfig], for a
     * settings save whose follow-up belongs to the connection it started on. It is sent only while [connection] is
     * still the local node and connection; otherwise nothing is sent.
     *
     * @throws PacketQueueRejectedException when the local node or the connection is no longer [connection].
     */
    suspend fun setLocalConfigForConnection(config: Config, packetId: Int, connection: LocalNodeConnection)

    /** Updates a local radio channel. Same fire-and-forget contract as [setLocalConfig]. */
    suspend fun setLocalChannel(channel: Channel)

    /**
     * Enables or disables live display mirroring on the locally connected node. While enabled, the device streams its
     * framebuffer as `FromRadio.display_frame` chunks — collect them via [DisplayMirrorManager]. Non-suspend (immediate
     * send, bypassing the outbound FIFO) so lifecycle teardown can reliably disable the stream.
     */
    fun setDisplayMirror(enabled: Boolean)

    /** Requests a single framebuffer frame from the locally connected node, delivered like mirrored frames. */
    fun requestDisplayFrame()

    /**
     * Injects a physical input event (button/key/touch) into the locally connected node's UI via the firmware
     * InputBroker. Event codes are the firmware's `input_broker_event` values. Non-suspend immediate send: input must
     * not queue behind bulk admin traffic.
     */
    fun sendInputEvent(eventCode: Int, kbChar: Int = 0, touchX: Int = 0, touchY: Int = 0)

    // ── Remote configuration ────────────────────────────────────────────────
    //
    // Operations that return an [AdminResult] run on the active session's SDK admin API, addressed with `forNode`
    // unless [destNum] is the connected node. With no active session they return [AdminResult.NodeUnreachable].

    /** Updates the owner (user info) on a node. */
    suspend fun setOwner(destNum: Int, user: User): AdminResult<Unit>

    /**
     * Enables amateur-radio (ham) mode on a node via `AdminMessage.set_ham_mode`.
     *
     * Must target only the locally connected node — firmware ham onboarding is a local operation; the implementation
     * ignores requests for any other node. The firmware handler rewrites the owner (long_name = call_sign, with an
     * optional [HamParameters.long_name] appended after `//`), flips `is_licensed`, disables encryption, applies
     * [HamParameters.tx_power]/[HamParameters.frequency] to the LoRa config verbatim, and reboots. The implementation
     * echoes the local node's current LoRa values into those two fields so a re-send never wipes the node's overrides;
     * caller-supplied [HamParameters.tx_power]/[HamParameters.frequency] are ignored. Intentionally absent from
     * [AdminEditScope]: ham enablement is not a batch-edit operation.
     */
    suspend fun setHamMode(destNum: Int, hamParameters: HamParameters): AdminResult<Unit>

    /** Updates the general configuration on a node. */
    suspend fun setConfig(destNum: Int, config: Config): AdminResult<Unit>

    /** Updates a module configuration on a node. */
    suspend fun setModuleConfig(destNum: Int, config: ModuleConfig): AdminResult<Unit>

    /** Updates a channel configuration on a node. */
    suspend fun setRemoteChannel(destNum: Int, channel: Channel): AdminResult<Unit>

    /** Sets a fixed position on a remote node. */
    suspend fun setFixedPosition(destNum: Int, position: Position)

    /** Updates the notification ringtone on a remote node. */
    suspend fun setRingtone(destNum: Int, ringtone: String)

    /**
     * Plays the node's saved ringtone once via `AdminMessage.play_ringtone_request`. Fire-and-forget: the firmware
     * sends no response, and builds without RTTTL support ignore it.
     */
    suspend fun playRingtone(destNum: Int)

    /** Updates the canned messages configuration on a remote node. */
    suspend fun setCannedMessages(destNum: Int, messages: String)

    /**
     * Syncs a node's real-time clock to the phone's current time via `AdminMessage.set_time_only`.
     *
     * Mirrors the Python CLI's `Node.setTime`: an accurate epoch-seconds timestamp is sent so a remote node whose RTC
     * has drifted can be corrected without an on-site visit. The firmware applies the value without an admin response.
     */
    suspend fun setTime(destNum: Int): AdminResult<Unit>

    /**
     * Adds [contact] to [destNum]'s node DB via `AdminMessage.add_contact`. Firmware also favorites it, except on a
     * `CLIENT_BASE` node. Returns `true` once [destNum] acknowledges.
     */
    suspend fun addRemoteContact(destNum: Int, contact: SharedContact): Boolean

    /** Favorites or unfavorites [nodeNum] in [destNum]'s node DB. Returns `true` once [destNum] acknowledges. */
    suspend fun setRemoteFavorite(destNum: Int, nodeNum: Int, favorite: Boolean): Boolean

    /** Ignores or unignores [nodeNum] in [destNum]'s node DB. Returns `true` once [destNum] acknowledges. */
    suspend fun setRemoteIgnored(destNum: Int, nodeNum: Int, ignored: Boolean): Boolean

    /** Removes [nodeNum] from [destNum]'s node DB. Returns `true` once [destNum] acknowledges. */
    suspend fun removeRemoteNode(destNum: Int, nodeNum: Int): Boolean

    // ── Remote queries ──────────────────────────────────────────────────────
    //
    // A read addressed to another node waits up to [REMOTE_READ_TIMEOUT], so a multi-hop reply that outlives the
    // caller's own UX deadline still arrives.

    /** Reads the device metadata, the remote-admin preflight. */
    suspend fun getDeviceMetadata(destNum: Int): AdminResult<DeviceMetadata>

    /** Reads the current owner (user info). */
    suspend fun getOwner(destNum: Int): AdminResult<User>

    /** Reads a configuration section; [configType] is an `AdminMessage.ConfigType` value. */
    suspend fun getConfig(destNum: Int, configType: Int): AdminResult<Config>

    /** Reads a module configuration section; [moduleConfigType] is an `AdminMessage.ModuleConfigType` value. */
    suspend fun getModuleConfig(destNum: Int, moduleConfigType: Int): AdminResult<ModuleConfig>

    /** Reads the channel at zero-based [index]. */
    suspend fun getChannel(destNum: Int, index: Int): AdminResult<Channel>

    /** Reads the current ringtone. */
    suspend fun getRingtone(destNum: Int): AdminResult<String>

    /** Reads the current canned messages. */
    suspend fun getCannedMessages(destNum: Int): AdminResult<String>

    /** Reads the hardware connection status. */
    suspend fun getDeviceConnectionStatus(destNum: Int): AdminResult<DeviceConnectionStatus>

    // ── Device lifecycle ────────────────────────────────────────────────────

    /** Commands a node to reboot. */
    suspend fun reboot(destNum: Int): AdminResult<Unit>

    /** Commands a node to reboot into DFU mode; firmware answers nothing, so success means sent. */
    suspend fun rebootToDfu(nodeNum: Int): AdminResult<Unit>

    /** Sends an ESP32 OTA reboot request without waiting for the node's answer. */
    suspend fun requestRebootOta(destNum: Int, mode: Int, hash: ByteArray?)

    /** Commands a node to shut down. */
    suspend fun shutdown(destNum: Int): AdminResult<Unit>

    /** Performs a factory reset on a node, BLE bonds included. */
    suspend fun factoryReset(destNum: Int): AdminResult<Unit>

    /** Resets the NodeDB on a node. */
    suspend fun nodedbReset(destNum: Int, preserveFavorites: Boolean): AdminResult<Unit>

    // ── Batch edit ──────────────────────────────────────────────────────────

    /**
     * Runs [block] inside a begin/commit edit-settings transaction on [destNum].
     *
     * The session is opened before [block] runs and committed after it returns normally, so callers can neither forget
     * to commit nor leak a half-open session. Operations inside the block target [destNum] implicitly. Mirrors the
     * SDK's `AdminApi.editSettings { }`.
     *
     * All admin packets for the session — begin, the [block]'s writes, and commit — are issued from the calling
     * coroutine, which is required for the firmware to associate them with one transaction. The implementation waits
     * for radio queue acceptance at both boundaries, so callers cannot start a later rebooting stage while this
     * transaction is still queued or uncommitted.
     *
     * For the locally connected node, a commit dispatched immediately before the expected transport departure is
     * treated as accepted because the reboot can prevent an acknowledgement from returning. Local owner, config,
     * module-config, and fixed-position projections are staged until that commit is accepted; they are discarded when
     * the block or commit fails so local state cannot describe settings the device did not commit.
     *
     * Firmware exposes no abort boundary, so commit is attempted in [NonCancellable] context even when [block] throws
     * or the caller is cancelled. The original block failure is rethrown, with a distinct commit failure attached as a
     * suppressed exception. When [block] succeeds and only the commit fails, the commit failure itself is thrown and
     * the staged projections are discarded.
     *
     * @throws EditSettingsTransactionException when the radio rejects or does not answer the begin or commit boundary.
     * @throws PacketQueueRejectedException when the outbound queue refuses a transactional write issued by [block].
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

    /**
     * Updates a channel configuration on the session's node. The batch operation that owns the complete target set is
     * responsible for authoritative local-cache reconciliation after [AdminController.editSettings] returns.
     */
    suspend fun setChannel(channel: Channel)

    /** Sets a fixed position on the session's node. */
    suspend fun setFixedPosition(position: Position)
}

/** How long a read addressed to another node waits: the 30 s UX deadline plus the two-minute late-reply grace. */
val REMOTE_READ_TIMEOUT: Duration = 150.seconds

/**
 * The local node and the connection lifecycle `version` a write was started on.
 *
 * @property nodeNum the node that was local when the write started.
 * @property connectionVersion the `version` of [ConnectionStateProvider.connectionLifecycle] at that moment.
 */
data class LocalNodeConnection(val nodeNum: Int, val connectionVersion: Long)
