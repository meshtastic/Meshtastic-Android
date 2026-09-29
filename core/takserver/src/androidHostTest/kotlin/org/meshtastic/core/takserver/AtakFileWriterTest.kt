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
package org.meshtastic.core.takserver

import android.app.Application
import android.content.ContentProvider
import android.content.ContentUris
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.BaseColumns
import android.provider.MediaStore
import co.touchlab.kermit.Severity
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.meshtastic.core.common.ContextServices
import org.meshtastic.core.testing.CapturingLogWriter
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class AtakFileWriterTest {

    private val app: Application = RuntimeEnvironment.getApplication()
    private lateinit var logs: CapturingLogWriter

    @Before
    fun setUp() {
        ContextServices.app = app
        logs = CapturingLogWriter.install()
    }

    @After
    fun tearDown() {
        CapturingLogWriter.uninstall()
    }

    @Test
    @Config(sdk = [34])
    fun `saves to the shared Downloads folder through MediaStore`() {
        val mediaStore = Robolectric.setupContentProvider(FakeMediaStore::class.java, MediaStore.AUTHORITY)

        assertTrue(AtakFileWriter.writeToImportDir("route-1.zip", byteArrayOf(1, 2, 3)))

        val row = mediaStore.rows.values.single()
        assertEquals("route-1.zip", row.values.getAsString(MediaStore.MediaColumns.DISPLAY_NAME))
        assertEquals("Download/", row.values.getAsString(MediaStore.MediaColumns.RELATIVE_PATH))
        assertEquals(0, row.values.getAsInteger(MediaStore.MediaColumns.IS_PENDING))
        assertContentEquals(byteArrayOf(1, 2, 3), row.file.readBytes())
    }

    @Test
    @Config(sdk = [34])
    fun `saving the same route again replaces the earlier file`() {
        val mediaStore = Robolectric.setupContentProvider(FakeMediaStore::class.java, MediaStore.AUTHORITY)

        assertTrue(AtakFileWriter.writeToImportDir("route-1.zip", byteArrayOf(1, 2, 3)))
        assertTrue(AtakFileWriter.writeToImportDir("route-1.zip", byteArrayOf(9)))

        val row = mediaStore.rows.values.single()
        assertContentEquals(byteArrayOf(9), row.file.readBytes())
    }

    @Test
    @Config(sdk = [34])
    fun `a failed save removes its pending row`() {
        val mediaStore = Robolectric.setupContentProvider(FakeMediaStore::class.java, MediaStore.AUTHORITY)
        mediaStore.failUpdatesWith = IllegalStateException("provider refused the update")

        assertFalse(AtakFileWriter.writeToImportDir("route-1.zip", byteArrayOf(1, 2, 3)))

        assertTrue(mediaStore.rows.isEmpty(), "pending rows left behind: ${mediaStore.rows.keys}")
    }

    @Test
    @Config(sdk = [34])
    fun `a failed save is logged as an error without the file name`() {
        val mediaStore = Robolectric.setupContentProvider(FakeMediaStore::class.java, MediaStore.AUTHORITY)
        mediaStore.failUpdatesWith = IllegalStateException("provider refused the update")

        assertFalse(AtakFileWriter.writeToImportDir("route-1.zip", byteArrayOf(1, 2, 3)))

        assertEquals(1, logs.messages(Severity.Error).size, "error logs: ${logs.messages(Severity.Error)}")
        logs.assertNotLogged("route-1")
    }

    @Test
    @Config(sdk = [28])
    fun `a failed save below API 29 logs neither the file name nor its path`() {
        val dir = checkNotNull(app.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS))
        // A directory in the file's place makes the write fail with an error that names the full path.
        check(File(dir, "route-1.zip").mkdirs())

        assertFalse(AtakFileWriter.writeToImportDir("route-1.zip", byteArrayOf(5)))

        assertEquals(1, logs.messages(Severity.Error).size, "error logs: ${logs.messages(Severity.Error)}")
        logs.assertNotLogged("route-1", dir.absolutePath)
    }

    @Test
    @Config(sdk = [34])
    fun `a successful save logs no error`() {
        Robolectric.setupContentProvider(FakeMediaStore::class.java, MediaStore.AUTHORITY)

        assertTrue(AtakFileWriter.writeToImportDir("route-1.zip", byteArrayOf(1, 2, 3)))

        assertEquals(emptyList(), logs.messages(Severity.Error))
    }

    @Test
    @Config(sdk = [28])
    fun `saves to the app external Downloads folder below API 29`() {
        assertTrue(AtakFileWriter.writeToImportDir("route/../1.zip", byteArrayOf(5)))

        val dir = checkNotNull(app.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS))
        assertContentEquals(byteArrayOf(5), File(dir, "route_.._1.zip").readBytes())
    }

    /** Just enough of MediaStore for the writer: rows keyed by id, each backed by a real file. */
    class FakeMediaStore : ContentProvider() {
        class Row(val values: ContentValues, val file: File)

        val rows = linkedMapOf<Long, Row>()
        var failUpdatesWith: RuntimeException? = null
        private var nextId = 1L

        override fun onCreate(): Boolean = true

        override fun getType(uri: Uri): String? = null

        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            selection: String?,
            selectionArgs: Array<out String>?,
            sortOrder: String?,
        ): Cursor {
            val (name, relativePath) = checkNotNull(selectionArgs)
            val cursor = MatrixCursor(arrayOf(BaseColumns._ID))
            rows
                .filterValues {
                    it.values.getAsString(MediaStore.MediaColumns.DISPLAY_NAME) == name &&
                        it.values.getAsString(MediaStore.MediaColumns.RELATIVE_PATH) == relativePath
                }
                .keys
                .forEach { cursor.addRow(arrayOf(it)) }
            return cursor
        }

        override fun insert(uri: Uri, values: ContentValues?): Uri {
            val id = nextId++
            val file = File.createTempFile("media", ".bin", checkNotNull(context).cacheDir)
            rows[id] = Row(ContentValues(values), file)
            return ContentUris.withAppendedId(uri, id)
        }

        override fun update(
            uri: Uri,
            values: ContentValues?,
            selection: String?,
            selectionArgs: Array<out String>?,
        ): Int {
            failUpdatesWith?.let { throw it }
            val row = rows[ContentUris.parseId(uri)] ?: return 0
            row.values.putAll(values)
            return 1
        }

        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int =
            if (rows.remove(ContentUris.parseId(uri)) != null) 1 else 0

        override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor = ParcelFileDescriptor.open(
            rows.getValue(ContentUris.parseId(uri)).file,
            ParcelFileDescriptor.parseMode(mode),
        )
    }
}
