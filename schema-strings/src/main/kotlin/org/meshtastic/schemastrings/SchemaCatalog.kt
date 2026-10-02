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

import com.squareup.wire.WireEnum
import com.squareup.wire.WireField
import org.meshtastic.proto.FieldMetadata
import org.meshtastic.proto.FieldMetadataRegistry
import java.io.File
import java.util.SortedMap
import java.util.TreeMap
import java.util.zip.ZipFile

/**
 * Every label and description the field-metadata registry carries, keyed by a name derived from the schema path, so the
 * code that shows a field names the resource and nothing else has to.
 *
 * `Config.LoRaConfig.hop_limit` becomes `schema_lora_hop_limit`; its description `schema_lora_hop_limit_description`;
 * the enum value `Config.PositionConfig.PositionFlags.DOP` becomes `schema_position_positionflags_dop`.
 */
object SchemaCatalog {
    private const val PROTO_PACKAGE = "meshtastic."
    private const val GENERATED_PACKAGE = "org.meshtastic.proto."
    private const val PREFIX = "schema_"
    private const val DESCRIPTION_SUFFIX = "_description"
    private val topLevelContainers = setOf("Config", "ModuleConfig")

    /** `resource -> English`, sorted by resource, for every annotated field and enum value on the classpath. */
    fun all(): SortedMap<String, String> {
        val out = TreeMap<String, String>()
        val origins = HashMap<String, String>()
        fun put(resource: String, text: String?, origin: String) {
            if (text.isNullOrBlank()) return
            val earlier = origins.put(resource, origin)
            check(earlier == null) { "$origin and $earlier both derive the resource '$resource'" }
            out[resource] = text
        }
        for (path in generatedTypePaths()) {
            val type = Class.forName(GENERATED_PACKAGE + path.replace('.', '$'))
            val constants = type.enumConstants
            if (constants != null) {
                constants.filterIsInstance<WireEnum>().forEach { constant ->
                    val meta = FieldMetadataRegistry.forEnumValue(PROTO_PACKAGE + path, constant.value)
                    val name = (constant as Enum<*>).name
                    put(keyFor(path, name), meta?.label, "$path.$name.label")
                    put(keyFor(path, name) + DESCRIPTION_SUFFIX, meta?.description, "$path.$name.description")
                }
            } else {
                type.declaredFields.forEach { property ->
                    val tag = property.getAnnotation(WireField::class.java)?.tag ?: return@forEach
                    val meta: FieldMetadata? = FieldMetadataRegistry.get(PROTO_PACKAGE + path, tag)
                    val key = keyFor(path, property.name)
                    val origin = "$path.${property.name}"
                    put(key, meta?.label, "$origin.label")
                    put(key + DESCRIPTION_SUFFIX, meta?.description, "$origin.description")
                }
            }
        }
        return out
    }

    /**
     * Every distinct unit symbol the schema declares on a field. The app names these in `FieldMetadataUnits.kt`, which
     * renders nothing for a symbol it does not know, so a new one has to be added there deliberately.
     */
    fun units(): Set<String> {
        val out = sortedSetOf<String>()
        for (path in generatedTypePaths()) {
            val type = Class.forName(GENERATED_PACKAGE + path.replace('.', '$'))
            if (type.enumConstants != null) continue
            type.declaredFields.forEach { property ->
                val tag = property.getAnnotation(WireField::class.java)?.tag ?: return@forEach
                val unit = FieldMetadataRegistry.get(PROTO_PACKAGE + path, tag)?.unit
                if (!unit.isNullOrBlank()) out += unit
            }
        }
        return out
    }

    /** Every enum the schema labels, in proto-path order, for the Kotlin accessors that read those labels. */
    fun labelledEnums(): List<EnumLabelsKt.LabelledEnum> = generatedTypePaths().mapNotNull { path ->
        val type = Class.forName(GENERATED_PACKAGE + path.replace('.', '$'))
        val constants = type.enumConstants ?: return@mapNotNull null
        val metadata =
            constants.filterIsInstance<WireEnum>().mapNotNull {
                FieldMetadataRegistry.forEnumValue(PROTO_PACKAGE + path, it.value)
            }
        if (metadata.none { !it.label.isNullOrBlank() }) return@mapNotNull null
        EnumLabelsKt.LabelledEnum(
            path = path,
            prefix = keyFor(path, ""),
            hasDescriptions = metadata.any { !it.description.isNullOrBlank() },
        )
    }

    /**
     * The resource for a field or enum value. Each message segment is lowercased with a trailing `Config` dropped, and
     * the `Config`/`ModuleConfig` container is dropped: `ModuleConfig.MQTTConfig.address` is `schema_mqtt_address`.
     */
    fun keyFor(path: String, member: String): String {
        val segments = path.split('.').filterIndexed { index, s -> !(index == 0 && s in topLevelContainers) }
        val message = segments.joinToString("_") { it.removeSuffix("Config").ifEmpty { it }.lowercase() }
        return PREFIX + message + "_" + member.lowercase()
    }

    /** Proto paths (`Config.PositionConfig`) of every generated type in the protobufs jar, from its class list. */
    private fun generatedTypePaths(): List<String> {
        val jar = File(FieldMetadataRegistry::class.java.protectionDomain.codeSource.location.toURI())
        val prefix = GENERATED_PACKAGE.replace('.', '/')
        return ZipFile(jar).use { zip ->
            zip.entries()
                .asSequence()
                .map { it.name }
                .filter { it.startsWith(prefix) && it.endsWith(".class") && '/' !in it.removePrefix(prefix) }
                .map { it.removePrefix(prefix).removeSuffix(".class") }
                .filter { name -> name.split('$').all { it.isNotEmpty() && it[0].isUpperCase() } }
                .map { it.replace('$', '.') }
                .sorted()
                .toList()
        }
    }
}
