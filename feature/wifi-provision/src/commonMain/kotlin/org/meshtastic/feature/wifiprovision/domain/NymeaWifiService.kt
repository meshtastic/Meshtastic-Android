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
package org.meshtastic.feature.wifiprovision.domain

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.SerializationException
import org.meshtastic.core.ble.BleCharacteristic
import org.meshtastic.core.ble.BleConnectionFactory
import org.meshtastic.core.ble.BleConnectionState
import org.meshtastic.core.ble.BleDevice
import org.meshtastic.core.ble.BleScanner
import org.meshtastic.core.ble.BleWriteType
import org.meshtastic.core.ble.bleAddressesMatch
import org.meshtastic.core.common.util.safeCatching
import org.meshtastic.core.model.util.anonymize
import org.meshtastic.feature.wifiprovision.NymeaBleConstants
import org.meshtastic.feature.wifiprovision.NymeaBleConstants.CMD_CONNECT
import org.meshtastic.feature.wifiprovision.NymeaBleConstants.CMD_CONNECT_HIDDEN
import org.meshtastic.feature.wifiprovision.NymeaBleConstants.CMD_GET_CONNECTION
import org.meshtastic.feature.wifiprovision.NymeaBleConstants.CMD_GET_NETWORKS
import org.meshtastic.feature.wifiprovision.NymeaBleConstants.CMD_SCAN
import org.meshtastic.feature.wifiprovision.NymeaBleConstants.COMMANDER_RESPONSE_UUID
import org.meshtastic.feature.wifiprovision.NymeaBleConstants.CONNECTION_INFO_TIMEOUT
import org.meshtastic.feature.wifiprovision.NymeaBleConstants.NO_RESPONSE_CODE
import org.meshtastic.feature.wifiprovision.NymeaBleConstants.RESPONSE_SUCCESS
import org.meshtastic.feature.wifiprovision.NymeaBleConstants.RESPONSE_TIMEOUT
import org.meshtastic.feature.wifiprovision.NymeaBleConstants.SCAN_TIMEOUT
import org.meshtastic.feature.wifiprovision.NymeaBleConstants.SUBSCRIPTION_SETTLE
import org.meshtastic.feature.wifiprovision.NymeaBleConstants.WIRELESS_COMMANDER_UUID
import org.meshtastic.feature.wifiprovision.NymeaBleConstants.WIRELESS_SERVICE_UUID
import org.meshtastic.feature.wifiprovision.model.ProvisionResult
import org.meshtastic.feature.wifiprovision.model.WifiNetwork
import kotlin.time.Duration

/**
 * GATT client for the nymea-networkmanager WiFi provisioning profile.
 *
 * Responsibilities:
 * - Scan for a device advertising [WIRELESS_SERVICE_UUID].
 * - Connect and subscribe to the Commander Response characteristic.
 * - Send JSON commands (chunked into ≤20-byte BLE packets) via the Wireless Commander characteristic.
 * - Reassemble newline-terminated JSON responses from notification packets.
 * - Parse the nymea JSON protocol into typed Kotlin results.
 *
 * Lifecycle: create once per provisioning session, call [connect], use [scanNetworks] / [provision], then [close].
 */
