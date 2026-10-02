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
import java.time.Year

/** The repository view of the sync: where the files live, what the English file should contain, how to write it. */
class SchemaStringsSync(private val rootDir: File) {
    private val valuesRoot = rootDir.resolve("core/resources/src/commonMain/composeResources")
    private val catalog = rootDir.resolve("gradle/libs.versions.toml")
    val englishStrings: File = valuesRoot.resolve("values/$HAND_WRITTEN")
    val englishSchemaStrings: File = valuesRoot.resolve("values/$GENERATED")
    val enumLabels: File = rootDir.resolve("core/model/src/commonMain/kotlin/$GENERATED_KT")

    /** Every hand-written strings file: the English one and one per locale. */
    val handWrittenFiles: List<File>
        get() =
            listOf(englishStrings) +
                valuesRoot
                    .listFiles { f -> f.isDirectory && f.name.startsWith("values-") && f.resolve(HAND_WRITTEN).isFile }
                    .orEmpty()
                    .sortedBy { it.name }
                    .map { it.resolve(HAND_WRITTEN) }

    /** The `org.meshtastic:protobufs` version the build resolves, from the version catalog. */
    val catalogPin: String by lazy {
        catalogPinLine.find(catalog.readText())?.groupValues?.get(1) ?: error("no meshtastic-protobufs pin in $catalog")
    }

    /** The pin the committed generated file says it was built from, or null when the file is absent or unmarked. */
    val recordedPin: String?
        get() = englishSchemaStrings.takeIf { it.isFile }?.let { recordedPin(it.readText()) }

    /** The English file the registry implies. The header is borrowed from `strings.xml`, so the licence is one text. */
    fun expectedEnglish(): String = StringsXml.render(
        header = StringsXml.header(englishStrings.readText()),
        bodies = SchemaCatalog.all().mapValues { (_, text) -> StringsXml.escape(text) },
        notice = notice(catalogPin),
    )

    /**
     * The Kotlin accessors the registry implies. The licence header is the committed file's own where there is one, so
     * a rerun does not restamp a year Spotless is happy with - everything before `package` is that header.
     */
    fun expectedEnumLabels(): String = EnumLabelsKt.render(
        header = enumLabels.takeIf { it.isFile }?.let { licenceHeader(it.readText()) } ?: freshLicenceHeader(),
        pin = catalogPin,
        enums = SchemaCatalog.labelledEnums(),
    )

    /** The pin the committed Kotlin file says it was built from, or null when it is absent or unmarked. */
    val recordedEnumLabelsPin: String?
        get() = enumLabels.takeIf { it.isFile }?.let { noticePin.find(it.readText())?.groupValues?.get(1) }

    private fun licenceHeader(kotlin: String): String = kotlin.substringBefore("package ").substringBefore("// ")

    private fun freshLicenceHeader(): String =
        rootDir.resolve("config/spotless/copyright.kt").readText().replace("\$YEAR", YEAR) + "\n"

    /** Schema-named keys that someone wrote into a hand-written file, by file. Those files must never carry one. */
    fun strays(): Map<File, Set<String>> = handWrittenFiles
        .associateWith { file -> schemaKeys(StringsXml.bodies(file.readText()).keys) }
        .filterValues { it.isNotEmpty() }

    fun apply(): String {
        val text = expectedEnglish()
        englishSchemaStrings.writeText(text)
        enumLabels.writeText(expectedEnumLabels())
        return "wrote ${StringsXml.bodies(text).size} strings and " +
            "${SchemaCatalog.labelledEnums().size} enum accessors from protobufs $catalogPin"
    }

    private fun schemaKeys(names: Set<String>): Set<String> = names.filterTo(HashSet()) { it.startsWith("schema_") }

    companion object {
        const val HAND_WRITTEN = "strings.xml"
        const val GENERATED = "schema_strings.xml"
        const val GENERATED_KT = "org/meshtastic/core/model/SchemaEnumLabels.kt"
        private val YEAR = Year.now().toString()

        private val catalogPinLine = Regex("""^meshtastic-protobufs = "([^"]+)"$""", RegexOption.MULTILINE)
        private val noticePin = Regex("""from org\.meshtastic:protobufs (\S+)\.""")

        /** The first line inside `<resources>`: says where the file comes from and which pin it reflects. */
        fun notice(pin: String): String =
            "<!-- Generated by ./gradlew :schema-strings:sync from org.meshtastic:protobufs $pin. " +
                "Do not edit: change the schema, then run sync. -->"

        fun recordedPin(xml: String): String? = noticePin.find(xml)?.groupValues?.get(1)
    }
}
