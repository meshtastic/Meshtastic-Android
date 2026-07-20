/*
 * Copyright (c) 2026 Meshtastic LLC
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package org.meshtastic.feature.map.offline

import android.content.Context
import android.content.ContextWrapper
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.io.path.createTempDirectory
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.time.Instant

class BurningManPackRuntimeTest {

    @After
    fun resetRuntime() {
        BurningManPackRuntime.resetForTest()
    }

    @Test
    fun `application consumers share one coordinator and restore one validated selection`() = runTest {
        val context = ApplicationContext()
        val filesDirectory = createTempDirectory("burning-man-runtime").toFile()
        val store = CountingStore(installedRecord())
        writeValidatedPack(File(filesDirectory, "offline/burning-man-2026.pmtiles"))
        BurningManPackRuntime.installFactoryForTest { BurningManPackCoordinator(filesDirectory, store) }

        try {
            val firstConsumer = BurningManPackRuntime.forContext(context)
            val secondConsumer = BurningManPackRuntime.forContext(context.applicationContext)
            firstConsumer.restoreSelection()
            secondConsumer.restoreSelection()

            assertSame(firstConsumer.coordinator, secondConsumer.coordinator)
            assertEquals(firstConsumer.coordinator.selectedPack.value, secondConsumer.coordinator.selectedPack.value)
            assertEquals(1, store.loadCalls)
        } finally {
            filesDirectory.deleteRecursively()
        }
    }

    @Test
    fun `runtime keeps the factory captured during singleton creation`() {
        val context = ApplicationContext()
        val filesDirectory = createTempDirectory("burning-man-first").toFile()
        try {
            val firstCoordinator = BurningManPackCoordinator(filesDirectory, EmptyStore)
            BurningManPackRuntime.installFactoryForTest { firstCoordinator }
            val runtime = BurningManPackRuntime.forContext(context)

            BurningManPackRuntime.resetForTest()
            BurningManPackRuntime.installFactoryForTest { error("A reset must not alter an existing runtime") }

            assertSame(firstCoordinator, runtime.coordinator)
        } finally {
            filesDirectory.deleteRecursively()
        }
    }

    private fun installedRecord() =
        BurningManPackRecord(
            packId = "burning-man-2026",
            sourceBuild = "20260720",
            replicationTimestamp = "20260720",
            installedAt = Instant.parse("2026-08-25T00:00:00Z"),
            userSuppressed = false,
        )

    private fun writeValidatedPack(file: File) {
        file.parentFile?.mkdirs()
        val metadata = "{\"$REPLICATION_TIME_KEY\":\"20260720\"}".encodeToByteArray()
        val header = ByteBuffer.allocate(PmtilesV3Reader.HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN)
        header.put("PMTiles".encodeToByteArray())
        header.put(3)
        header.putLong(PmtilesV3Reader.HEADER_SIZE.toLong())
        header.putLong(0)
        header.putLong(PmtilesV3Reader.HEADER_SIZE.toLong())
        header.putLong(metadata.size.toLong())
        header.putLong((PmtilesV3Reader.HEADER_SIZE + metadata.size).toLong())
        header.putLong(0)
        header.putLong((PmtilesV3Reader.HEADER_SIZE + metadata.size).toLong())
        header.putLong(0)
        header.putLong(0)
        header.putLong(0)
        header.putLong(0)
        header.put(PmtilesCompression.None.value)
        header.put(PmtilesCompression.None.value)
        header.put(PmtilesCompression.None.value)
        header.put(PmtilesTileType.Mvt.value)
        file.writeBytes(header.array() + metadata)
    }

    private class CountingStore(private val record: BurningManPackRecord) : BurningManPackStore {
        var loadCalls = 0

        override fun load(): BurningManPackRecord? {
            loadCalls++
            return record
        }

        override fun save(record: BurningManPackRecord?) = Unit
    }

    private class ApplicationContext : ContextWrapper(null) {
        override fun getApplicationContext(): Context = this
    }

    private data object EmptyStore : BurningManPackStore {
        override fun load(): BurningManPackRecord? = null

        override fun save(record: BurningManPackRecord?) = Unit
    }
}
