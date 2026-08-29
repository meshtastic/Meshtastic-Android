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
package org.meshtastic.feature.map.kml

import org.meshtastic.feature.map.layers.MAX_KMZ_INFLATED_BYTES
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The KMZ side of the platform readers: extraction, and the inflate cap that keeps a zip bomb from an OOM.
 *
 * Lives in jvmTest because `readKmlDocument`/`readKmlArchiveImages` are `actual` functions — the androidMain twin is a
 * verbatim copy of the jvmMain body, so exercising one exercises the logic of both.
 */
class KmlDocumentTest {

    @Test
    fun `a kmz yields the kml inside it`() {
        val kmz = zip("doc.kml" to "<kml/>".encodeToByteArray(), "files/pin.png" to byteArrayOf(1, 2, 3))
        assertEquals("<kml/>", readKmlDocument(kmz))
    }

    @Test
    fun `bare kml passes straight through`() {
        assertEquals("<kml/>", readKmlDocument("<kml/>".encodeToByteArray()))
    }

    @Test
    fun `only the images the document names are extracted`() {
        val kmz =
            zip(
                "doc.kml" to "<kml/>".encodeToByteArray(),
                "files/pin.png" to byteArrayOf(1, 2, 3),
                "files/unreferenced.png" to byteArrayOf(9),
            )
        val images = readKmlArchiveImages(kmz, setOf("files/pin.png"))
        assertEquals(setOf("files/pin.png"), images.keys)
        assertEquals(listOf<Byte>(1, 2, 3), images.getValue("files/pin.png").toList())
    }

    @Test
    fun `a document past the inflate cap is refused rather than read`() {
        // Zeroes deflate at ~1000:1, so this "KML" is a small file on disk that inflates past the cap — the zip-bomb
        // shape. The header's size field is deliberately not what the cap trusts; the read itself is bounded.
        val kmz = zip("doc.kml" to ByteArray((MAX_KMZ_INFLATED_BYTES + 1).toInt()))
        assertNull(readKmlDocument(kmz))
    }

    @Test
    fun `images already read survive a later entry blowing the budget`() {
        val kmz =
            zip(
                "doc.kml" to "<kml/>".encodeToByteArray(),
                "a.png" to byteArrayOf(1),
                "bomb.png" to ByteArray(MAX_KMZ_INFLATED_BYTES.toInt()),
                "z.png" to byteArrayOf(2),
            )
        // The bomb exhausts the budget, so it and everything after it are dropped; what was read stays usable.
        assertEquals(setOf("a.png"), readKmlArchiveImages(kmz, setOf("a.png", "bomb.png", "z.png")).keys)
    }

    private fun zip(vararg entries: Pair<String, ByteArray>): ByteArray {
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
}
