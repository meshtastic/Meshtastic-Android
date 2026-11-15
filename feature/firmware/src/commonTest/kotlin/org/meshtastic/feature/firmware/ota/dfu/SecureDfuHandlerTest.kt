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
package org.meshtastic.feature.firmware.ota.dfu

import dev.mokkery.MockMode
import dev.mokkery.answering.throws
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.meshtastic.core.ble.BleConnectionFactory
import org.meshtastic.core.ble.BleScanner
import org.meshtastic.core.common.util.CommonUri
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.model.DeviceHardware
import org.meshtastic.core.model.FirmwareRelease
import org.meshtastic.core.testing.FakeRadioController
import org.meshtastic.feature.firmware.FirmwareFileHandler
import org.meshtastic.feature.firmware.FirmwareRetriever
import org.meshtastic.feature.firmware.FirmwareUpdateState
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** A package that cannot be unzipped is refused before the device leaves its application. */
class SecureDfuHandlerTest {

    private val release = FirmwareRelease(id = "v2.8.3", title = "test")
    private val hardware = DeviceHardware(hwModelSlug = "TBEAM", platformioTarget = "tbeam", architecture = "nrf52840")
    private val dispatchers =
        CoroutineDispatchers(
            io = Dispatchers.Unconfined,
            main = Dispatchers.Unconfined,
            default = Dispatchers.Unconfined,
        )

    private fun startWith(failure: Exception): List<FirmwareUpdateState> {
        val fileHandler: FirmwareFileHandler = mock(MockMode.autofill)
        everySuspend { fileHandler.extractZipEntries(any()) } throws failure
        val handler =
            SecureDfuHandler(
                firmwareRetriever = FirmwareRetriever(fileHandler),
                firmwareFileHandler = fileHandler,
                radioController = FakeRadioController(),
                bleScanner = mock<BleScanner>(MockMode.autofill),
                bleConnectionFactory = mock<BleConnectionFactory>(MockMode.autofill),
                dispatchers = dispatchers,
            )
        val states = mutableListOf<FirmwareUpdateState>()
        runBlocking {
            handler.startUpdate(
                release,
                hardware,
                "AA:BB:CC:DD:EE:FF",
                states::add,
                CommonUri.parse("file:///tmp/f.zip"),
            )
        }
        return states
    }

    @Test
    fun `a zip over the size limit wrote nothing`() {
        val error = startWith(IllegalArgumentException("entry too large")).last()
        assertTrue(assertIs<FirmwareUpdateState.Error>(error).nothingWritten)
    }

    @Test
    fun `a corrupt archive wrote nothing`() {
        val error = startWith(Exception("invalid entry")).last()
        assertTrue(assertIs<FirmwareUpdateState.Error>(error).nothingWritten)
    }
}
