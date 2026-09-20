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

/** The repository view of the sync: where the files live, what the English file should contain, how to write it. */
class SchemaStringsSync(rootDir: File) {
    private val valuesRoot = rootDir.resolve("core/resources/src/commonMain/composeResources")
    val englishStrings: File = valuesRoot.resolve("values/$HAND_WRITTEN")
    val englishSchemaStrings: File = valuesRoot.resolve("values/$GENERATED")

    /** Every hand-written strings file: the English one and one per locale. */
    val handWrittenFiles: List<File>
        get() =
            listOf(englishStrings) +
                valuesRoot
                    .listFiles { f -> f.isDirectory && f.name.startsWith("values-") && f.resolve(HAND_WRITTEN).isFile }
                    .orEmpty()
                    .sortedBy { it.name }
                    .map { it.resolve(HAND_WRITTEN) }

    /** The English file the registry implies. The header is borrowed from `strings.xml`, so the licence is one text. */
    fun expectedEnglish(): String = StringsXml.render(
        header = StringsXml.header(englishStrings.readText()),
        bodies = SchemaCatalog.all().mapValues { (_, text) -> StringsXml.escape(text) },
    )

    /** Schema-named keys that someone wrote into a hand-written file, by file. Those files must never carry one. */
    fun strays(): Map<File, Set<String>> = handWrittenFiles
        .associateWith { file -> schemaKeys(StringsXml.bodies(file.readText()).keys) }
        .filterValues { it.isNotEmpty() }

    private fun schemaKeys(names: Set<String>): Set<String> = names.filterTo(HashSet()) { it.startsWith("schema_") }

    fun apply(): String {
        val text = expectedEnglish()
        englishSchemaStrings.writeText(text)
        return "wrote ${englishSchemaStrings.path} (${StringsXml.bodies(text).size} strings)"
    }

    companion object {
        const val HAND_WRITTEN = "strings.xml"
        const val GENERATED = "schema_strings.xml"
    }
}
