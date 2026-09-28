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
package org.meshtastic.core.data.manager

import com.squareup.wire.ProtoAdapter
import com.squareup.wire.WireField
import org.meshtastic.proto.ModuleConfig
import kotlin.test.Test
import kotlin.test.assertEquals

/** Walks Wire's generated oneof, so a variant the proto grows fails here by name. JVM-only: reads `@WireField`. */
class ModuleConfigSummaryCoverageTest {

    @Test
    fun `every ModuleConfig variant is summarized by its field name`() {
        val variants =
            ModuleConfig::class.java.declaredFields.filter {
                it.getAnnotation(WireField::class.java)?.oneofName == "payload_variant"
            }

        val mislabelled =
            variants
                .associate { field ->
                    val value = (field.type.getField("ADAPTER").get(null) as ProtoAdapter<*>).decode(ByteArray(0))
                    val config =
                        ModuleConfig.Builder().also { it.javaClass.getField(field.name).set(it, value) }.build()
                    field.name to config.summarize()
                }
                .filter { (name, summary) -> name != summary }

        assertEquals(emptyMap(), mislabelled, "these variants are logged under the wrong name")
    }
}
