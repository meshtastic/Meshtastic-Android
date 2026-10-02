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
package org.meshtastic.core.datastore

import com.squareup.wire.ProtoAdapter
import com.squareup.wire.WireField
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.meshtastic.core.datastore.di.CoreModuleConfigDataStore
import org.meshtastic.proto.LocalModuleConfig
import org.meshtastic.proto.ModuleConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Walks Wire's generated oneof, so a variant the proto grows fails here by name. JVM-only: reads `@WireField`. */
class ModuleConfigDataSourceCoverageTest {

    private class InMemoryModuleConfigStore : CoreModuleConfigDataStore {
        override val data = MutableStateFlow(LocalModuleConfig.Builder().build())

        override suspend fun updateData(transform: suspend (t: LocalModuleConfig) -> LocalModuleConfig) =
            transform(data.value).also { data.value = it }
    }

    @Test
    fun `every ModuleConfig variant is persisted into its LocalModuleConfig section`() = runTest {
        val store = InMemoryModuleConfigStore()
        val dataSource = ModuleConfigDataSource(store)
        val variants =
            ModuleConfig::class.java.declaredFields.filter {
                it.getAnnotation(WireField::class.java)?.oneofName == "payload_variant"
            }
        assertTrue(variants.isNotEmpty(), "found no @WireField fields in the ModuleConfig payload_variant oneof")
        assertTrue(variants.any { it.name == "mesh_beacon" }, "mesh_beacon is not among the oneof fields found")

        variants.forEach { field ->
            val value = (field.type.getField("ADAPTER").get(null) as ProtoAdapter<*>).decode(ByteArray(0))
            dataSource.setLocalModuleConfig(
                ModuleConfig.Builder().also { it.javaClass.getField(field.name).set(it, value) }.build(),
            )
        }

        val stored = store.data.first()
        val dropped = variants.map { it.name }.filter { LocalModuleConfig::class.java.getField(it).get(stored) == null }
        assertEquals(emptyList(), dropped, "these variants are never persisted, or a later one cleared them")
    }
}
