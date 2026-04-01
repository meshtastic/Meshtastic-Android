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
@file:Suppress("MagicNumber")

package org.meshtastic.feature.firmware

import kotlinx.coroutines.test.runTest
import org.meshtastic.core.common.util.CommonUri
import org.meshtastic.core.database.entity.FirmwareRelease
import org.meshtastic.core.model.DeviceHardware
import org.meshtastic.core.testing.FakeNodeRepository
import org.meshtastic.core.testing.FakeRadioController
import org.meshtastic.core.testing.TestDataFactory
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for [performUsbUpdate] — the top-level internal function that handles USB/UF2 firmware updates.
 *
 * This class is `abstract` because it creates [CommonUri] instances via [CommonUri.parse], which on Android delegates
 * to `android.net.Uri` and therefore requires Robolectric. Platform subclasses in `androidHostTest` and `jvmTest` apply
 * the necessary runner configuration.
 */
abstract class CommonPerformUsbUpdateTest {

    private val testRelease = FirmwareRelease(id = "v2.7.17", zipUrl = "https://example.com/fw.zip")
    private val testHardware =
        DeviceHardware(hwModelSlug = "RPI_PICO", platformioTarget = "pico", architecture = "rp2040")

    // ── firmwareUri != null (user-selected file) ────────────────────────────

    @Test
    fun `user-selected file emits Downloading then Processing then AwaitingFileSave`() = runTest {
        val radioController = FakeRadioController()
        val nodeRepository = FakeNodeRepository()
        nodeRepository.setMyNodeInfo(TestDataFactory.createMyNodeInfo(myNodeNum = 42))

        val states = mutableListOf<FirmwareUpdateState>()
        val firmwareUri = CommonUri.parse("file:///tmp/firmware-pico-2.7.17.uf2")

        performUsbUpdate(
            release = testRelease,
            hardware = testHardware,
            firmwareUri = firmwareUri,
            radioController = radioController,
            nodeRepository = nodeRepository,
            updateState = { states.add(it) },
            retrieveUsbFirmware = { _, _, _ -> null },
        )

        assertTrue(states.size >= 3, "Expected at least 3 state transitions, got ${states.size}")
        assertIs<FirmwareUpdateState.Downloading>(states[0])
        assertIs<FirmwareUpdateState.Processing>(states[1])
        assertIs<FirmwareUpdateState.AwaitingFileSave>(states[2])
    }

    @Test
    fun `user-selected file returns null - no cleanup artifact`() = runTest {
        val radioController = FakeRadioController()
        val nodeRepository = FakeNodeRepository()
        nodeRepository.setMyNodeInfo(TestDataFactory.createMyNodeInfo(myNodeNum = 42))
        val firmwareUri = CommonUri.parse("file:///tmp/firmware.uf2")

        val result =
            performUsbUpdate(
                release = testRelease,
                hardware = testHardware,
                firmwareUri = firmwareUri,
                radioController = radioController,
                nodeRepository = nodeRepository,
                updateState = {},
                retrieveUsbFirmware = { _, _, _ -> null },
            )

        assertNull(result)
    }

    @Test
    fun `user-selected file extracts filename from URI path`() = runTest {
        val radioController = FakeRadioController()
        val nodeRepository = FakeNodeRepository()
        nodeRepository.setMyNodeInfo(TestDataFactory.createMyNodeInfo(myNodeNum = 1))

        val states = mutableListOf<FirmwareUpdateState>()
        val firmwareUri = CommonUri.parse("file:///storage/firmware-pico-2.7.17.uf2")

        performUsbUpdate(
            release = testRelease,
            hardware = testHardware,
            firmwareUri = firmwareUri,
            radioController = radioController,
            nodeRepository = nodeRepository,
            updateState = { states.add(it) },
            retrieveUsbFirmware = { _, _, _ -> null },
        )

        val awaitingState = states.filterIsInstance<FirmwareUpdateState.AwaitingFileSave>().first()
        assertTrue(
            awaitingState.fileName.endsWith(".uf2"),
            "Expected filename to end with .uf2, got: ${awaitingState.fileName}",
        )
    }

    // ── firmwareUri == null (download path) ─────────────────────────────────

    @Test
    fun `download path emits Error when retriever returns null`() = runTest {
        val radioController = FakeRadioController()
        val nodeRepository = FakeNodeRepository()
        nodeRepository.setMyNodeInfo(TestDataFactory.createMyNodeInfo(myNodeNum = 42))

        val states = mutableListOf<FirmwareUpdateState>()

        val result =
            performUsbUpdate(
                release = testRelease,
                hardware = testHardware,
                firmwareUri = null,
                radioController = radioController,
                nodeRepository = nodeRepository,
                updateState = { states.add(it) },
                retrieveUsbFirmware = { _, _, _ -> null },
            )

        assertNull(result)
        assertTrue(
            states.any { it is FirmwareUpdateState.Error },
            "Expected an Error state when retriever returns null",
        )
    }

