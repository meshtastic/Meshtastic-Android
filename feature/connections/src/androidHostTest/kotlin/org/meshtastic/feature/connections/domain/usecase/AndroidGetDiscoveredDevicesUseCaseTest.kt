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
package org.meshtastic.feature.connections.domain.usecase

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import app.cash.turbine.test
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.mock
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.meshtastic.core.datastore.model.RecentAddress
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.network.repository.DiscoveredService
import org.meshtastic.core.network.repository.UsbRepository
import org.meshtastic.core.repository.ConnectionIdentity
import org.meshtastic.core.repository.NodeManager
import org.meshtastic.core.testing.FakeBleDevice
import org.meshtastic.core.testing.FakeBluetoothRepository
import org.meshtastic.core.testing.FakeDatabaseManager
import org.meshtastic.core.testing.FakeNodeRepository
import org.meshtastic.core.testing.FakeRadioInterfaceService
import org.meshtastic.core.testing.TestDataFactory
import org.meshtastic.feature.connections.data.RecentAddressesSource
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.Test

/**
 * Drives [AndroidGetDiscoveredDevicesUseCase] through its real flow combination. Bonded radios come from
 * [FakeBluetoothRepository.state], the seam `AndroidBluetoothRepository` fills from `BluetoothAdapter.bondedDevices`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AndroidGetDiscoveredDevicesUseCaseTest {
    // Issue #8228: SD7T advertises SD7T_964e, the last two bytes of its MAC. On firmware 2.8 its node number is the
    // crc32 of its public key, while ptzm, another node on the mesh, has an id that happens to end in 964e.
    private val sd7tRadio = FakeBleDevice(address = "AA:BB:CC:DD:96:4E", name = "SD7T_964e")
    private val sd7tAddress = "x${sd7tRadio.address}"
    private val sd7t = TestDataFactory.createTestNode(num = 0x1A2B3C4D, userId = "!1a2b3c4d", longName = "SD7T")
    private val ptzm = TestDataFactory.createTestNode(num = 0x5E6F964E, userId = "!5e6f964e", longName = "ptzm")

    private val bluetoothRepository = FakeBluetoothRepository()
    private val nodeRepository = FakeNodeRepository().apply { setNodes(listOf(sd7t, ptzm)) }
    private val databaseManager = FakeDatabaseManager()
    private val identityFlow = MutableStateFlow<ConnectionIdentity?>(null)
    private val nodeManager: NodeManager = mock { every { connectionIdentity } returns identityFlow }
    private val recentAddresses =
        object : RecentAddressesSource {
            override val recentAddresses = MutableStateFlow(emptyList<RecentAddress>())

            override suspend fun add(address: RecentAddress) = Unit

            override suspend fun remove(address: String) = Unit
        }

    private val useCase =
        AndroidGetDiscoveredDevicesUseCase(
            bluetoothRepository = bluetoothRepository,
            recentAddressesDataSource = recentAddresses,
            nodeRepository = nodeRepository,
            databaseManager = databaseManager,
            nodeManager = nodeManager,
            usbRepository = noUsbDevices(),
            radioInterfaceService = FakeRadioInterfaceService(CoroutineScope(Job())),
            usbManagerLazy = lazy { error("no USB device is attached, so no permission is checked") },
        )

    private val resolved = MutableStateFlow(emptyList<DiscoveredService>())

    @Test
    fun `paired radio shows the node it reported at association never the node sharing its name suffix`() = runTest {
        bluetoothRepository.setBondedDevices(listOf(sd7tRadio))
        databaseManager.switchActiveDatabase(sd7tAddress)
        databaseManager.associateDevice(sd7tAddress, sd7t.num, deviceId = null) { true }

        useCase.invoke(showMock = false, showReplay = false, resolvedList = resolved).test {
            awaitItem().bleDevices.single().node shouldBe sd7t
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `paired radio with no recorded node number shows no node even when another node id ends in its suffix`() =
        runTest {
            bluetoothRepository.setBondedDevices(listOf(sd7tRadio))

            useCase.invoke(showMock = false, showReplay = false, resolvedList = resolved).test {
                awaitItem().bleDevices.single().node shouldBe null
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `paired radios are listed by name whatever order the platform returns them in`() = runTest {
        bluetoothRepository.setBondedDevices(
            listOf(
                FakeBleDevice(address = "AA:BB:CC:DD:00:01", name = "Zulu_0001"),
                sd7tRadio,
                FakeBleDevice(address = "AA:BB:CC:DD:00:02", name = "Alpha_0002"),
            ),
        )

        useCase.invoke(showMock = false, showReplay = false, resolvedList = resolved).test {
            awaitItem().bleDevices.map { it.name } shouldBe listOf("Alpha_0002", "SD7T_964e", "Zulu_0001")
            cancelAndIgnoreRemainingEvents()
        }
    }

    /**
     * A real [UsbRepository] (final and cross-module, so it cannot be mocked) on a DESTROYED lifecycle: its scan never
     * starts, so [UsbRepository.serialDevices] holds its initial empty map.
     */
    private fun noUsbDevices(): UsbRepository {
        val dispatcher = StandardTestDispatcher()
        return UsbRepository(
            application = RuntimeEnvironment.getApplication(),
            dispatchers = CoroutineDispatchers(io = dispatcher, main = dispatcher, default = dispatcher),
            processLifecycle = DestroyedLifecycle,
            usbBroadcastReceiverLazy = lazy { error("UsbBroadcastReceiver must not be used in this test") },
            usbManagerLazy = lazy { null },
            usbSerialProberLazy = lazy { error("UsbSerialProber must not be used in this test") },
        )
    }

    private object DestroyedLifecycle : Lifecycle() {
        override val currentState: State = State.DESTROYED

        override fun addObserver(observer: LifecycleObserver) = Unit

        override fun removeObserver(observer: LifecycleObserver) = Unit
    }
}
