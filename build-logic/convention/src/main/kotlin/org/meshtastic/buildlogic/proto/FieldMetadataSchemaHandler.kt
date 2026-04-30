/*
 * Copyright (c) 2025-2026 Meshtastic LLC
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

package org.meshtastic.buildlogic.proto

import com.squareup.wire.schema.Extend
import com.squareup.wire.schema.Field
import com.squareup.wire.schema.MessageType
import com.squareup.wire.schema.Options.Companion.FIELD_OPTIONS
import com.squareup.wire.schema.ProtoFile
import com.squareup.wire.schema.ProtoMember
import com.squareup.wire.schema.Schema
import com.squareup.wire.schema.SchemaHandler
import com.squareup.wire.schema.Service
import com.squareup.wire.schema.Type
import okio.Path
import okio.Path.Companion.toPath
import okio.buffer

/**
 * Wire custom [SchemaHandler] that walks every message field annotated with
 * `(meshtastic.config_field)` and emits a Kotlin source file containing a
 * static metadata registry.
 *
 * This runs at build time during Wire codegen — the generated registry is
 * compiled into the app with zero runtime overhead and no `wire-schema` dep.
 *
 * ## Auto-discovery
 *
 * The handler automatically finds ALL fields across ALL messages that carry
 * `(meshtastic.config_field)`. Adding the annotation to new proto fields
 * requires **zero code changes here** — the registry regenerates on next build.
 *
 * ## When to modify this file
 *
 * Only if the `ConfigFieldMetadata` message in `field_metadata.proto` gains
 * new fields (e.g. `admin_only`, `min_value`, `max_value`). In that case,
 * update [parseConfigFieldOption] and [FieldMeta] to extract the new values.
 */
class FieldMetadataSchemaHandler : SchemaHandler() {

    private val configFieldMember: ProtoMember =
        ProtoMember.get(FIELD_OPTIONS, "meshtastic.config_field")

    /**
     * Override the top-level handle to walk all types and collect field metadata,
     * then emit a single generated file.
     */
    override fun handle(schema: Schema, context: Context) {
        val registry = mutableMapOf<String, MutableList<FieldEntry>>()

        for (protoFile in schema.protoFiles) {
            if (!context.inSourcePath(protoFile)) continue
            collectFieldMetadata(protoFile.types, registry)
        }

        if (registry.isNotEmpty()) {
            val output = generateRegistrySource(registry)
            val outputPath = context.outDirectory / "org/meshtastic/proto/ConfigFieldMetadataRegistry.kt"
            context.fileSystem.createDirectories(outputPath.parent!!)
            context.fileSystem.sink(outputPath).buffer().use { sink ->
                sink.writeUtf8(output)
            }
        }
    }

    private fun collectFieldMetadata(
        types: List<Type>,
        registry: MutableMap<String, MutableList<FieldEntry>>,
    ) {
        for (type in types) {
            if (type is MessageType) {
                for (field in type.fieldsAndOneOfFields) {
                    val optionValue = field.options.get(configFieldMember)
                    if (optionValue != null) {
                        val messageKey = type.type.toString()
                        val meta = parseConfigFieldOption(optionValue)
                        registry.getOrPut(messageKey) { mutableListOf() }
                            .add(FieldEntry(name = field.name, tag = field.tag, meta = meta))
                    }
                }
                // Recurse into nested types
                collectFieldMetadata(type.nestedTypes, registry)
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseConfigFieldOption(value: Any?): FieldMeta {
        // Wire schema options are returned as Map<ProtoMember, Any?>
        return when (value) {
            is Map<*, *> -> {
                val map = value as Map<ProtoMember, Any?>
                val diyOnly = map.entries.find { it.key.member == "diy_only" }?.value
                FieldMeta(diyOnly = diyOnly == "true" || diyOnly == true)
            }
            else -> FieldMeta(diyOnly = false)
        }
    }

    /**
     * Converts a proto type like "meshtastic.Config.PositionConfig" to the
     * Wire-generated Kotlin class name "org.meshtastic.proto.Config.PositionConfig".
     */
    private fun protoTypeToKotlinClass(protoType: String): String {
        // Wire maps package "meshtastic" → "org.meshtastic.proto"
        // and nested messages become nested Kotlin classes.
        val parts = protoType.split(".")
        return if (parts.firstOrNull() == "meshtastic") {
            "org.meshtastic.proto." + parts.drop(1).joinToString(".")
        } else {
            protoType
        }
    }

    private fun generateRegistrySource(registry: Map<String, List<FieldEntry>>): String {
        val sb = StringBuilder()
        sb.appendLine("// AUTO-GENERATED by FieldMetadataSchemaHandler — do not edit.")
        sb.appendLine("@file:Suppress(\"ktlint\")")
        sb.appendLine()
        sb.appendLine("package org.meshtastic.proto")
        sb.appendLine()
        sb.appendLine("/**")
        sb.appendLine(" * Build-time generated field metadata from `(meshtastic.config_field)` proto annotations.")
        sb.appendLine(" *")
        sb.appendLine(" * Usage — just reference the field by name:")
        sb.appendLine(" * ```")
        sb.appendLine(" * PositionConfigFields.rx_gpio.diyOnly  // true")
        sb.appendLine(" * PositionConfigFields.tx_gpio.diyOnly  // true")
        sb.appendLine(" * ```")
        sb.appendLine(" */")
        sb.appendLine()
        sb.appendLine("/** Metadata for a single proto field. */")
        sb.appendLine("data class ProtoFieldMeta(val name: String, val tag: Int, val diyOnly: Boolean)")
        sb.appendLine()

        // Generate one object per message type
        for ((protoType, fields) in registry.entries.sortedBy { it.key }) {
            val kotlinClass = protoTypeToKotlinClass(protoType)
            // "meshtastic.Config.PositionConfig" → "PositionConfigFields"
            val objectName = protoType.split(".").last() + "Fields"

            sb.appendLine("/** Field metadata for [${kotlinClass}]. */")
            sb.appendLine("object $objectName {")
            for (entry in fields.sortedBy { it.name }) {
                sb.appendLine("    val ${entry.name} = ProtoFieldMeta(name = \"${entry.name}\", tag = ${entry.tag}, diyOnly = ${entry.meta.diyOnly})")
            }
            sb.appendLine()
            sb.appendLine("    /** All fields carrying metadata on this message. */")
            sb.appendLine("    val all: kotlin.collections.List<ProtoFieldMeta> = listOf(${fields.sortedBy { it.name }.joinToString { it.name }})")
            sb.appendLine("}")
            sb.appendLine()
        }

        return sb.toString()
    }

    // Unused — we override the top-level handle() instead
    override fun handle(type: Type, context: Context): Path? = null
    override fun handle(service: Service, context: Context): List<Path> = emptyList()
    override fun handle(extend: Extend, field: Field, context: Context): Path? = null

    private data class FieldMeta(val diyOnly: Boolean)
    private data class FieldEntry(val name: String, val tag: Int, val meta: FieldMeta)
}

/** Factory for Wire's Gradle plugin to instantiate the handler. */
class FieldMetadataSchemaHandlerFactory : SchemaHandler.Factory {
    override fun create(
        includes: List<String>,
        excludes: List<String>,
        exclusive: Boolean,
        outDirectory: String,
        options: Map<String, String>,
    ): SchemaHandler = FieldMetadataSchemaHandler()
}







