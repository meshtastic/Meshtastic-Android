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
package org.meshtastic.desktop.di

import io.ktor.client.plugins.cache.HttpCache
import io.ktor.client.plugins.pluginOrNull
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DesktopHttpCacheTest {

    private val dir: File = Files.createTempDirectory("http-cache-test").toFile()

    @AfterTest
    fun cleanUp() {
        dir.deleteRecursively()
    }

    private fun entry(name: String, bytes: Int, modifiedAt: Long) = File(dir, name).apply {
        writeBytes(ByteArray(bytes))
        setLastModified(modifiedAt)
    }

    @Test
    fun `the oldest entries go first once the cache is over budget`() {
        entry("oldest", bytes = 400, modifiedAt = 1_000_000L)
        entry("middle", bytes = 400, modifiedAt = 2_000_000L)
        entry("newest", bytes = 400, modifiedAt = 3_000_000L)

        trimDirectoryToBudget(dir, maxBytes = 1_000)

        assertEquals(setOf("middle", "newest"), dir.list().orEmpty().toSet())
    }

    @Test
    fun `a cache within budget is left alone`() {
        entry("a", bytes = 100, modifiedAt = 1_000_000L)
        entry("b", bytes = 100, modifiedAt = 2_000_000L)

        trimDirectoryToBudget(dir, maxBytes = 1_000)

        assertEquals(setOf("a", "b"), dir.list().orEmpty().toSet())
    }

    @Test
    fun `only the api client caches responses`() {
        val shared = DesktopRuntimeModule().httpClient(Json)
        val api = shared.withApiCache(File(dir, "http_cache"))
        try {
            assertNull(shared.pluginOrNull(HttpCache), "the shared client also carries firmware and images")
            assertNotNull(api.pluginOrNull(HttpCache))
        } finally {
            api.close()
            shared.close()
        }
    }

    @Test
    fun `a missing cache directory is created`() {
        val missing = File(dir, "nested/http_cache")

        trimDirectoryToBudget(missing, maxBytes = 1_000)

        assertTrue(missing.isDirectory)
    }
}
