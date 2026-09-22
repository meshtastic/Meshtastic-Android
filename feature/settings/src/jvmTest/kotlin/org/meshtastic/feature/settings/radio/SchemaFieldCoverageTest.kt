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
package org.meshtastic.feature.settings.radio

import com.squareup.wire.WireField
import org.meshtastic.proto.FieldMetadataRegistry
import java.io.File
import java.util.zip.ZipFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Every field the schema gives a label is either offered by a settings screen or named below with a reason. A field the
 * schema does not label cannot be rendered at all - there is no text for it - so it is never listed.
 *
 * What this pins is the schema side, which is where the change comes from: a newly annotated field upstream moves the
 * count and has to be given a control or a reason. It cannot prove a screen renders a field; android lays its screens
 * out by hand, so unlike Meshtastic-Apple's overlay there is no structure to read that from. The reasons below are a
 * review matter, the count is the ratchet.
 *
 * JVM-only: walking the generated messages needs `Class.forName` over the protobufs jar.
 */
class SchemaFieldCoverageTest {

    private enum class Reason {
        /** The field is one android has no control for, on a screen it otherwise renders. */
        NO_CONTROL,

        /** The whole module has no settings screen yet. */
        NO_SCREEN,
    }

    private val notOffered =
        mapOf(
            "Config.LoRaConfig.ignore_incoming" to Reason.NO_CONTROL,
            "ModuleConfig.TrafficManagementConfig.nodeinfo_direct_response_max_hops" to Reason.NO_SCREEN,
            "ModuleConfig.TrafficManagementConfig.position_min_interval_secs" to Reason.NO_SCREEN,
            "ModuleConfig.TrafficManagementConfig.rate_limit_max_packets" to Reason.NO_SCREEN,
            "ModuleConfig.TrafficManagementConfig.rate_limit_window_secs" to Reason.NO_SCREEN,
            "ModuleConfig.TrafficManagementConfig.unknown_packet_threshold" to Reason.NO_SCREEN,
        )

    /** Move this when the schema labels a field it did not before, and say in the same commit what the app does. */
    private val labelledFieldCount = 171

    @Test
    fun `every field named as not offered still exists and still carries a label`() {
        val labelled = labelledFields()
        val stale = notOffered.keys - labelled

        assertTrue(
            stale.isEmpty(),
            "these fields are no longer labelled by the schema, so they cannot still be listed as not offered: " +
                stale.sorted().joinToString(),
        )
    }

    @Test
    fun `the count of labelled fields is the one someone last looked at`() {
        val labelled = labelledFields()

        assertEquals(
            labelledFieldCount,
            labelled.size,
            "the schema labels ${labelled.size} config fields, not $labelledFieldCount. A field gained or lost a " +
                "label upstream: give the new one a control or a reason in notOffered, then move the count. Now: " +
                labelled.sorted().joinToString(),
        )
    }

    /** `Config.LoRaConfig.hop_limit` for every field of every config message the schema labels. */
    private fun labelledFields(): Set<String> = configMessagePaths()
        .flatMap { path ->
            Class.forName(GENERATED_PACKAGE + path.replace('.', '$')).declaredFields.mapNotNull { property ->
                val tag = property.getAnnotation(WireField::class.java)?.tag ?: return@mapNotNull null
                val label = FieldMetadataRegistry.get(PROTO_PACKAGE + path, tag)?.label
                "$path.${property.name}".takeIf { !label.isNullOrBlank() }
            }
        }
        .toSet()

    /** Proto paths of the message types under `Config` and `ModuleConfig`, read from the protobufs jar's class list. */
    private fun configMessagePaths(): List<String> {
        val jar = File(FieldMetadataRegistry::class.java.protectionDomain.codeSource.location.toURI())
        val prefix = GENERATED_PACKAGE.replace('.', '/')
        return ZipFile(jar).use { zip ->
            zip.entries()
                .asSequence()
                .map { it.name }
                .filter { it.startsWith(prefix) && it.endsWith(".class") && '/' !in it.removePrefix(prefix) }
                .map { it.removePrefix(prefix).removeSuffix(".class").replace('$', '.') }
                .filter { path ->
                    path.split('.').let { it.size > 1 && it.first() in CONTAINERS && it.all(::isTypeName) }
                }
                .filter { Class.forName(GENERATED_PACKAGE + it.replace('.', '$')).enumConstants == null }
                .sorted()
                .toList()
        }
    }

    private fun isTypeName(segment: String) = segment.isNotEmpty() && segment.first().isUpperCase()

    private companion object {
        const val GENERATED_PACKAGE = "org.meshtastic.proto."
        const val PROTO_PACKAGE = "meshtastic."
        val CONTAINERS = setOf("Config", "ModuleConfig")
    }
}
