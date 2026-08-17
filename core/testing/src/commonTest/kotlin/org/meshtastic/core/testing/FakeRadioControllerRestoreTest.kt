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
package org.meshtastic.core.testing

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.meshtastic.proto.Channel
import org.meshtastic.proto.ChannelSettings
import org.meshtastic.proto.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FakeRadioControllerRestoreTest {
    @Test
    fun resetClearsRestoreSessionState() = runTest {
        val controller = FakeRadioController()
        controller.beforeRestoreLocalConfiguration = { error("stale hook") }
        controller.selectedDeviceAddress = "x:DEVICE"
        controller.setSessionGeneration(7L)

        controller.reset()

        assertNull(controller.selectedDeviceAddress)
        assertEquals(0L, controller.sessionGeneration.value)
        controller.selectedDeviceAddress = "x:DEVICE"
        assertTrue(
            controller.restoreLocalConfiguration(
                expectedDeviceAddress = "x:DEVICE",
                config = Config(),
                primaryChannel = null,
            ),
        )
    }

    @Test
    fun restoreRequiresSelectedDeviceOwnership() = runTest {
        val controller = FakeRadioController().apply { selectedDeviceAddress = "x:CURRENT" }
        val channel = Channel(index = 0, settings = ChannelSettings(name = "Primary"))
        val config = Config(lora = Config.LoRaConfig(use_preset = true))

        assertFalse(
            controller.restoreLocalConfiguration(
                expectedDeviceAddress = "x:STALE",
                config = config,
                primaryChannel = channel,
            ),
        )
        assertTrue(controller.localChannels.isEmpty())
        assertTrue(controller.localConfigs.isEmpty())

        assertTrue(
            controller.restoreLocalConfiguration(
                expectedDeviceAddress = "x:CURRENT",
                config = config,
                primaryChannel = channel,
            ),
        )
        assertEquals(listOf(channel), controller.localChannels)
        assertEquals(listOf(config), controller.localConfigs)
    }

    @Test
    fun restoreRejectsMissingSelectedDeviceOwnership() = runTest {
        val controller = FakeRadioController()
        val channel = Channel(index = 0, settings = ChannelSettings(name = "Primary"))
        val config = Config(lora = Config.LoRaConfig(use_preset = true))

        val restored =
            controller.restoreLocalConfiguration(
                expectedDeviceAddress = null,
                config = config,
                primaryChannel = channel,
            )

        assertFalse(restored)
        assertTrue(controller.localChannels.isEmpty())
        assertTrue(controller.localConfigs.isEmpty())
    }

    @Test
    fun restorePropagatesConfigFailureAfterChannelWrite() = runTest {
        val controller =
            FakeRadioController().apply {
                selectedDeviceAddress = "x:CURRENT"
                throwOnSetLocalConfig = true
            }
        val channel = Channel(index = 0, settings = ChannelSettings(name = "Primary"))
        val config = Config(lora = Config.LoRaConfig(use_preset = true))

        assertFailsWith<IllegalStateException> {
            controller.restoreLocalConfiguration(
                expectedDeviceAddress = "x:CURRENT",
                config = config,
                primaryChannel = channel,
            )
        }

        assertEquals(listOf(channel), controller.localChannels)
        assertTrue(controller.localConfigs.isEmpty())
    }

    @Test
    fun restoreSerializesWithDeviceSelection() = runTest {
        val controller = FakeRadioController()
        controller.setDeviceAddress("x:FIRST")
        val writeEntered = CompletableDeferred<Unit>()
        val releaseWrite = CompletableDeferred<Unit>()
        controller.beforeRestoreLocalConfiguration = {
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
        assertEquals("x:SECOND", controller.selectedDeviceAddress)
        assertEquals(listOf(config), controller.localConfigs)
    }
}