class NymeaWifiService(
    private val scanner: BleScanner,
    connectionFactory: BleConnectionFactory,
    dispatcher: CoroutineDispatcher,
) {
    private val serviceScope = CoroutineScope(SupervisorJob() + dispatcher)
    private val bleConnection = connectionFactory.create(serviceScope, TAG)

    private val commanderChar = BleCharacteristic(WIRELESS_COMMANDER_UUID)
    private val responseChar = BleCharacteristic(COMMANDER_RESPONSE_UUID)

    /** Unbounded channel — the observer coroutine feeds complete JSON strings here. */
    private val responseChannel = Channel<String>(Channel.UNLIMITED)
    private val reassembler = NymeaPacketCodec.Reassembler()

    /** One command in flight at a time: replies carry only the command code, so overlapping exchanges could mix. */
    private val exchangeLock = Mutex()

    /**
     * Set when a command went unanswered. The device may still send that reply, and nothing in it says which command it
     * answers, so every later command on this session fails; a new connection starts clean.
     */
    private var unsynchronized = false

    /** Set by [close]: a closed service sends nothing and opens no connection, whoever still holds it. */
    private var closed = false

    // region Public API

    /** Address of the device [connect] last connected to, or null before a connection. */
    var deviceAddress: String? = null
        private set

    /**
     * Find the device with [address], or the one the user picks in the scanner's own chooser, and connect to it.
     *
     * A scanner that lists devices itself needs an [address]: choosing among advertisers is the caller's job, which
     * then calls [connect] with the [BleDevice] picked.
     *
     * @param address The MAC address to connect to, or null where [BleScanner.picksDevice] is true.
     * @return The device's advertised name on success; a [NoDevicePickedException] failure when the scanner is a device
     *   chooser the user dismissed; a [DeviceNotFoundException] failure when the device does not advertise within
     *   [SCAN_TIMEOUT]. Cancelling the caller still cancels: only the timeout is converted to a failure.
     */
    suspend fun connect(address: String?): Result<String> = safeCatching {
        Logger.i { "$TAG: Scanning for nymea-networkmanager device (address=${address.anonymize()})…" }

        val scan = scanner.scan(timeout = SCAN_TIMEOUT, serviceUuid = WIRELESS_SERVICE_UUID, address = address)
        val device =
            if (scanner.picksDevice) {
                // A chooser stays open until the user answers, so no timeout; dismissing it ends the scan empty.
                scan.firstOrNull() ?: throw NoDevicePickedException()
            } else {
                val wanted = requireNotNull(address) { "A device must be chosen before connecting" }
                // A timeout is the device not being there, a failure; withTimeout would throw a CancellationException,
                // which neither safeCatching nor the caller's failure handling treats as one.
                withTimeoutOrNull(SCAN_TIMEOUT) { scan.firstOrNull { bleAddressesMatch(it.address, wanted) } }
                    ?: throw DeviceNotFoundException()
            }

        openConnection(device)
    }

    /** Connect to [device], which the caller already chose from a scan, and return its advertised name. */
    suspend fun connect(device: BleDevice): Result<String> = safeCatching { openConnection(device) }

    private suspend fun openConnection(device: BleDevice): String {
        check(!closed) { "The connection is closed" }
        val deviceName = device.name ?: device.address
        Logger.i { "$TAG: Connecting to device @ ${device.address.anonymize()}" }
        deviceAddress = device.address

        val state = bleConnection.connectAndAwait(device, SCAN_TIMEOUT)
        check(state is BleConnectionState.Connected) {
            "Failed to connect to ${device.address.anonymize()} — final state: $state"
        }

        Logger.i { "$TAG: Connected. Discovering wireless service…" }

        try {
            bleConnection.profile(WIRELESS_SERVICE_UUID) { service ->
                val subscribed = CompletableDeferred<Unit>()

                service
                    .observe(responseChar)
                    .onEach { bytes ->
                        val message = reassembler.feed(bytes)
                        if (message != null) {
                            Logger.d { "$TAG: ← response (${message.length} chars)" }
                            responseChannel.trySend(message)
                        }
                        if (!subscribed.isCompleted) subscribed.complete(Unit)
                    }
                    .catch { e ->
                        Logger.e(e) { "$TAG: Error in response characteristic subscription" }
                        if (!subscribed.isCompleted) subscribed.completeExceptionally(e)
                    }
                    .launchIn(this)

                delay(SUBSCRIPTION_SETTLE)
                if (!subscribed.isCompleted) subscribed.complete(Unit)
                subscribed.await()

                Logger.i { "$TAG: Wireless service ready" }
            }
        } catch (e: TimeoutCancellationException) {
            // profile() bounds service discovery with withTimeout, which is a failure here and not a cancellation.
            throw IllegalStateException("Timed out waiting for the wireless service", e)
        }

        return deviceName
    }

    /**
     * Trigger a fresh WiFi scan on the device, then return the list of visible networks.
     *
     * Sends: CMD_SCAN (4), waits for ack, then CMD_GET_NETWORKS (0).
     */
    suspend fun scanNetworks(): Result<List<WifiNetwork>> = safeCatching {
        exchangeLock.withLock {
            // Trigger scan
            sendCommand(CMD_SCAN, NymeaJson.encodeToString(NymeaSimpleCommand(CMD_SCAN)))
            val scanAck = decodeReply<NymeaResponse>(waitForResponse(CMD_SCAN))
            if (scanAck.responseCode != RESPONSE_SUCCESS) {
                error("Scan command failed: ${nymeaErrorMessage(scanAck.responseCode)}")
            }

            // Fetch results
            sendCommand(CMD_GET_NETWORKS, NymeaJson.encodeToString(NymeaSimpleCommand(CMD_GET_NETWORKS)))
            val networksResponse = decodeReply<NymeaNetworksResponse>(waitForResponse(CMD_GET_NETWORKS))
            if (networksResponse.responseCode != RESPONSE_SUCCESS) {
                error("GetNetworks failed: ${nymeaErrorMessage(networksResponse.responseCode)}")
            }

            // A success with no list is a bad reply; an empty list (`[]`) is the device saying there are none.
            val networks = networksResponse.networks ?: throw MalformedReplyException()
            networks.map { entry ->
                WifiNetwork(
                    ssid = entry.ssid,
                    bssid = entry.bssid,
                    signalStrength = entry.signalStrength,
                    isProtected = entry.protection != 0,
                )
            }
        }
    }

    /**
     * Provision the device with the given WiFi credentials.
     *
     * Sends CMD_CONNECT (1) or CMD_CONNECT_HIDDEN (2) with the SSID and password. The response error code is mapped to
     * a [ProvisionResult].
     *
     * @param ssid The target network SSID.
     * @param password The network password. Pass an empty string for open networks.
     * @param hidden Set to `true` to target a hidden (non-broadcasting) network.
     */
    suspend fun provision(ssid: String, password: String, hidden: Boolean = false): ProvisionResult {
        val cmd = if (hidden) CMD_CONNECT_HIDDEN else CMD_CONNECT
        val json =
            NymeaJson.encodeToString(
                NymeaConnectCommand(command = cmd, params = NymeaConnectParams(ssid = ssid, password = password)),
            )

        return safeCatching {
            exchangeLock.withLock {
                sendCommand(cmd, json)
                // The service writes code 1 in every reply to a connect, and answers an unknown command such as 2
                // with code 1 and InvalidCommand. Code 2 is accepted only for a hidden request, as the README has it.
                val reply =
                    if (hidden) waitForResponse(CMD_CONNECT, CMD_CONNECT_HIDDEN) else waitForResponse(CMD_CONNECT)
                val response = decodeReply<NymeaResponse>(reply)
                if (response.responseCode == RESPONSE_SUCCESS) {
                    val ipAddress =
                        response.connectionInfo?.ipAddress?.takeIf { it.isNotBlank() } ?: fetchConnectionIpAddress()
                    ProvisionResult.Success(ipAddress = ipAddress)
                } else {
                    ProvisionResult.Failure(response.responseCode, nymeaErrorMessage(response.responseCode))
                }
            }
        }
            .getOrElse { e ->
                Logger.e(e) { "$TAG: Provision failed" }
                ProvisionResult.Failure(
                    if (e is DeviceResponseTimeoutException) NO_RESPONSE_CODE else -1,
                    e.message ?: "Unknown error",
                )
            }
    }

    /** Disconnect, which releases the BLE peripheral, then cancel the service scope. */
    suspend fun close() {
        closed = true
        try {
            bleConnection.disconnect()
        } finally {
            reassembler.reset()
            serviceScope.cancel()
        }
    }

    // endregion

    // region Internal helpers

    /**
     * Encode [json] into ≤20-byte packets and write each one WITH_RESPONSE to the commander characteristic. Only the
     * [command] code is logged: a Connect payload carries the WiFi password, and even its length gives away the SSID
     * and password lengths.
     */
    private suspend fun sendCommand(command: Int, json: String) {
        check(!closed) { "The connection is closed" }
        // After a missed reply the next one may be the late one; every command would be unanswerable by its own reply.
        if (unsynchronized) throw DeviceResponseTimeoutException()
        Logger.d { "$TAG: → command=$command" }
        val packets = NymeaPacketCodec.encode(json)
        try {
            bleConnection.profile(WIRELESS_SERVICE_UUID) { service ->
                for (packet in packets) {
                    service.write(commanderChar, packet, BleWriteType.WITH_RESPONSE)
                }
            }
        } catch (e: TimeoutCancellationException) {
            // profile() bounds itself with withTimeout. Part of the command may have been written, so the session is
            // as unusable as after a missed reply.
            unsynchronized = true
            throw DeviceResponseTimeoutException(e)
        }
    }

    /**
     * Wait up to [timeout] for the reply to a command, which echoes its code, so a reply with any other code is not
     * this command's and is skipped within the same deadline. A timeout fails with a [DeviceResponseTimeoutException]
     * rather than the cancellation `withTimeout` throws, and ends the session's usefulness: the late reply may still
     * arrive, and the protocol has no request id to tell it from the reply to the next command with the same code.
     */
    private suspend fun waitForResponse(vararg expected: Int, timeout: Duration = RESPONSE_TIMEOUT): String {
        val reply =
            withTimeoutOrNull(timeout) {
                var found: String? = null
                while (found == null) {
                    val message = responseChannel.receive()
                    val command = safeCatching {
                        NymeaJson.decodeFromString<NymeaReplyHeader>(message).command
                    }
                        .getOrNull()
                    if (command != null && command in expected) {
                        found = message
                    } else {
                        Logger.d { "$TAG: skipped a reply for command=$command, waiting for ${expected.toList()}" }
                    }
                }
                found
            }
        if (reply == null) {
            unsynchronized = true
            throw DeviceResponseTimeoutException()
        }
        return reply
    }

    /**
     * Best-effort query for current connection info (`CMD_GET_CONNECTION`), returning the reported IP address.
     *
     * Uses a short timeout because this is an optional enrichment for UX, not a provisioning success criterion.
     */
    private suspend fun fetchConnectionIpAddress(): String? = safeCatching {
        sendCommand(CMD_GET_CONNECTION, NymeaJson.encodeToString(NymeaSimpleCommand(CMD_GET_CONNECTION)))
        val reply = waitForResponse(CMD_GET_CONNECTION, timeout = CONNECTION_INFO_TIMEOUT)
        val response = decodeReply<NymeaResponse>(reply)
        if (response.responseCode == RESPONSE_SUCCESS) {
            response.connectionInfo?.ipAddress?.takeIf { it.isNotBlank() }
        } else {
            null
        }
    }
        .getOrNull()

    /** Decodes a reply, turning a missing, null or wrongly typed field into a [MalformedReplyException]. */
    private inline fun <reified T> decodeReply(raw: String): T = try {
        NymeaJson.decodeFromString<T>(raw)
    } catch (e: SerializationException) {
        throw MalformedReplyException(e)
    }

    private fun nymeaErrorMessage(code: Int): String = when (code) {
        NymeaBleConstants.RESPONSE_INVALID_COMMAND -> "Invalid command"
        NymeaBleConstants.RESPONSE_INVALID_PARAMETER -> "Invalid parameter"
        NymeaBleConstants.RESPONSE_NETWORK_MANAGER_UNAVAILABLE -> "NetworkManager not available"
        NymeaBleConstants.RESPONSE_WIRELESS_UNAVAILABLE -> "Wireless adapter not available"
        NymeaBleConstants.RESPONSE_NETWORKING_DISABLED -> "Networking disabled"
        NymeaBleConstants.RESPONSE_WIRELESS_DISABLED -> "Wireless disabled"
        else -> "Unknown error (code $code)"
    }

    // endregion

    companion object {
        private const val TAG = "NymeaWifiService"
    }
}

/** The device chooser closed without a pick, which is the user backing out rather than a failure. */
class NoDevicePickedException : IllegalStateException("No device was picked")

/** The device to connect to did not advertise within the scan window. */
class DeviceNotFoundException : IllegalStateException("Device not found")

/** A reply decoded as neither success nor a device error, such as one with no status. */
class MalformedReplyException(cause: Throwable? = null) :
    IllegalStateException("Unexpected reply from the device", cause)

/** The connected device sent no answer to a command in time. */
class DeviceResponseTimeoutException(cause: Throwable? = null) :
    IllegalStateException("No response from the device", cause)
