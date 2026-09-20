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
package org.meshtastic.schemastrings

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The committed tree must match what `sync` would write, so the schema stays the only place this copy is written. */
class RepositorySyncTest {

    private val sync = SchemaStringsSync(File(System.getProperty("schemaStrings.rootDir")))

    @Test
    fun `schema_strings xml is what the registry implies`() {
        assertEquals(
            sync.expectedEnglish(),
            sync.englishSchemaStrings.readText(),
            "core/resources values/schema_strings.xml is stale: run ./gradlew :schema-strings:sync",
        )
    }

    @Test
    fun `no schema key is written by hand`() {
        val strays = sync.strays()

        assertTrue(
            strays.isEmpty(),
            strays.entries.joinToString(
                "\n",
                prefix = "schema_ keys are generated into schema_strings.xml only; remove them from:\n",
            ) { (file, keys) ->
                "${file.parentFile.name}/${file.name}: ${keys.sorted().joinToString()}"
            },
        )
    }
}
