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
package org.meshtastic.feature.map.offline

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class BurningManPackCoordinatorTest {

    @Test
    fun `first reconciliation installs once to the app private pack path`() = runTest {
        val filesDirectory = createTempDirectory("burning-man-pack").toFile()
        val store = FakeStore()
        val downloader = FakeDownloader()
        val coordinator = BurningManPackCoordinator(filesDirectory, store, downloader)

        try {
            val selected = coordinator.reconcile(INSTALL_TIME, INSIDE_LOCATION)

            val destination = File(filesDirectory, "offline/burning-man-2026.pmtiles")
            assertEquals(destination, selected?.file)
            assertEquals(listOf(destination), downloader.destinations)
            assertEquals("burning-man-2026", store.record?.packId)
            assertEquals("20260902", store.record?.sourceBuild)
            assertEquals(REPLICATION_TIME, store.record?.replicationTimestamp)
        } finally {
            filesDirectory.deleteRecursively()
        }
    }

    @Test
    fun `an installed validated manifest returns the selected pack without downloading`() = runTest {
        val filesDirectory = createTempDirectory("burning-man-pack").toFile()
        val file = File(filesDirectory, "offline/burning-man-2026.pmtiles")
        writeValidatedPack(file)
        val store = FakeStore(record = installedRecord())
        val downloader = FakeDownloader()
        val coordinator = BurningManPackCoordinator(filesDirectory, store, downloader)

        try {
            assertEquals(file, coordinator.reconcile(INSTALL_TIME, INSIDE_LOCATION)?.file)
            assertTrue(downloader.destinations.isEmpty())
        } finally {
            filesDirectory.deleteRecursively()
        }
    }

    @Test
    fun `lower-detail pack is rejected and deleted on install`() = runTest {
        val filesDirectory = createTempDirectory("burning-man-pack").toFile()
        val store = FakeStore()
        val downloader = FakeDownloader(maxZoom = 14)
        val coordinator = BurningManPackCoordinator(filesDirectory, store, downloader)

        try {
            assertNull(coordinator.reconcile(INSTALL_TIME, INSIDE_LOCATION))
            assertFalse(File(filesDirectory, "offline/burning-man-2026.pmtiles").exists())
            assertNull(store.record)
        } finally {
            filesDirectory.deleteRecursively()
        }
    }

    @Test
    fun `lower-detail persisted pack is rejected and deleted on restore`() = runTest {
        val filesDirectory = createTempDirectory("burning-man-pack").toFile()
        val file = File(filesDirectory, "offline/burning-man-2026.pmtiles")
        writeValidatedPack(file, maxZoom = 14)
        val store = FakeStore(record = installedRecord())
        val coordinator = BurningManPackCoordinator(filesDirectory, store, FakeDownloader())

        try {
            assertNull(coordinator.restoreValidatedSelection())
            assertFalse(file.exists())
            assertNull(store.record)
        } finally {
            filesDirectory.deleteRecursively()
        }
    }

    @Test
    fun `user removal suppresses a later install`() = runTest {
        val filesDirectory = createTempDirectory("burning-man-pack").toFile()
        val store = FakeStore()
        val downloader = FakeDownloader()
        val coordinator = BurningManPackCoordinator(filesDirectory, store, downloader)

        try {
            coordinator.reconcile(INSTALL_TIME, INSIDE_LOCATION)
            coordinator.removeByUser()

            assertNull(coordinator.reconcile(INSTALL_TIME, INSIDE_LOCATION))
            assertEquals(1, downloader.destinations.size)
            assertTrue(store.record?.userSuppressed == true)
        } finally {
            filesDirectory.deleteRecursively()
        }
    }

    @Test
    fun `missing selected file clears selection and a later reconciliation retries`() = runTest {
        val filesDirectory = createTempDirectory("burning-man-pack").toFile()
        val store = FakeStore(record = installedRecord())
        val downloader = FakeDownloader(failFirstDownload = true)
        val coordinator = BurningManPackCoordinator(filesDirectory, store, downloader)

        try {
            assertNull(coordinator.reconcile(INSTALL_TIME, INSIDE_LOCATION))
            assertNull(store.record)

            assertTrue(coordinator.reconcile(INSTALL_TIME, INSIDE_LOCATION)?.file?.isFile == true)
            assertEquals(2, downloader.destinations.size)
        } finally {
            filesDirectory.deleteRecursively()
        }
    }

    @Test
    fun `automatic cleanup removes a pack without user suppression`() = runTest {
        val filesDirectory = createTempDirectory("burning-man-pack").toFile()
        val file = File(filesDirectory, "offline/burning-man-2026.pmtiles")
        writeValidatedPack(file)
        val store = FakeStore(record = installedRecord())
        val coordinator = BurningManPackCoordinator(filesDirectory, store, FakeDownloader())

        try {
            assertNull(coordinator.reconcile(AUTOMATIC_CLEANUP_TIME, null))
            assertFalse(file.exists())
            assertNull(store.record)
        } finally {
            filesDirectory.deleteRecursively()
        }
    }

    @Test
    fun `user removal during installation leaves the pack suppressed and absent`() = runTest {
        val filesDirectory = createTempDirectory("burning-man-pack").toFile()
        val store = FakeStore()
        val downloader = BlockingDownloader()
        val coordinator = BurningManPackCoordinator(filesDirectory, store, downloader)

        try {
            val installation = async { coordinator.reconcile(INSTALL_TIME, INSIDE_LOCATION) }
            downloader.started.await()
            val removal = async { coordinator.removeByUser() }
            runCurrent()
            downloader.release.complete(Unit)
            installation.await()
            removal.await()

            assertNull(coordinator.selectedPack.value)
            assertTrue(store.record?.userSuppressed == true)
            assertFalse(File(filesDirectory, "offline/burning-man-2026.pmtiles").exists())
        } finally {
            downloader.release.complete(Unit)
            filesDirectory.deleteRecursively()
        }
    }

    @Test
    fun `automatic cleanup queued during installation leaves no selected pack`() = runTest {
        val filesDirectory = createTempDirectory("burning-man-pack").toFile()
        val store = FakeStore()
        val downloader = BlockingDownloader()
        val coordinator = BurningManPackCoordinator(filesDirectory, store, downloader)

        try {
            val installation = async { coordinator.reconcile(INSTALL_TIME, INSIDE_LOCATION) }
            downloader.started.await()
            val cleanup = async { coordinator.reconcile(AUTOMATIC_CLEANUP_TIME, null) }
            runCurrent()
            downloader.release.complete(Unit)
            installation.await()
            cleanup.await()

            assertNull(coordinator.selectedPack.value)
            assertNull(store.record)
            assertFalse(File(filesDirectory, "offline/burning-man-2026.pmtiles").exists())
        } finally {
            downloader.release.complete(Unit)
            filesDirectory.deleteRecursively()
        }
    }

    @Test
    fun `concurrent reconciliations perform one installation`() = runTest {
        val filesDirectory = createTempDirectory("burning-man-pack").toFile()
        val downloader = BlockingDownloader()
        val coordinator = BurningManPackCoordinator(filesDirectory, FakeStore(), downloader)

        try {
            val first = async { coordinator.reconcile(INSTALL_TIME, INSIDE_LOCATION) }
            downloader.started.await()
            val second = async { coordinator.reconcile(INSTALL_TIME, INSIDE_LOCATION) }

            try {
                runCurrent()
                assertEquals(1, downloader.destinations.size)
            } finally {
                downloader.release.complete(Unit)
            }

            assertEquals(first.await()?.file, second.await()?.file)
            assertEquals(1, downloader.destinations.size)
        } finally {
            downloader.release.complete(Unit)
            filesDirectory.deleteRecursively()
        }
    }

    @Test
    fun `mismatched persisted pack identity clears the manifest and file`() = runTest {
        val filesDirectory = createTempDirectory("burning-man-pack").toFile()
        val file = File(filesDirectory, "offline/burning-man-2026.pmtiles")
        writeValidatedPack(file)
        val store = FakeStore(record = installedRecord().copy(packId = "other-pack"))
        val coordinator = BurningManPackCoordinator(filesDirectory, store, FakeDownloader())

        try {
            assertNull(coordinator.reconcile(INSTALL_TIME, null))
            assertNull(store.record)
            assertFalse(file.exists())
        } finally {
            filesDirectory.deleteRecursively()
        }
    }

    private class FakeStore(record: BurningManPackRecord? = null) : BurningManPackStore {
        var record: BurningManPackRecord? = record

        override fun load(): BurningManPackRecord? = record

        override fun save(record: BurningManPackRecord?) {
            this.record = record
        }
    }

    private class FakeDownloader(
        private var failFirstDownload: Boolean = false,
        private val maxZoom: Int = 15,
    ) : BurningManPackDownloader {
        val destinations = mutableListOf<File>()

        override suspend fun download(bounds: GeoBounds, destination: File): DownloadedPack {
            destinations += destination
            if (failFirstDownload) {
                failFirstDownload = false
                throw IllegalStateException("download failed")
            }
            writeValidatedPack(destination, maxZoom)
            return DownloadedPack("20260902", destination)
        }
    }

    private class BlockingDownloader : BurningManPackDownloader {
        val destinations = mutableListOf<File>()
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()

        override suspend fun download(bounds: GeoBounds, destination: File): DownloadedPack {
            destinations += destination
            started.complete(Unit)
            release.await()
            writeValidatedPack(destination)
            return DownloadedPack("20260902", destination)
        }
    }

    private fun installedRecord() =
        BurningManPackRecord(
            packId = "burning-man-2026",
            sourceBuild = "20260901",
            replicationTimestamp = REPLICATION_TIME,
            installedAt = INSTALL_TIME,
            userSuppressed = false,
        )

    private companion object {
        val INSTALL_TIME: Instant = Instant.parse("2026-09-07T20:00:00Z")
        val AUTOMATIC_CLEANUP_TIME: Instant = Instant.parse("2026-09-12T07:00:00Z")
        val INSIDE_LOCATION =
            PackLocation(
                latitude = 40.7864,
                longitude = -119.2065,
                timestamp = Instant.parse("2026-09-07T19:00:00Z"),
            )
        const val REPLICATION_TIME = "20260901"
    }
}

