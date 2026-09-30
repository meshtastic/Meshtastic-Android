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
package org.meshtastic.feature.firmware.ota

import dev.mokkery.MockMode
import dev.mokkery.mock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.model.DeviceHardware
import org.meshtastic.core.model.FirmwareRelease
import org.meshtastic.core.repository.FirmwareUpdateStatusRepository
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.UiText
import org.meshtastic.core.resources.firmware_update_transfer_percent
import org.meshtastic.core.testing.FakeNodeRepository
import org.meshtastic.core.testing.FakeRadioController
import org.meshtastic.feature.firmware.FirmwareArtifact
import org.meshtastic.feature.firmware.FirmwareFileHandler
import org.meshtastic.feature.firmware.FirmwareRetriever
import org.meshtastic.feature.firmware.FirmwareUpdateState
import org.meshtastic.feature.firmware.ota.dfu.SecureDfuHandler
import kotlin.test.Test
import kotlin.test.assertEquals

/** The download step of the ESP32 and Nordic handlers, which resolves compose resources and so runs on the JVM. */
class DownloadProgressDetailTest {

    private val release = FirmwareRelease(id = "v2.7.17", title = "test")
    private val fileHandler: FirmwareFileHandler = mock(MockMode.autofill)
    private val dispatchers =
        CoroutineDispatchers(
            io = Dispatchers.Unconfined,
            main = Dispatchers.Unconfined,
            default = Dispatchers.Unconfined,
        )

    /** Reports a quarter of the download, then finds no file, so each handler stops right after its download step. */
    private val retriever =
        object : FirmwareRetriever(fileHandler) {
            override suspend fun retrieveEsp32Firmware(
                release: FirmwareRelease,
                hardware: DeviceHardware,
                onProgress: (Float) -> Unit,
            ): FirmwareArtifact? {
                onProgress(QUARTER)
                return null
            }

            override suspend fun retrieveOtaFirmware(
                release: FirmwareRelease,
                hardware: DeviceHardware,
                onProgress: (Float) -> Unit,
            ): FirmwareArtifact? {
                onProgress(QUARTER)
                return null
            }
        }

    @Test
    fun `ESP32 download progress is a translated percentage`() = runTest {
        val handler =
            Esp32OtaUpdateHandler(
                firmwareRetriever = retriever,
                firmwareFileHandler = fileHandler,
                radioController = FakeRadioController(),
                nodeRepository = FakeNodeRepository(),
                firmwareUpdateStatusRepository = FirmwareUpdateStatusRepository(),
                environment = DefaultEsp32OtaUpdateEnvironment(),
                bleScanner = mock(MockMode.autofill),
                bleConnectionFactory = mock(MockMode.autofill),
                dispatchers = dispatchers,
            )
        val states = mutableListOf<FirmwareUpdateState>()

        handler.startUpdate(release, ESP32, target = BLE_ADDRESS, updateState = states::add, firmwareUri = null)

        assertEquals(listOf(percentDetail(25)), states.downloadDetails())
    }

    @Test
    fun `Nordic DFU download progress is a translated percentage`() = runTest {
        val handler =
            SecureDfuHandler(
                firmwareRetriever = retriever,
                firmwareFileHandler = fileHandler,
                radioController = FakeRadioController(),
                bleScanner = mock(MockMode.autofill),
                bleConnectionFactory = mock(MockMode.autofill),
                dispatchers = dispatchers,
            )
        val states = mutableListOf<FirmwareUpdateState>()

        handler.startUpdate(release, NRF52, target = BLE_ADDRESS, updateState = states::add, firmwareUri = null)

        assertEquals(listOf(percentDetail(25)), states.downloadDetails())
    }

    private fun List<FirmwareUpdateState>.downloadDetails() =
        filterIsInstance<FirmwareUpdateState.Downloading>().mapNotNull { it.progressState.details }

    private fun percentDetail(percent: Int) = UiText.Resource(Res.string.firmware_update_transfer_percent, percent)

    private companion object {
        const val QUARTER = 0.25f
        const val BLE_ADDRESS = "AA:BB:CC:DD:EE:FF"
        val ESP32 = DeviceHardware(hwModelSlug = "HELTEC_V3", platformioTarget = "heltec-v3", architecture = "esp32-s3")
        val NRF52 = DeviceHardware(hwModelSlug = "RAK4631", platformioTarget = "rak4631", architecture = "nrf52840")
    }
}
