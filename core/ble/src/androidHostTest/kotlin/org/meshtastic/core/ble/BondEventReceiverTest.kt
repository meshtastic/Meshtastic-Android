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
package org.meshtastic.core.ble

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.Intent
import android.os.Looper
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import co.touchlab.kermit.Severity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.testing.CapturingLogWriter
import org.meshtastic.core.testing.RobolectricBleBonding
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Robolectric's API 36 sandbox needs `jdk.internal.access` exported, which the host test JVM does not do, so the API 36
 * broadcasts run on API 35 with the receiver told it is on 36. Log lines therefore carry `sdk=35`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BondEventReceiverTest {

    private val radio = "EF:E2:0A:BE:95:6A"
    private val headphones = "11:22:33:44:55:66"

    private val app
        get() = RuntimeEnvironment.getApplication()

    private val scope = CoroutineScope(Job() + UnconfinedTestDispatcher())
    private lateinit var logs: CapturingLogWriter

    @Before
    fun setUp() {
        logs = CapturingLogWriter.install()
    }

    @After
    fun tearDown() {
        scope.cancel()
        CapturingLogWriter.uninstall()
    }

    @Test
    fun `registers once for all three bond broadcasts as an exported receiver on API 36`() {
        RobolectricBleBonding.grantBluetoothConnectPermission()
        val receiver = BondEventReceiver(app, scope, sdkInt = 36)

        receiver.watch(radio)
        receiver.watch(headphones)

        val registration = bondReceivers().single()
        assertEquals(
            setOf(
                BluetoothDevice.ACTION_BOND_STATE_CHANGED,
                BluetoothDevice.ACTION_KEY_MISSING,
                BluetoothDevice.ACTION_ENCRYPTION_CHANGE,
            ),
            registration.intentFilter.actionsIterator().asSequence().toSet(),
        )
        assertEquals(Context.RECEIVER_EXPORTED, registration.flags and Context.RECEIVER_EXPORTED)
    }

    @Test
    fun `registers only for bond state changes below API 36`() {
        RobolectricBleBonding.grantBluetoothConnectPermission()

        BondEventReceiver(app, scope).watch(radio)

        assertEquals(
            setOf(BluetoothDevice.ACTION_BOND_STATE_CHANGED),
            bondReceivers().single().intentFilter.actionsIterator().asSequence().toSet(),
        )
    }

    @Test
    fun `waits for BLUETOOTH_CONNECT before registering`() {
        shadowOf(app).denyPermissions(Manifest.permission.BLUETOOTH_CONNECT)
        val receiver = BondEventReceiver(app, scope)

        receiver.watch(radio)
        assertTrue(bondReceivers().isEmpty())

        RobolectricBleBonding.grantBluetoothConnectPermission()
        receiver.watch(radio)
        assertEquals(1, bondReceivers().size)
    }

    @Test
    @Config(sdk = [30])
    fun `registers without a runtime permission before API 31`() {
        BondEventReceiver(app, scope).watch(radio)

        assertEquals(1, bondReceivers().size)
    }

    @Test
    fun `unregisters when its scope is cancelled`() {
        RobolectricBleBonding.grantBluetoothConnectPermission()
        BondEventReceiver(app, scope).watch(radio)

        scope.cancel()

        assertTrue(bondReceivers().isEmpty())
    }

    @Test
    fun `logs key missing for a watched radio without its address`() {
        RobolectricBleBonding.grantBluetoothConnectPermission()
        BondEventReceiver(app, scope, sdkInt = 36).watch(radio)

        send(
            Intent(BluetoothDevice.ACTION_KEY_MISSING)
                .putExtra(BluetoothDevice.EXTRA_DEVICE, device(radio))
                .putExtra(BluetoothDevice.EXTRA_BOND_LOSS_REASON, BluetoothDevice.BOND_LOSS_REASON_LE_ENCRYPT_FAILURE),
        )

        assertEquals(
            listOf(
                "BLE bond event key-missing sdk=35 " +
                    "lossReason=${BluetoothDevice.BOND_LOSS_REASON_LE_ENCRYPT_FAILURE} device=...:6A",
            ),
            logs.messages(Severity.Warn),
        )
        logs.assertNotLogged(radio)
    }

    @Test
    fun `logs encryption changes and bond state transitions from their intent extras`() {
        RobolectricBleBonding.grantBluetoothConnectPermission()
        BondEventReceiver(app, scope, sdkInt = 36).watch(radio)

        send(
            Intent(BluetoothDevice.ACTION_ENCRYPTION_CHANGE)
                .putExtra(BluetoothDevice.EXTRA_DEVICE, device(radio))
                .putExtra(BluetoothDevice.EXTRA_ENCRYPTION_ENABLED, false)
                .putExtra(BluetoothDevice.EXTRA_ENCRYPTION_STATUS, 6),
        )
        RobolectricBleBonding.sendBondStateChanged(
            radio,
            newState = BluetoothDevice.BOND_NONE,
            previousState = BluetoothDevice.BOND_BONDED,
        )

        assertEquals(
            listOf(
                "BLE bond event encryption-change sdk=35 encrypted=false status=6 device=...:6A",
                "BLE bond event bond-state BONDED->NONE sdk=35 device=...:6A",
            ),
            logs.messages(Severity.Warn),
        )
    }

    @Test
    fun `ignores bond events for devices that are not a radio`() {
        RobolectricBleBonding.grantBluetoothConnectPermission()
        BondEventReceiver(app, scope, connectingAddress = { null }).watch(radio)

        RobolectricBleBonding.sendBondStateChanged(
            headphones,
            newState = BluetoothDevice.BOND_NONE,
            previousState = BluetoothDevice.BOND_BONDED,
        )

        assertTrue(logs.messages().none { it.startsWith("BLE bond event") }, logs.messages().toString())
    }

    @Test
    fun `logs the radio being connected to even when it was not watched`() {
        RobolectricBleBonding.grantBluetoothConnectPermission()
        BondEventReceiver(app, scope, connectingAddress = { headphones.lowercase() }).watch(radio)

        RobolectricBleBonding.sendBondStateChanged(
            headphones,
            newState = BluetoothDevice.BOND_BONDING,
            previousState = BluetoothDevice.BOND_NONE,
        )

        assertEquals(
            listOf("BLE bond event bond-state NONE->BONDING sdk=35 device=...:66"),
            logs.messages(Severity.Info),
        )
    }

    @Test
    fun `the repository logs bond events for a radio it is asked about`() {
        RobolectricBleBonding.grantBluetoothConnectPermission()
        val dispatcher = UnconfinedTestDispatcher()
        val repository =
            AndroidBluetoothRepository(
                app,
                CoroutineDispatchers(io = dispatcher, main = dispatcher, default = dispatcher),
                startedLifecycle(),
            )

        repository.isBonded(radio)
        RobolectricBleBonding.sendBondStateChanged(
            radio,
            newState = BluetoothDevice.BOND_NONE,
            previousState = BluetoothDevice.BOND_BONDED,
        )

        assertEquals(
            listOf("BLE bond event bond-state BONDED->NONE sdk=35 device=...:6A"),
            logs.messages(Severity.Warn).filter { it.startsWith("BLE bond event") },
        )
    }

    private fun startedLifecycle(): Lifecycle {
        val owner =
            object : LifecycleOwner {
                override val lifecycle: LifecycleRegistry =
                    LifecycleRegistry.createUnsafe(this).apply { currentState = Lifecycle.State.STARTED }
            }
        return owner.lifecycle
    }

    private fun bondReceivers(): List<ShadowApplication.Wrapper> = shadowOf(app).registeredReceivers.filter {
        it.intentFilter.hasAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
    }

    private fun device(mac: String): BluetoothDevice = RobolectricBleBonding.adapter.getRemoteDevice(mac)

    private fun send(intent: Intent) {
        app.sendBroadcast(intent)
        shadowOf(Looper.getMainLooper()).idle()
    }
}
