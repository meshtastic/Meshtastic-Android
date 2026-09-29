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
package org.meshtastic.app

import android.content.ContentProvider
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import androidx.test.core.app.ApplicationProvider
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayInputStream
import java.io.File
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@RunWith(RobolectricTestRunner::class)
class SharedMapFileTest {

    @get:Rule val temp = TemporaryFolder()

    private fun shared(
        displayName: String,
        mimeType: String? = null,
        size: Long? = null,
        scheme: String? = ContentResolver.SCHEME_CONTENT,
    ) = SharedMapFile(scheme = scheme, displayName = displayName, mimeType = mimeType, size = size)

    @Test
    fun `kml kmz geojson and json files are accepted by their extension`() {
        listOf("route.kml", "route.kmz", "coverage.geojson", "area.json", "ROUTE.KML").forEach { name ->
            assertNull(shared(name, mimeType = "application/octet-stream").rejection(), name)
        }
    }

    @Test
    fun `a file with no extension is accepted by a map MIME type`() {
        listOf(
            "application/vnd.google-earth.kml+xml",
            "application/vnd.google-earth.kmz",
            "application/geo+json",
            "application/vnd.geo+json",
            "application/json",
        )
            .forEach { mime -> assertNull(shared("export", mimeType = mime).rejection(), mime) }
    }

    @Test
    fun `a file whose extension is not a map format is refused`() {
        listOf("photo.jpg", "track.gpx", "notes.txt", "route.kml.zip").forEach { name ->
            assertEquals(
                SharedMapFileRejection.UNSUPPORTED_TYPE,
                shared(name, mimeType = "application/json").rejection(),
                name,
            )
        }
    }

    @Test
    fun `a file with no extension and a non-map MIME type is refused`() {
        listOf("image/png", "text/plain", "application/octet-stream").forEach { mime ->
            assertEquals(SharedMapFileRejection.UNSUPPORTED_TYPE, shared("export", mimeType = mime).rejection(), mime)
        }
        assertEquals(SharedMapFileRejection.UNSUPPORTED_TYPE, shared("export", mimeType = null).rejection())
    }

    @Test
    fun `a coverage file from another app is refused`() {
        assertEquals(SharedMapFileRejection.UNSUPPORTED_TYPE, shared("estimate.coverage").rejection())
    }

    @Test
    fun `a file uri is refused whatever it names`() {
        listOf("route.kml", "coverage.geojson").forEach { name ->
            assertEquals(
                SharedMapFileRejection.NOT_CONTENT_URI,
                shared(name, scheme = ContentResolver.SCHEME_FILE, size = 10).rejection(),
                name,
            )
        }
        assertEquals(SharedMapFileRejection.NOT_CONTENT_URI, shared("route.kml", scheme = null).rejection())
    }

    @Test
    fun `a file reported over the cap is refused before it is read`() {
        assertEquals(SharedMapFileRejection.TOO_LARGE, shared("route.kml", size = 101).rejection(maxBytes = 100))
        assertNull(shared("route.kml", size = 100).rejection(maxBytes = 100))
        assertNull(shared("route.kml", size = 0).rejection(maxBytes = 100))
    }

    @Test
    fun `a file of unknown size is left to the capped read`() {
        assertNull(shared("route.kml", size = null).rejection(maxBytes = 100))
    }

    @Test
    fun `a read that runs past the cap returns nothing`() {
        assertNull(ByteArrayInputStream(ByteArray(DEFAULT_BUFFER_SIZE * 3 + 1)).readAtMost(DEFAULT_BUFFER_SIZE * 3L))
        assertNull(ByteArrayInputStream(ByteArray(101)).readAtMost(100))
    }

    @Test
    fun `a read at the cap returns every byte`() {
        val bytes = ByteArray(DEFAULT_BUFFER_SIZE * 2 + 7) { it.toByte() }
        assertContentEquals(bytes, ByteArrayInputStream(bytes).readAtMost(bytes.size.toLong()))
        assertContentEquals(ByteArray(0), ByteArrayInputStream(ByteArray(0)).readAtMost(100))
    }

    @Test
    fun `the provider's name size and type are what the check sees`() {
        val provider = registerProvider()
        provider.displayName = "Ridge Trail.kml"
        provider.size = 42L
        provider.mimeType = "application/vnd.google-earth.kml+xml"

        assertEquals(
            SharedMapFile(
                scheme = ContentResolver.SCHEME_CONTENT,
                displayName = "Ridge Trail.kml",
                mimeType = "application/vnd.google-earth.kml+xml",
                size = 42L,
            ),
            resolver.sharedMapFile(uri),
        )
    }

