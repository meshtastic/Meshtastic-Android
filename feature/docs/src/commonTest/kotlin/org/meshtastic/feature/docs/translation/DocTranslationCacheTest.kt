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
package org.meshtastic.feature.docs.translation

import kotlinx.coroutines.test.runTest
import okio.FileSystem
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DocTranslationCacheTest {

    private val cacheDir = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "docs_cache_test_${kotlin.random.Random.nextInt()}"
    private val cache =
        DocTranslationCache(
            cacheDir = cacheDir,
            fileSystem = FileSystem.SYSTEM,
            maxCacheSizeBytes = 1024, // 1KB for testing eviction
        )

    @AfterTest
    fun tearDown() {
        try {
            FileSystem.SYSTEM.deleteRecursively(cacheDir)
        } catch (_: Exception) {
            // best effort cleanup
        }
    }

    @Test
    fun `get returns null for uncached page`() = runTest {
        val result = cache.get("onboarding", "es", "abc123")
        assertNull(result)
    }

    @Test
    fun `put then get returns cached content`() = runTest {
        cache.put("onboarding", "es", "hash1", "# Bienvenido")
        val result = cache.get("onboarding", "es", "hash1")
        assertEquals("# Bienvenido", result)
    }

    @Test
    fun `different hash returns null - stale cache`() = runTest {
        cache.put("onboarding", "es", "hash1", "# Bienvenido")
        val result = cache.get("onboarding", "es", "hash2")
        assertNull(result)
    }

    @Test
    fun `different locale returns null`() = runTest {
        cache.put("onboarding", "es", "hash1", "# Bienvenido")
        val result = cache.get("onboarding", "fr", "hash1")
        assertNull(result)
    }

    @Test
    fun `clear removes all cached entries`() = runTest {
        cache.put("page1", "es", "h1", "content1")
        cache.put("page2", "fr", "h2", "content2")
        cache.clear()
        assertNull(cache.get("page1", "es", "h1"))
        assertNull(cache.get("page2", "fr", "h2"))
    }

    @Test
    fun `sizeBytes reports total cache size`() = runTest {
        assertEquals(0L, cache.sizeBytes())
        cache.put("page1", "es", "h1", "Hello")
        assertTrue(cache.sizeBytes() > 0)
    }

    @Test
    fun `eviction removes oldest entries when over limit`() = runTest {
        // Fill cache beyond 1KB limit with multiple entries
        val largeContent = "x".repeat(300)
        cache.put("page1", "es", "h1", largeContent)
        cache.put("page2", "es", "h2", largeContent)
        cache.put("page3", "es", "h3", largeContent)
        cache.put("page4", "es", "h4", largeContent) // triggers eviction

        // page4 should still be accessible as the newest entry
        val page4 = cache.get("page4", "es", "h4")
        assertEquals(largeContent, page4)

        // Total size should be at or below max
        assertTrue(cache.sizeBytes() <= 1024)
    }

    @Test
    fun `md5Hash produces consistent hash`() {
        val hash1 = md5Hash("Hello, world!")
        val hash2 = md5Hash("Hello, world!")
        assertEquals(hash1, hash2)
    }

    @Test
    fun `md5Hash differs for different content`() {
        val hash1 = md5Hash("Hello")
        val hash2 = md5Hash("World")
        assertTrue(hash1 != hash2)
    }
}