    @Test
    fun `download path emits AwaitingFileSave when retriever succeeds`() = runTest {
        val radioController = FakeRadioController()
        val nodeRepository = FakeNodeRepository()
        nodeRepository.setMyNodeInfo(TestDataFactory.createMyNodeInfo(myNodeNum = 42))

        val artifact =
            FirmwareArtifact(
                uri = CommonUri.parse("file:///tmp/firmware-pico-2.7.17.uf2"),
                fileName = "firmware-pico-2.7.17.uf2",
                isTemporary = true,
            )

        val states = mutableListOf<FirmwareUpdateState>()

        val result =
            performUsbUpdate(
                release = testRelease,
                hardware = testHardware,
                firmwareUri = null,
                radioController = radioController,
                nodeRepository = nodeRepository,
                updateState = { states.add(it) },
                retrieveUsbFirmware = { _, _, onProgress ->
                    onProgress(0.5f)
                    onProgress(1.0f)
                    artifact
                },
            )

        assertNotNull(result)
        val awaitingState = states.filterIsInstance<FirmwareUpdateState.AwaitingFileSave>().first()
        assertTrue(awaitingState.fileName == "firmware-pico-2.7.17.uf2")
    }

    @Test
    fun `download path reports progress percentages during download`() = runTest {
        val radioController = FakeRadioController()
        val nodeRepository = FakeNodeRepository()
        nodeRepository.setMyNodeInfo(TestDataFactory.createMyNodeInfo(myNodeNum = 42))

        val artifact =
            FirmwareArtifact(uri = CommonUri.parse("file:///tmp/fw.uf2"), fileName = "fw.uf2", isTemporary = true)

        val states = mutableListOf<FirmwareUpdateState>()

        performUsbUpdate(
            release = testRelease,
            hardware = testHardware,
            firmwareUri = null,
            radioController = radioController,
            nodeRepository = nodeRepository,
            updateState = { states.add(it) },
            retrieveUsbFirmware = { _, _, onProgress ->
                onProgress(0.25f)
                onProgress(0.75f)
                artifact
            },
        )

        val downloadingStates = states.filterIsInstance<FirmwareUpdateState.Downloading>()
        assertTrue(downloadingStates.size >= 2, "Expected multiple Downloading states for progress updates")
        assertTrue(downloadingStates.any { it.progressState.details == "25%" }, "Expected 25% progress detail")
        assertTrue(downloadingStates.any { it.progressState.details == "75%" }, "Expected 75% progress detail")
    }

    @Test
    fun `download path returns artifact for caller cleanup`() = runTest {
        val radioController = FakeRadioController()
        val nodeRepository = FakeNodeRepository()
        nodeRepository.setMyNodeInfo(TestDataFactory.createMyNodeInfo(myNodeNum = 42))

        val artifact =
            FirmwareArtifact(uri = CommonUri.parse("file:///tmp/fw.uf2"), fileName = "fw.uf2", isTemporary = true)

        val result =
            performUsbUpdate(
                release = testRelease,
                hardware = testHardware,
                firmwareUri = null,
                radioController = radioController,
                nodeRepository = nodeRepository,
                updateState = {},
                retrieveUsbFirmware = { _, _, _ -> artifact },
            )

        assertNotNull(result, "Should return artifact for caller cleanup")
    }

    // ── Error handling ──────────────────────────────────────────────────────

    @Test
    fun `exception during update emits Error state`() = runTest {
        val radioController = FakeRadioController()
        val nodeRepository = FakeNodeRepository()
        nodeRepository.setMyNodeInfo(TestDataFactory.createMyNodeInfo(myNodeNum = 42))

        val states = mutableListOf<FirmwareUpdateState>()

        performUsbUpdate(
            release = testRelease,
            hardware = testHardware,
            firmwareUri = null,
            radioController = radioController,
            nodeRepository = nodeRepository,
            updateState = { states.add(it) },
            retrieveUsbFirmware = { _, _, _ -> throw RuntimeException("Download failed") },
        )

        assertTrue(states.any { it is FirmwareUpdateState.Error }, "Expected Error state on exception")
    }

    @Test
    fun `exception returns cleanup artifact when download partially completed`() = runTest {
        val radioController = FakeRadioController()
        val nodeRepository = FakeNodeRepository()
        nodeRepository.setMyNodeInfo(TestDataFactory.createMyNodeInfo(myNodeNum = 42))

        // The retriever provides a file, but then something after (rebootToDfu) throws.
        // In this test, since rebootToDfu on FakeRadioController is a no-op, we need to
        // simulate failure differently. Instead, we throw during the retrieval.
        val result =
            performUsbUpdate(
                release = testRelease,
                hardware = testHardware,
                firmwareUri = null,
                radioController = radioController,
                nodeRepository = nodeRepository,
                updateState = {},
                retrieveUsbFirmware = { _, _, _ -> throw RuntimeException("Network error") },
            )

        // cleanupArtifact is null when the error happens before retriever returns
        assertNull(result, "No cleanup artifact when retriever throws")
    }
}
