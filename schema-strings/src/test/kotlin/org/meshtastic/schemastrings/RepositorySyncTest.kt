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
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The committed generated file is checked against the registry only while it records the pin the build resolves. A
 * merged protobufs bump leaves the two apart until the hourly scheduled-updates run re-syncs; that window is not an
 * error, a hand edit at a matching pin is.
 */
class RepositorySyncTest {

    private val sync = SchemaStringsSync(File(System.getProperty("schemaStrings.rootDir")))

    @Test
    fun `the generated file records the pin it was built from`() {
        assertNotNull(sync.recordedPin, "values/schema_strings.xml carries no pin: run ./gradlew :schema-strings:sync")
    }

    @Test
    fun `at a matching pin the generated file is what the registry implies`() {
        if (sync.recordedPin != sync.catalogPin) {
            println(
                "protobufs moved from ${sync.recordedPin} to ${sync.catalogPin}; scheduled-updates re-syncs the file",
            )
            return
        }
        assertEquals(
            sync.expectedEnglish(),
            sync.englishSchemaStrings.readText(),
            "core/resources values/schema_strings.xml is stale: run ./gradlew :schema-strings:sync",
        )
    }

    @Test
    fun `the generated Kotlin records the pin it was built from`() {
        assertNotNull(
            sync.recordedEnumLabelsPin,
            "core/model SchemaEnumLabels.kt carries no pin: run ./gradlew :schema-strings:sync",
        )
    }

    /**
     * Compares the accessors the file declares rather than its bytes: Spotless reformats what the sync writes, so its
     * text is the formatter's and only the set of enums and prefixes is the sync's to promise.
     */
    @Test
    fun `at a matching pin the generated Kotlin declares the accessors the registry implies`() {
        if (sync.recordedEnumLabelsPin != sync.catalogPin) {
            println("protobufs moved to ${sync.catalogPin}; scheduled-updates re-syncs SchemaEnumLabels.kt")
            return
        }
        val committed =
            EnumLabelsKt.branchPattern
                .findAll(sync.enumLabels.readText())
                .map { Triple(it.groupValues[1], it.groupValues[2], it.groupValues[3]) }
                .toSet()
        val expected =
            SchemaCatalog.labelledEnums()
                .flatMap { enum ->
                    listOfNotNull(
                        Triple(enum.path, "Label", enum.prefix),
                        Triple(enum.path, "Description", enum.prefix).takeIf { enum.hasDescriptions },
                    )
                }
                .toSet()

        assertEquals(expected, committed, "core/model SchemaEnumLabels.kt is stale: run ./gradlew :schema-strings:sync")
    }

    @Test
    fun `every labelled enum builds a prefix the generated strings carry`() {
        val strings = StringsXml.bodies(sync.englishSchemaStrings.readText()).keys
        val missing = SchemaCatalog.labelledEnums().filter { enum -> strings.none { it.startsWith(enum.prefix) } }

        assertTrue(
            missing.isEmpty(),
            "these enums build a resource prefix nothing in schema_strings.xml matches: " +
                missing.joinToString { "${it.path} (${it.prefix})" },
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
