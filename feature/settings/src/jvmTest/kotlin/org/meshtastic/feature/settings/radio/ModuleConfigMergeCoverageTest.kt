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

import com.squareup.wire.ProtoAdapter
import com.squareup.wire.WireField
import org.meshtastic.proto.LocalModuleConfig
import org.meshtastic.proto.ModuleConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Walks Wire's generated oneof, so a variant the proto grows fails here by name. JVM-only: reads `@WireField`. */
class ModuleConfigMergeCoverageTest {

    @Test
    fun `merging every ModuleConfig variant in turn fills and keeps every LocalModuleConfig section`() {
        val variants =
            ModuleConfig::class.java.declaredFields.filter {
                it.getAnnotation(WireField::class.java)?.oneofName == "payload_variant"
            }
        assertTrue(variants.isNotEmpty(), "found no @WireField fields in the ModuleConfig payload_variant oneof")
        assertTrue(variants.any { it.name == "mesh_beacon" }, "mesh_beacon is not among the oneof fields found")

        val merged =
            variants.fold(LocalModuleConfig.Builder().build()) { acc, field ->
                val value = (field.type.getField("ADAPTER").get(null) as ProtoAdapter<*>).decode(ByteArray(0))
                acc.mergedWith(ModuleConfig.Builder().also { it.javaClass.getField(field.name).set(it, value) }.build())
            }

        val dropped = variants.map { it.name }.filter { LocalModuleConfig::class.java.getField(it).get(merged) == null }
        assertEquals(emptyList(), dropped, "these variants are never merged, or a later merge cleared them")
    }
}
