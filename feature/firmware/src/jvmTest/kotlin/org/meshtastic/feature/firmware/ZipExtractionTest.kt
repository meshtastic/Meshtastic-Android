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
package org.meshtastic.feature.firmware

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ZipExtractionTest {

    /** Counts bytes actually pulled from the underlying source, so a test can assert reads stop early. */
    private class CountingStream(delegate: InputStream) : FilterInputStream(delegate) {
        var bytesRead = 0L
            private set

        override fun read(b: ByteArray, off: Int, len: Int): Int =
            super.read(b, off, len).also { if (it > 0) bytesRead += it }

        override fun read(): Int = super.read().also { if (it >= 0) bytesRead++ }
    }

    private fun zipOf(vararg entries: Pair<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            entries.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    // ---------- readAtMost: the ordering property ----------

    @Test
    fun `readAtMost stops reading once the limit is passed`() {
        // This is the property the previous implementation got wrong: it inflated an entry fully and only then compared
        // the size, so the allocation that exhausts the heap had already happened. Asserting on bytes actually pulled
        // from the source is what distinguishes "checked before" from "checked after".
        val tenMegabytes = ByteArray(10 * 1024 * 1024)
        val counting = CountingStream(ByteArrayInputStream(tenMegabytes))

        val result = readAtMost(counting, limit = 1024)

        assertNull(result, "a source over the limit must be rejected")
        assertTrue(
            counting.bytesRead <= 1024 + 8192,
            "must stop near the limit, but read ${counting.bytesRead} of ${tenMegabytes.size} bytes",
        )
    }

    @Test
    fun `readAtMost returns the content when it fits`() {
        val payload = "firmware".encodeToByteArray()

        val result = readAtMost(ByteArrayInputStream(payload), limit = 1024)

        assertEquals("firmware", result?.decodeToString())
    }

    @Test
    fun `readAtMost accepts a source exactly at the limit`() {
        val payload = ByteArray(64) { 1 }

        assertEquals(64, readAtMost(ByteArrayInputStream(payload), limit = 64)?.size)
        assertNull(readAtMost(ByteArrayInputStream(payload), limit = 63))
    }

    // ---------- extractZipEntriesBounded ----------

    @Test
    fun `a normal archive extracts every entry`() {
        val zip = zipOf("firmware.bin" to ByteArray(32) { 7 }, "manifest.json" to "{}".encodeToByteArray())

        val entries = extractZipEntriesBounded(ByteArrayInputStream(zip))

        assertEquals(setOf("firmware.bin", "manifest.json"), entries.keys)
        assertEquals(32, entries["firmware.bin"]?.size)
    }

    @Test
    fun `a compression bomb is refused without inflating it`() {
        // 8 MiB of zeros compresses to a few KB. With a small budget this must abort during the entry, not after.
        val bomb = zipOf("bomb.bin" to ByteArray(8 * 1024 * 1024))
        val counting = CountingStream(ByteArrayInputStream(bomb))

        assertFailsWith<IllegalArgumentException> { extractZipEntriesBounded(counting, maxTotalBytes = 4096) }
    }

    @Test
    fun `the uncompressed budget is enforced across entries, not per entry`() {
        // Three entries each under the budget but over it in total — a per-entry check would let this through.
        val zip = zipOf("a" to ByteArray(2048) { 1 }, "b" to ByteArray(2048) { 2 }, "c" to ByteArray(2048) { 3 })

        assertFailsWith<IllegalArgumentException> {
            extractZipEntriesBounded(ByteArrayInputStream(zip), maxTotalBytes = 5000)
        }
    }

    @Test
    fun `too many entries is refused`() {
        val many = Array(20) { "entry$it" to ByteArray(4) }
        val zip = zipOf(*many)

        assertFailsWith<IllegalArgumentException> {
            extractZipEntriesBounded(ByteArrayInputStream(zip), maxEntries = 10)
        }
    }

    /**
     * Builds an archive containing [count] zero-byte STORED entries that all share [name].
     *
     * `ZipOutputStream` refuses to write a duplicate name, so this emits the local file headers directly — which is how
     * a hostile archive would be produced anyway. `ZipInputStream` reads local headers sequentially and stops at the
     * end-of-central-directory signature, so it yields all [count] entries.
     */
    private fun zipWithDuplicateNames(name: String, count: Int): ByteArray {
        val out = ByteArrayOutputStream()
        fun le16(v: Int) {
            out.write(v and 0xFF)
            out.write((v ushr 8) and 0xFF)
        }
        fun le32(v: Int) {
            le16(v and 0xFFFF)
            le16((v ushr 16) and 0xFFFF)
        }
        val nameBytes = name.encodeToByteArray()
        repeat(count) {
            le32(0x04034B50) // local file header signature
            le16(20) // version needed
            le16(0) // flags
            le16(0) // method: stored
            le16(0) // mod time
            le16(0) // mod date
            le32(0) // crc32 of empty data
            le32(0) // compressed size
            le32(0) // uncompressed size
            le16(nameBytes.size)
            le16(0) // extra length
            out.write(nameBytes)
        }
        le32(0x06054B50) // end of central directory: stops the stream reader
        repeat(18) { out.write(0) }
        return out.toByteArray()
    }

    @Test
    fun `the compressed side is bounded even when the declared size is unknown`() {
        // getFileSize reports 0 for a provider that declines to answer, so the declared-size gate passes vacuously.
        // Seeded-random content so deflate cannot shrink it — a regular pattern here compresses to a few hundred bytes
        // and the bound would never be reached, making the test pass for the wrong reason.
        val incompressible = Random(seed = 1234).nextBytes(256 * 1024)
        val zip = zipOf("firmware.bin" to incompressible)

        assertFailsWith<IllegalArgumentException> {
            extractZipEntriesBounded(ByteArrayInputStream(zip), maxCompressedBytes = 4096)
        }
    }

    @Test
    fun `a normal archive is unaffected by the compressed bound`() {
        val zip = zipOf("firmware.bin" to ByteArray(64) { 3 })

        val entries = extractZipEntriesBounded(ByteArrayInputStream(zip), maxCompressedBytes = 1024 * 1024)

        assertEquals(setOf("firmware.bin"), entries.keys)
    }

    @Test
    fun `duplicate entry names cannot bypass the entry cap`() {
        // The cap counted map keys, and duplicates collapse to one key — so an archive of arbitrarily many same-named
        // entries walked straight past it while doing unbounded work.
        val zip = zipWithDuplicateNames("same.bin", count = 50)

        assertFailsWith<IllegalArgumentException> {
            extractZipEntriesBounded(ByteArrayInputStream(zip), maxEntries = 10)
        }
    }

    @Test
    fun `the duplicate-name fixture really does yield repeated entries`() {
        // Guards the fixture itself: if ZipInputStream collapsed or rejected these, the cap test above would pass for
        // the wrong reason.
        val zip = zipWithDuplicateNames("same.bin", count = 5)

        val entries = extractZipEntriesBounded(ByteArrayInputStream(zip), maxEntries = 100)

        assertEquals(setOf("same.bin"), entries.keys, "duplicates collapse to one key — that is the bug's premise")
    }

    // ---------- extractFirmwareEntry ----------

    private fun tempDir(): File = Files.createTempDirectory("zip-extraction-test").toFile().apply { deleteOnExit() }

    @Test
    fun `the target firmware is written to the output directory`() {
        val zip =
            zipOf(
                "esp32s3/firmware-heltec-v3-2.8.0.abc.bin" to ByteArray(32) { 7 },
                "esp32s3/firmware-tbeam-s3-core-2.8.0.abc.bin" to ByteArray(16) { 1 },
            )
        val out = tempDir()

        val file = extractFirmwareEntry(ByteArrayInputStream(zip), out, "heltec-v3", ".bin", preferredFilename = null)

        assertEquals(File(out, "firmware-heltec-v3-2.8.0.abc.bin"), file)
        assertContentEquals(ByteArray(32) { 7 }, file?.readBytes())
    }

    @Test
    fun `the shortest matching entry name wins`() {
        val zip =
            zipOf(
                "firmware-heltec-v3-2.8.0.abc-update.bin" to ByteArray(8) { 2 },
                "firmware-heltec-v3-2.8.0.abc.bin" to ByteArray(8) { 1 },
            )

        val file = extractFirmwareEntry(ByteArrayInputStream(zip), tempDir(), "heltec-v3", ".bin", null)

        assertEquals("firmware-heltec-v3-2.8.0.abc.bin", file?.name)
    }

    @Test
    fun `a preferred filename is matched by name alone ignoring directory and case`() {
        val zip = zipOf("nrf52840/Firmware-RAK4631-2.8.0.abc-ota.zip" to ByteArray(8) { 4 })

        val file =
            extractFirmwareEntry(
                ByteArrayInputStream(zip),
                tempDir(),
                target = "",
                fileExtension = ".zip",
                preferredFilename = "firmware-rak4631-2.8.0.abc-ota.zip",
            )

        assertEquals("firmware-rak4631-2.8.0.abc-ota.zip", file?.name)
    }

    @Test
    fun `no match returns null and writes nothing`() {
        val zip = zipOf("firmware-tbeam-2.8.0.abc.bin" to ByteArray(8))
        val out = tempDir()

        assertNull(extractFirmwareEntry(ByteArrayInputStream(zip), out, "heltec-v3", ".bin", null))
        assertEquals(0, out.listFiles()?.size)
    }

    @Test
    fun `an entry past the write budget is refused without reading the rest and leaves no file`() {
        val incompressible = Random(seed = 99).nextBytes(256 * 1024)
        val zip = zipOf("firmware-heltec-v3-2.8.0.abc.bin" to incompressible)
        val counting = CountingStream(ByteArrayInputStream(zip))
        val out = tempDir()

        assertFailsWith<IllegalArgumentException> {
            extractFirmwareEntry(counting, out, "heltec-v3", ".bin", null, maxWrittenBytes = 4096)
        }
        assertTrue(counting.bytesRead < 64 * 1024, "read ${counting.bytesRead} of ${zip.size} bytes")
        assertEquals(0, out.listFiles()?.size)
    }

    @Test
    fun `extraction refuses too many entries`() {
        val zip = zipOf(*Array(20) { "entry$it" to ByteArray(4) })

        assertFailsWith<IllegalArgumentException> {
            extractFirmwareEntry(ByteArrayInputStream(zip), tempDir(), "heltec-v3", ".bin", null, maxEntries = 10)
        }
    }

    @Test
    fun `a refused archive leaves none of the matches written before the refusal`() {
        val zip =
            zipOf("firmware-heltec-v3-2.8.0.abc.bin" to ByteArray(8) { 1 }, *Array(20) { "entry$it" to ByteArray(4) })
        val out = tempDir()

        assertFailsWith<IllegalArgumentException> {
            extractFirmwareEntry(ByteArrayInputStream(zip), out, "heltec-v3", ".bin", null, maxEntries = 10)
        }
        assertEquals(0, out.listFiles()?.size)
    }

    @Test
    fun `a truncated archive leaves no partial firmware file`() {
        val zip = zipOf("firmware-heltec-v3-2.8.0.abc.bin" to Random(seed = 7).nextBytes(64 * 1024))
        val out = tempDir()

        assertFailsWith<IOException> {
            extractFirmwareEntry(ByteArrayInputStream(zip.copyOf(zip.size / 2)), out, "heltec-v3", ".bin", null)
        }
        assertEquals(0, out.listFiles()?.size)
    }

    @Test
    fun `entry matching excludes directories and non-firmware images`() {
        assertTrue(isFirmwareEntryMatch("esp32/firmware-heltec-v3-2.8.0.bin", false, "heltec-v3", ".bin", null))
        assertFalse(isFirmwareEntryMatch("esp32/firmware-heltec-v3-2.8.0.bin/", true, "heltec-v3", ".bin", null))
        assertFalse(isFirmwareEntryMatch("littlefs-heltec-v3-2.8.0.bin", false, "heltec-v3", ".bin", null))
        assertFalse(isFirmwareEntryMatch("firmware-heltec-v3-2.8.0.factory.bin", false, "heltec-v3", ".bin", null))
        assertFalse(isFirmwareEntryMatch("x/firmware.uf2/", true, "", ".uf2", preferredFilename = "firmware.uf2"))
    }

    @Test
    fun `directory entries do not consume the entry budget`() {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            repeat(20) {
                zip.putNextEntry(ZipEntry("dir$it/"))
                zip.closeEntry()
            }
            zip.putNextEntry(ZipEntry("firmware.bin"))
            zip.write(ByteArray(8))
            zip.closeEntry()
        }

        val entries = extractZipEntriesBounded(ByteArrayInputStream(out.toByteArray()), maxEntries = 2)

        assertEquals(setOf("firmware.bin"), entries.keys)
    }
}
