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
package org.meshtastic.core.service

import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.meshtastic.core.datastore.FirmwareRecoveryDataSource
import org.meshtastic.core.datastore.model.PendingFirmwareRecovery
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.model.MyNodeInfo
import org.meshtastic.core.model.Node
import org.meshtastic.core.testing.FakeNodeRepository
import org.meshtastic.core.testing.FakeRadioPrefs
import org.meshtastic.core.testing.FakeServiceRepository
import org.meshtastic.proto.HardwareModel
import org.meshtastic.proto.User
import kotlin.test.Test

class LastBleNodeRecorderTest {
    private val serviceRepository = FakeServiceRepository()
    private val nodeRepository = FakeNodeRepository()
    private val radioPrefs = FakeRadioPrefs()
    private val dataSource: FirmwareRecoveryDataSource =
        mock(MockMode.autofill) { everySuspend { setLastBleNode(any()) } returns Unit }

    private fun recorder() = DefaultLastBleNodeRecorder(serviceRepository, nodeRepository, radioPrefs, dataSource)

    private fun handshake(address: String, pioEnv: String? = "rak4631") {
        radioPrefs.setDevAddr(address)
        // A name the scanner set for a different device must not leak into this node's record.
        radioPrefs.setDevName("Other")
        nodeRepository.setMyNodeInfo(myNodeInfo(pioEnv))
        nodeRepository.setOurNode(
            Node(
                num = 1,
                user =
                User.Builder()
                    .also {
                        it.hw_model = HardwareModel.RAK4631
                        it.long_name = "Base"
                    }
                    .build(),
            ),
        )
        serviceRepository.setConnectionState(ConnectionState.Connected)
    }

    @Test
    fun `a completed BLE handshake records the node`() = runTest(UnconfinedTestDispatcher()) {
        recorder().start(backgroundScope)

        handshake("xAA:BB:CC:00:00:01")

        verifySuspend {
            dataSource.setLastBleNode(
                PendingFirmwareRecovery(
                    fullAddress = "xAA:BB:CC:00:00:01",
                    hwModel = HardwareModel.RAK4631.value,
                    pioEnv = "rak4631",
                    releaseType = "STABLE",
                    deviceName = "Base",
                ),
            )
        }
    }

    @Test
    fun `a USB node is not recorded`() = runTest(UnconfinedTestDispatcher()) {
        recorder().start(backgroundScope)

        handshake("s/dev/ttyACM0")

        verifySuspend(mode = VerifyMode.exactly(0)) { dataSource.setLastBleNode(any()) }
    }

    @Test
    fun `a node with no firmware build is not recorded`() = runTest(UnconfinedTestDispatcher()) {
        recorder().start(backgroundScope)

        handshake("xAA:BB:CC:00:00:01", pioEnv = null)

        verifySuspend(mode = VerifyMode.exactly(0)) { dataSource.setLastBleNode(any()) }
    }

    private fun myNodeInfo(pioEnv: String?) = MyNodeInfo(
        myNodeNum = 1,
        hasGPS = false,
        model = null,
        firmwareVersion = null,
        couldUpdate = false,
        shouldUpdate = false,
        currentPacketId = 0,
        messageTimeoutMsec = 0,
        minAppVersion = 0,
        maxChannels = 8,
        hasWifi = false,
        channelUtilization = 0f,
        airUtilTx = 0f,
        deviceId = null,
        pioEnv = pioEnv,
    )
}
