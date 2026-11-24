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

import android.app.Application
import android.content.Context
import android.provider.Settings
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import org.koin.core.annotation.Single
import org.meshtastic.core.ble.BluetoothRepository
import org.meshtastic.core.common.BuildConfigProvider
import org.meshtastic.core.common.state.HiddenFeaturesUnlock
import org.meshtastic.core.model.DeviceType
import org.meshtastic.core.network.repository.NetworkRepository
import org.meshtastic.core.network.repository.UsbRepository
import org.meshtastic.core.network.transport.parseTcpRadioAddress
import org.meshtastic.core.repository.RadioLink
import org.meshtastic.core.repository.RadioLinks
import org.meshtastic.sdk.TransportIdentity
import org.meshtastic.sdk.TransportSpec
import org.meshtastic.sdk.transport.ble.BleResilience
import org.meshtastic.sdk.transport.ble.BleTransport
import org.meshtastic.sdk.transport.serial.AndroidUsbSerialPorts
import org.meshtastic.sdk.transport.tcp.TcpTransport
import kotlin.time.Duration.Companion.seconds

/** Android's radio links: Bluetooth, TCP, WebSocket, USB serial and the demo radios. */
@Single(binds = [RadioLinks::class])
@Suppress("LongParameterList")
class AndroidRadioLinks(
    private val application: Application,
    buildConfigProvider: BuildConfigProvider,
    private val usbRepository: UsbRepository,
    private val networkRepository: NetworkRepository,
    hiddenFeaturesUnlock: HiddenFeaturesUnlock,
    bluetoothRepository: BluetoothRepository,
    pipeline: RadioSessionPipeline,
) : BaseRadioLinks(bluetoothRepository, pipeline) {

    override val supportedDeviceTypes: List<DeviceType> = listOf(DeviceType.BLE, DeviceType.TCP, DeviceType.USB)

    /**
     * Demo Mode gate.
     *
     * Debug builds and Firebase Test Lab get it unconditionally. Release builds get it only after the hidden-features
     * gesture (five taps on the Settings app-version row), the process-scoped unlock that also reveals the
     * firmware-excluded module screens, so a Play reviewer with no LoRa hardware can still reach Demo Mode.
     */
    override val mockTransportEnabled: StateFlow<Boolean> =
        if (buildConfigProvider.isDebug || isFirebaseTestLab(application)) {
            MutableStateFlow(true)
        } else {
            hiddenFeaturesUnlock.unlocked
        }

    /** Probed once: the asset is baked into the APK. Empty counts as absent, as in [replayFrames]. */
    override val isReplayTransportAvailable: Boolean by lazy {
        runCatching { application.assets.open(REPLAY_ASSET_NAME).use { it.read() != -1 } }.getOrDefault(false)
    }

    override val isSerialSupported: Boolean = usbRepository.isSupported

    override fun openBle(address: String, scope: CoroutineScope): RadioLink {
        val transport =
            BleTransport(
                address = address.uppercase(),
                resilience =
                BleResilience(
                    bondFirst = true,
                    autoConnectFallback = true,
                    refreshStaleGattCache = true,
                    refreshGattCacheAfterFailures = GATT_REFRESH_AFTER_FAILURES,
                ),
            )
        return pipeline.link(
            identity = transport.identity,
            ble = true,
            scope = scope,
            provider = { _, _ ->
                awaitBluetooth()
                transport
            },
            gattCache = transport::refreshGattCacheOnNextConnect,
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
                // A retry waits for a network, which the network observer then hurries along.
                if (attempt > 0) networkRepository.networkAvailable.first { it }
                transport
            },
        )
    }

    /** A USB radio re-enumerates when it reboots, so every attempt opens the port afresh. */
    override fun openSerial(rest: String, scope: CoroutineScope): RadioLink = pipeline.link(
        identity = TransportIdentity.of(TransportSpec.SerialAndroid(rest)),
        ble = false,
        scope = scope,
        provider = { _, _ -> AndroidUsbSerialPorts.open(application, awaitUsbDevice(rest)) },
    )

    /**
     * The attached device [key] names once Android lets the app open it. Old installations saved a bus path, so a
     * missing stable key falls back to the one attached device only for those.
     */
    private suspend fun awaitUsbDevice(key: String): android.hardware.usb.UsbDevice {
        val device =
            usbRepository.serialDevices
                .first { devices -> devices[key] != null || (key.startsWith("/dev/") && devices.size == 1) }
                .let { devices -> (devices[key] ?: devices.values.single()).device }
        if (!usbRepository.hasPermission(device)) {
            Logger.i { "Asking for USB permission before connecting" }
            usbRepository.requestPermission(device).firstOrNull()
            while (!usbRepository.hasPermission(device)) delay(USB_PERMISSION_POLL)
        }
        return device
    }

    override fun replayFrames(): ByteArray? {
        val frames = runCatching {
            application.assets.open(REPLAY_ASSET_NAME).use { it.readBytes() }
        }
            .getOrNull()
            ?.takeIf { it.isNotEmpty() }
        if (frames == null) Logger.w { "Replay device selected but $REPLAY_ASSET_NAME is missing; opening the mock" }
        return frames
    }

    private companion object {
        const val REPLAY_ASSET_NAME = "burningmesh.fromradio"

        /** A stale cache after a long absence fails every reconnect until the user unpairs. */
        const val GATT_REFRESH_AFTER_FAILURES = 6

        val USB_PERMISSION_POLL = 1.seconds

        fun isFirebaseTestLab(context: Context): Boolean =
            Settings.System.getString(context.contentResolver, "firebase.test.lab") == "true"
    }
}
