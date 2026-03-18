/*
 * Copyright (c) 2025-2026 Meshtastic LLC
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

import kotlinx.coroutines.ExperimentalCoroutinesApi

@OptIn(ExperimentalCoroutinesApi::class)
class Esp32OtaUpdateHandlerTest {
    /*


    private val firmwareRetriever: FirmwareRetriever = mockk()
    private val radioController: RadioController = mockk()
    private val nodeRepository: NodeRepository = mockk()
    private val bleScanner: org.meshtastic.core.ble.BleScanner = mockk()
    private val bleConnectionFactory: org.meshtastic.core.ble.BleConnectionFactory = mockk()
    private val context: Context = mockk()
    private val contentResolver: ContentResolver = mockk()

    private val handler =
        Esp32OtaUpdateHandler(
            firmwareRetriever,
            radioController,
            nodeRepository,
            bleScanner,
            bleConnectionFactory,
            context,
        )

    @Before
    fun setUp() {
        mockkStatic("org.jetbrains.compose.resources.StringResourcesKt")
        coEvery { org.jetbrains.compose.resources.getString(any()) } returns "Mocked String"
        coEvery { org.jetbrains.compose.resources.getString(any(), *anyVararg()) } answers
            {
                val args = secondArg<Array<Any?>>()
                if (args.isNotEmpty()) {
                    "OTA update failed: ${args[0]}"
                } else {
                    "Mocked String with args"
                }
            }
    }

    @After
    fun tearDown() {
        unmockkStatic("org.jetbrains.compose.resources.StringResourcesKt")
    }

    @Test
    fun `startUpdate from URI propagates exception when reading fails`() = runTest {
        val release = FirmwareRelease(id = "local", title = "Local File", zipUrl = "", releaseNotes = "")
        val hardware = DeviceHardware(hwModelSlug = "V3", architecture = "esp32")
        val target = "00:11:22:33:44:55"
        val platformUri: Uri = mockk()
        val commonUri: CommonUri = mockk()

        mockkStatic("org.meshtastic.core.common.util.CommonUri_androidKt")
        every { commonUri.toPlatformUri() } returns platformUri

        every { context.contentResolver } returns contentResolver
        every { contentResolver.openInputStream(platformUri) } throws IOException("Read error")

        val states = mutableListOf<FirmwareUpdateState>()

        handler.startUpdate(release, hardware, target, { states.add(it) }, commonUri)

        val lastState = states.last()
        assert(lastState is FirmwareUpdateState.Error)
        assertEquals("OTA update failed: Read error", (lastState as FirmwareUpdateState.Error).error)

        unmockkStatic("org.meshtastic.core.common.util.CommonUri_androidKt")
    }

     */
}
