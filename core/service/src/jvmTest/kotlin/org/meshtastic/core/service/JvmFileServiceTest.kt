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
package org.meshtastic.core.service

import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.meshtastic.core.common.util.CommonUri
import org.meshtastic.core.di.CoroutineDispatchers
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JvmFileServiceTest {
    private lateinit var dir: File

    @BeforeTest
    fun setUp() {
        dir = Files.createTempDirectory("jvm-file-service-test").toFile()
    }

    @AfterTest
    fun tearDown() {
        dir.deleteRecursively()
    }

    @Test
    fun `a file URI from the save dialog is written at its own path`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val service = JvmFileService(CoroutineDispatchers(dispatcher, dispatcher, dispatcher))
        val target = File(dir, "export.txt")

        val written = service.write(CommonUri.parse(target.toURI().toString())) { it.writeUtf8("hello") }

        assertTrue(written)
        assertEquals("hello", target.readText())
    }

    @Test
    fun `a file URI is read from its own path`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val service = JvmFileService(CoroutineDispatchers(dispatcher, dispatcher, dispatcher))
        val source = File(dir, "profile.cfg").apply { writeText("config") }
        var text: String? = null

        val read = service.read(CommonUri.parse(source.toURI().toString())) { text = it.readUtf8() }

        assertTrue(read)
        assertEquals("config", text)
    }
}