private fun writeValidatedPack(destination: File, maxZoom: Int = 15) {
    destination.parentFile?.mkdirs()
    val metadata = "{\"$REPLICATION_TIME_KEY\":\"20260901\"}".encodeToByteArray()
    val root = byteArrayOf(1, 0, 1, 1, 1)
    val header = ByteBuffer.allocate(PmtilesV3Reader.HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN)
    header.put("PMTiles".encodeToByteArray())
    header.put(3)
    header.putLong(PmtilesV3Reader.HEADER_SIZE.toLong())
    header.putLong(root.size.toLong())
    header.putLong((PmtilesV3Reader.HEADER_SIZE + root.size).toLong())
    header.putLong(metadata.size.toLong())
    header.putLong((PmtilesV3Reader.HEADER_SIZE + root.size + metadata.size).toLong())
    header.putLong(0)
    header.putLong((PmtilesV3Reader.HEADER_SIZE + root.size + metadata.size).toLong())
    header.putLong(1)
    header.putLong(1)
    header.putLong(1)
    header.putLong(1)
    header.put(0)
    header.put(PmtilesCompression.None.value)
    header.put(PmtilesCompression.None.value)
    header.put(PmtilesTileType.Mvt.value)
    header.put(0)
    header.put(maxZoom.toByte())
    destination.writeBytes(header.array() + root + metadata + byteArrayOf(0))
}
