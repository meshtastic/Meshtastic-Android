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
package org.meshtastic.core.ui.util

import kotlinx.coroutines.test.runTest
import okio.Buffer
import okio.BufferedSink
import okio.BufferedSource
import org.meshtastic.core.common.util.CommonUri
import org.meshtastic.core.repository.FileService
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WriteExportTest {

    private class RecordingFileService(private val writeSucceeds: Boolean = true) : FileService {
        val written = Buffer()
        var writes = 0
            private set

        override suspend fun write(uri: CommonUri, block: suspend (BufferedSink) -> Unit): Boolean {
            writes++
            if (!writeSucceeds) return false
            block(written)
            return true
        }

        override suspend fun read(uri: CommonUri, block: suspend (BufferedSource) -> Unit): Boolean = false
    }

    private val uri = CommonUri.parse("file:///tmp/export.txt")

    @Test
    fun `produced bytes are written and reported as exported`() = runTest {
        val fileService = RecordingFileService()

        val exported = writeExport(fileService, uri) { "log line".encodeToByteArray() }

        assertTrue(exported)
        assertEquals("log line", fileService.written.readUtf8())
    }

    @Test
    fun `nothing to export is reported as a failure without opening the file`() = runTest {
        val fileService = RecordingFileService()

        val exported = writeExport(fileService, uri) { null }

        assertFalse(exported)
        assertEquals(0, fileService.writes)
    }

    @Test
    fun `content that throws is reported as a failure without opening the file`() = runTest {
        val fileService = RecordingFileService()

        val exported = writeExport(fileService, uri) { error("log query failed") }

        assertFalse(exported)
        assertEquals(0, fileService.writes)
    }

    @Test
    fun `a failed write is reported as a failure`() = runTest {
        val fileService = RecordingFileService(writeSucceeds = false)

        val exported = writeExport(fileService, uri) { "log line".encodeToByteArray() }

        assertFalse(exported)
        assertEquals(1, fileService.writes)
    }
}
