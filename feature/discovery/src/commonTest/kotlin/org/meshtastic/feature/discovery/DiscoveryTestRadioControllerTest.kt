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
package org.meshtastic.feature.discovery

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.meshtastic.proto.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DiscoveryTestRadioControllerTest {
    @Test
    fun restoreLocalConfigurationRejectsMissingExpectedDeviceOwnership() = runTest {
        val controller = DiscoveryTestRadioController()

        val restored =
            controller.restoreLocalConfiguration(
                expectedDeviceAddress = null,
                config = Config(lora = Config.LoRaConfig(use_preset = true)),
                primaryChannel = null,
            )

        assertFalse(restored)
        assertTrue(controller.configWrites.isEmpty())
        assertTrue(controller.channelWrites.isEmpty())
    }

    @Test
    fun requestNeighborInfoPropagatesConfiguredFailure() = runTest {
        val controller = DiscoveryTestRadioController()
        controller.requestNeighborInfoFailure = IllegalStateException("Neighbor info failed")

        assertFailsWith<IllegalStateException> { controller.requestNeighborInfo(requestId = 1, destNum = 2) }
    }

    @Test
    fun restoreLocalConfigurationSerializesDeviceSelection() = runTest {
        val controller = DiscoveryTestRadioController()
        controller.setDeviceAddress("x:FIRST")
        val writeEntered = CompletableDeferred<Unit>()
        val releaseWrite = CompletableDeferred<Unit>()
        controller.onLocalConfigWriteAttempt = {
            writeEntered.complete(Unit)
            releaseWrite.await()
        }
        val config = Config(lora = Config.LoRaConfig(use_preset = true))

        val restore = async {
            controller.restoreLocalConfiguration(
                expectedDeviceAddress = "x:FIRST",
                config = config,
                primaryChannel = null,
            )
        }
        writeEntered.await()
        val selection = async { controller.setDeviceAddress("x:SECOND") }
        runCurrent()

        assertFalse(selection.isCompleted, "device selection must wait for the in-flight restoration")
        releaseWrite.complete(Unit)
        assertTrue(restore.await())
        selection.await()
        assertEquals(listOf(config), controller.configWrites)
        assertEquals("x:SECOND", controller.selectedDeviceAddress)
    }
}