    @Test
    fun `a provider with no name or size falls back to the last path segment`() {
        val provider = registerProvider()
        provider.displayName = null
        provider.size = null
        provider.mimeType = "application/geo+json"

        val shared = assertNotNull(resolver.sharedMapFile(uri))

        assertEquals("shared-layer", shared.displayName)
        assertNull(shared.size)
        assertEquals("geo+json", shared.extensionOrMime)
    }

    @Test
    fun `a provider that throws on the metadata query is refused unopened`() {
        val provider = registerProvider()
        provider.mimeType = "application/geo+json"
        provider.refuse = true

        assertEquals(SharedMapFileLoad.Refused(SharedMapFileRejection.UNREADABLE), resolver.loadSharedMapFile(uri))
        assertEquals(0, provider.opens)
    }

    @Test
    fun `a file uri is not queried`() {
        val fileUri = Uri.fromFile(File("/data/data/com.geeksville.mesh/databases/meshtastic.db"))

        assertEquals(
            SharedMapFile(ContentResolver.SCHEME_FILE, "meshtastic.db", null, null),
            resolver.sharedMapFile(fileUri),
        )
    }

    @Test
    fun `a map file of unknown size is read whole up to the cap and refused past it`() {
        val provider = registerProvider()
        provider.displayName = "layer.geojson"
        val bytes = """{"type":"FeatureCollection","features":[]}""".encodeToByteArray()
        provider.file = temp.newFile("layer.geojson").apply { writeBytes(bytes) }

        val loaded = assertIs<SharedMapFileLoad.Loaded>(resolver.loadSharedMapFile(uri, maxBytes = bytes.size.toLong()))
        assertContentEquals(bytes, loaded.bytes)
        assertEquals("layer.geojson", loaded.file.displayName)
        assertEquals(
            SharedMapFileLoad.Refused(SharedMapFileRejection.TOO_LARGE),
            resolver.loadSharedMapFile(uri, maxBytes = bytes.size - 1L),
        )
    }

    @Test
    fun `a file the metadata refuses is never opened`() {
        val provider = registerProvider()
        provider.file = temp.newFile("photo.jpg")

        provider.displayName = "photo.jpg"
        assertEquals(
            SharedMapFileLoad.Refused(SharedMapFileRejection.UNSUPPORTED_TYPE),
            resolver.loadSharedMapFile(uri),
        )

        provider.displayName = "route.kml"
        provider.size = 101L
        assertEquals(
            SharedMapFileLoad.Refused(SharedMapFileRejection.TOO_LARGE),
            resolver.loadSharedMapFile(uri, maxBytes = 100),
        )

        assertEquals(0, provider.opens)
    }

    @Test
    fun `a provider that throws on open is refused instead of crashing`() {
        val provider = registerProvider()
        provider.displayName = "route.kml"
        listOf(SecurityException("no grant"), IllegalStateException("provider bug")).forEach { error ->
            provider.openError = error

            assertEquals(
                SharedMapFileLoad.Refused(SharedMapFileRejection.UNREADABLE),
                resolver.loadSharedMapFile(uri),
                error.toString(),
            )
        }
    }

    @Test
    fun `a file uri is refused without being opened`() {
        val fileUri = Uri.fromFile(temp.newFile("route.kml"))

        assertEquals(
            SharedMapFileLoad.Refused(SharedMapFileRejection.NOT_CONTENT_URI),
            resolver.loadSharedMapFile(fileUri),
        )
    }

    private val resolver: ContentResolver
        get() = ApplicationProvider.getApplicationContext<Context>().contentResolver

    private val uri = Uri.parse("content://$AUTHORITY/shared-layer")

    private fun registerProvider(): FakeMapFileProvider =
        Robolectric.buildContentProvider(FakeMapFileProvider::class.java).create(AUTHORITY).get()

    class FakeMapFileProvider : ContentProvider() {
        var displayName: String? = null
        var size: Long? = null
        var mimeType: String? = null
        var file: File? = null
        var refuse = false
        var openError: RuntimeException? = null
        var opens = 0

        override fun onCreate() = true

        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            selection: String?,
            selectionArgs: Array<out String>?,
            sortOrder: String?,
        ): Cursor {
            if (refuse) throw SecurityException("no grant")
            return MatrixCursor(arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)).apply {
                addRow(arrayOf<Any?>(displayName, size))
            }
        }

        override fun getType(uri: Uri): String? = mimeType

        override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
            opens++
            openError?.let { throw it }
            return ParcelFileDescriptor.open(checkNotNull(file), ParcelFileDescriptor.MODE_READ_ONLY)
        }

        override fun insert(uri: Uri, values: ContentValues?): Uri? = null

        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0

        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
    }

    private companion object {
        const val AUTHORITY = "org.meshtastic.test.sharedmapfile"
    }
}
