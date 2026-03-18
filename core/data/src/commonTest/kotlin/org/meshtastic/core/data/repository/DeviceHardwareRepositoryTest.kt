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
package org.meshtastic.core.data.repository

class DeviceHardwareRepositoryTest {
    /*


    private val remoteDataSource: DeviceHardwareRemoteDataSource = mock()
    private val localDataSource: DeviceHardwareLocalDataSource = mock()
    private val jsonDataSource: DeviceHardwareJsonDataSource = mock()
    private val bootloaderOtaQuirksJsonDataSource: BootloaderOtaQuirksJsonDataSource = mock()
    private val testDispatcher = StandardTestDispatcher()
    private val dispatchers = CoroutineDispatchers(main = testDispatcher, io = testDispatcher, default = testDispatcher)

    private val repository =
        DeviceHardwareRepositoryImpl(
            remoteDataSource,
            localDataSource,
            jsonDataSource,
            bootloaderOtaQuirksJsonDataSource,
            dispatchers,
        )

    @Test
    fun `getDeviceHardwareByModel uses target for disambiguation`() = runTest(testDispatcher) {
        val hwModel = 50 // T_DECK
        val target = "tdeck-pro"
        val entities =
            listOf(createEntity(hwModel, "t-deck", "T-Deck"), createEntity(hwModel, "tdeck-pro", "T-Deck Pro"))

        everySuspend { localDataSource.getByHwModel(hwModel) } returns entities
        every { bootloaderOtaQuirksJsonDataSource.loadBootloaderOtaQuirksFromJsonAsset() } returns emptyList()

        val result = repository.getDeviceHardwareByModel(hwModel, target).getOrNull()

        assertEquals("T-Deck Pro", result?.displayName)
        assertEquals("tdeck-pro", result?.platformioTarget)
    }

    @Test
    fun `getDeviceHardwareByModel falls back to first entity when target not found`() = runTest(testDispatcher) {
        val hwModel = 50
        val target = "unknown-variant"
        val entities =
            listOf(createEntity(hwModel, "t-deck", "T-Deck"), createEntity(hwModel, "t-deck-tft", "T-Deck TFT"))

        everySuspend { localDataSource.getByHwModel(hwModel) } returns entities
        every { bootloaderOtaQuirksJsonDataSource.loadBootloaderOtaQuirksFromJsonAsset() } returns emptyList()

        val result = repository.getDeviceHardwareByModel(hwModel, target).getOrNull()

        // Should fall back to first entity if no exact match
        assertEquals("T-Deck", result?.displayName)
    }

    @Test
    fun `getDeviceHardwareByModel falls back to target lookup when hwModel not found`() = runTest(testDispatcher) {
        val hwModel = 0 // Unknown
        val target = "tdeck-pro"
        val entity = createEntity(102, "tdeck-pro", "T-Deck Pro")

        everySuspend { localDataSource.getByHwModel(hwModel) } returns emptyList()
        everySuspend { localDataSource.getByTarget(target) } returns entity
        every { bootloaderOtaQuirksJsonDataSource.loadBootloaderOtaQuirksFromJsonAsset() } returns emptyList()

        val result = repository.getDeviceHardwareByModel(hwModel, target).getOrNull()

        assertEquals("T-Deck Pro", result?.displayName)
        assertEquals("tdeck-pro", result?.platformioTarget)
    }

    @Test
    fun `getDeviceHardwareByModel correctly sets isEsp32Arc for ESP32 devices`() = runTest(testDispatcher) {
        val hwModel = 50
        val entities = listOf(createEntity(hwModel, "t-deck", "T-Deck").copy(architecture = "esp32-s3"))

        everySuspend { localDataSource.getByHwModel(hwModel) } returns entities
        every { bootloaderOtaQuirksJsonDataSource.loadBootloaderOtaQuirksFromJsonAsset() } returns emptyList()

        val result = repository.getDeviceHardwareByModel(hwModel).getOrNull()

        assertEquals(true, result?.isEsp32Arc)
    }

    private fun createEntity(hwModel: Int, target: String, displayName: String) = DeviceHardwareEntity(
        activelySupported = true,
        architecture = "esp32-s3",
        displayName = displayName,
        hwModel = hwModel,
        hwModelSlug = "T_DECK",
        images = listOf("image.svg"), // MUST be non-empty to avoid being considered incomplete/stale
        platformioTarget = target,
        requiresDfu = false,
        supportLevel = 0,
        tags = emptyList(),
        lastUpdated = nowMillis,
    )

     */
}
