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
package org.meshtastic.core.data.radio

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import org.koin.core.annotation.Single
import org.meshtastic.core.ble.BluetoothRepository
import org.meshtastic.core.common.BuildConfigProvider
import org.meshtastic.core.model.DeviceType
import org.meshtastic.core.network.repository.NetworkRepository
import org.meshtastic.core.network.repository.SerialDevicePresence
import org.meshtastic.core.network.transport.parseTcpRadioAddress
import org.meshtastic.core.repository.RadioLink
import org.meshtastic.core.repository.RadioLinks
import org.meshtastic.core.repository.TransportDisconnectReason
import org.meshtastic.sdk.TransportIdentity
import org.meshtastic.sdk.TransportSpec
import org.meshtastic.sdk.transport.ble.BleTransport
import org.meshtastic.sdk.transport.serial.JvmSerialPorts
import org.meshtastic.sdk.transport.tcp.TcpTransport
import java.io.File
import kotlin.time.Duration.Companion.seconds

/** Desktop's radio links: Bluetooth, TCP, WebSocket, serial ports and, in debug builds, the demo radio. */
@Single(binds = [RadioLinks::class])
class DesktopRadioLinks(
    buildConfigProvider: BuildConfigProvider,
    private val networkRepository: NetworkRepository,
    private val serialDevicePresence: SerialDevicePresence,
    bluetoothRepository: BluetoothRepository,
    pipeline: RadioSessionPipeline,
) : BaseRadioLinks(bluetoothRepository, pipeline) {

    override val supportedDeviceTypes: List<DeviceType> = listOf(DeviceType.TCP, DeviceType.BLE, DeviceType.USB)

    // Desktop has no unlock gesture, so Demo Mode is a debug-build feature: it admits the `m` addresses, including
    // the hidden showcase, and offers the demo entry in the picker.
    override val mockTransportEnabled: StateFlow<Boolean> = MutableStateFlow(buildConfigProvider.isDebug)

    /** Desktop bundles no capture asset, so a replay address opens the mock. */
    override val isReplayTransportAvailable: Boolean = false

    override fun openBle(address: String, scope: CoroutineScope): RadioLink {
        val transport = BleTransport(address = address)
        return pipeline.link(
            identity = transport.identity,
            ble = true,
            scope = scope,
            provider = { _, _ ->
                awaitBluetooth()
                transport
            },
            close = transport::shutdown,
        )
    }

    override fun openTcp(rest: String, scope: CoroutineScope): RadioLink {
        val (host, port) = parseTcpRadioAddress(rest)
        val transport = TcpTransport(host, port)
        return pipeline.link(
            identity = transport.identity,
            ble = false,
            scope = scope,
            provider = { attempt, _ ->
                if (attempt > 0) networkRepository.networkAvailable.first { it }
                transport
            },
        )
    }

    /** A rebooting radio's port disappears and comes back, so every attempt waits for it and opens it afresh. */
    override fun openSerial(rest: String, scope: CoroutineScope): RadioLink = pipeline.link(
        identity = TransportIdentity.of(TransportSpec.SerialJvm(rest)),
        ble = false,
        scope = scope,
        provider = { _, _ ->
            while (serialDevicePresence.portExists(rest) == false) delay(PORT_POLL)
            JvmSerialPorts.open(rest)
        },
        failureReason = { serialFailureReason(rest) },
    )

    private companion object {
        val PORT_POLL = 1.seconds
    }
}

/**
 * Reads a failed serial open the way the user can fix it: on Linux, a device node that exists but that this account
 * cannot read and write means it lacks the node's group. jSerialComm reports only that the open failed.
 */
internal fun serialFailureReason(
    address: String,
    isLinux: Boolean = System.getProperty("os.name", "").lowercase().contains("linux"),
): TransportDisconnectReason? {
    val node = File(if (address.startsWith("/")) address else "/dev/$address")
    val denied = isLinux && node.exists() && !(node.canRead() && node.canWrite())
    return if (denied) TransportDisconnectReason.SerialPermissionDenied else null
}
