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
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.encodeToString
import org.meshtastic.core.ble.BleCharacteristic
import org.meshtastic.core.ble.BleConnectionFactory
import org.meshtastic.core.ble.BleConnectionState
import org.meshtastic.core.ble.BleScanner
import org.meshtastic.core.ble.BleWriteType
import org.meshtastic.core.common.util.safeCatching
import org.meshtastic.feature.wifiprovision.NymeaBleConstants
import org.meshtastic.feature.wifiprovision.NymeaBleConstants.CMD_CONNECT
import org.meshtastic.feature.wifiprovision.NymeaBleConstants.CMD_CONNECT_HIDDEN
import org.meshtastic.feature.wifiprovision.NymeaBleConstants.CMD_GET_NETWORKS
import org.meshtastic.feature.wifiprovision.NymeaBleConstants.CMD_SCAN
import org.meshtastic.feature.wifiprovision.NymeaBleConstants.COMMANDER_RESPONSE_UUID
import org.meshtastic.feature.wifiprovision.NymeaBleConstants.RESPONSE_SUCCESS
import org.meshtastic.feature.wifiprovision.NymeaBleConstants.RESPONSE_TIMEOUT
import org.meshtastic.feature.wifiprovision.NymeaBleConstants.SCAN_TIMEOUT
import org.meshtastic.feature.wifiprovision.NymeaBleConstants.SUBSCRIPTION_SETTLE
import org.meshtastic.feature.wifiprovision.NymeaBleConstants.WIRELESS_COMMANDER_UUID
import org.meshtastic.feature.wifiprovision.NymeaBleConstants.WIRELESS_SERVICE_UUID
import org.meshtastic.feature.wifiprovision.model.ProvisionResult
import org.meshtastic.feature.wifiprovision.model.WifiNetwork

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

    // region Public API

    /**
     * Scan for a device advertising the nymea wireless service and connect to it.
     *
     * @param address Optional MAC address filter. If null, the first advertising device is used.
     * @return The discovered device's advertised name on success.
     * @throws IllegalStateException if no device is found within [SCAN_TIMEOUT].
     */
    suspend fun connect(address: String? = null): Result<String> = safeCatching {
        Logger.i { "$TAG: Scanning for nymea-networkmanager device (address=$address)…" }

        val device =
            withTimeout(SCAN_TIMEOUT) {
                scanner.scan(timeout = SCAN_TIMEOUT, serviceUuid = WIRELESS_SERVICE_UUID, address = address).first()
            }

        val deviceName = device.name ?: device.address
        Logger.i { "$TAG: Found device: ${device.name} @ ${device.address}" }

        val state = bleConnection.connectAndAwait(device, SCAN_TIMEOUT)
        check(state is BleConnectionState.Connected) { "Failed to connect to ${device.address} — final state: $state" }

        Logger.i { "$TAG: Connected. Discovering wireless service…" }

        bleConnection.profile(WIRELESS_SERVICE_UUID) { service ->
            val subscribed = CompletableDeferred<Unit>()

            service
                .observe(responseChar)
                .onEach { bytes ->
                    val message = reassembler.feed(bytes)
                    if (message != null) {
                        Logger.d { "$TAG: ← $message" }
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

        deviceName
    }

    /**
     * Trigger a fresh WiFi scan on the device, then return the list of visible networks.
     *
     * Sends: CMD_SCAN (4), waits for ack, then CMD_GET_NETWORKS (0).
     */
    suspend fun scanNetworks(): Result<List<WifiNetwork>> = safeCatching {
        // Trigger scan
        sendCommand(NymeaJson.encodeToString(NymeaSimpleCommand(CMD_SCAN)))
        val scanAck = NymeaJson.decodeFromString<NymeaResponse>(waitForResponse())
        if (scanAck.responseCode != RESPONSE_SUCCESS) {
            error("Scan command failed: ${nymeaErrorMessage(scanAck.responseCode)}")
        }

        // Fetch results
        sendCommand(NymeaJson.encodeToString(NymeaSimpleCommand(CMD_GET_NETWORKS)))
        val networksResponse = NymeaJson.decodeFromString<NymeaNetworksResponse>(waitForResponse())
        if (networksResponse.responseCode != RESPONSE_SUCCESS) {
            error("GetNetworks failed: ${nymeaErrorMessage(networksResponse.responseCode)}")
        }

        networksResponse.networks.map { entry ->
            WifiNetwork(
                ssid = entry.ssid,
                bssid = entry.bssid,
                signalStrength = entry.signalStrength,
                isProtected = entry.protection != 0,
            )
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
            sendCommand(json)
            val response = NymeaJson.decodeFromString<NymeaResponse>(waitForResponse())
            if (response.responseCode == RESPONSE_SUCCESS) {
                ProvisionResult.Success
            } else {
                ProvisionResult.Failure(response.responseCode, nymeaErrorMessage(response.responseCode))
            }
        }
            .getOrElse { e ->
                Logger.e(e) { "$TAG: Provision failed" }
                ProvisionResult.Failure(-1, e.message ?: "Unknown error")
            }
    }

    /** Disconnect and cancel the service scope. */
    suspend fun close() {
        bleConnection.disconnect()
        reassembler.reset()
        serviceScope.cancel()
    }

    /**
     * Synchronous teardown — cancels the service scope (and its child BLE connection) without suspending.
     *
     * Use this from `ViewModel.onCleared()` where `viewModelScope` is already cancelled and launching a new coroutine
     * is not possible.
     */
    fun cancel() {
        reassembler.reset()
        serviceScope.cancel()
    }

    // endregion

    // region Internal helpers

    /** Encode [json] into ≤20-byte packets and write each one WITH_RESPONSE to the commander characteristic. */
    private suspend fun sendCommand(json: String) {
        Logger.d { "$TAG: → $json" }
        val packets = NymeaPacketCodec.encode(json)
        bleConnection.profile(WIRELESS_SERVICE_UUID) { service ->
            for (packet in packets) {
                service.write(commanderChar, packet, BleWriteType.WITH_RESPONSE)
            }
        }
    }

    /** Wait up to [RESPONSE_TIMEOUT] for a complete JSON response from the notification channel. */
    private suspend fun waitForResponse(): String = withTimeout(RESPONSE_TIMEOUT) { responseChannel.receive() }

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
