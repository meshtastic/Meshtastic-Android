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
package org.meshtastic.core.domain.usecase.settings

import com.squareup.wire.Message
import com.squareup.wire.ProtoAdapter
import com.squareup.wire.WireField
import kotlinx.coroutines.test.runTest
import org.meshtastic.core.testing.FakeRadioConfigRepository
import org.meshtastic.core.testing.FakeRadioController
import org.meshtastic.proto.DeviceProfile
import org.meshtastic.proto.LocalModuleConfig
import org.meshtastic.proto.ModuleConfig
import java.lang.reflect.Field
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Walks Wire's generated fields, so a module config section the proto grows fails here by name until profile install
 * writes it. JVM-only because it reads `@WireField`.
 */
class InstallProfileModuleConfigCoverageTest {

    @Test
    fun `every LocalModuleConfig section has a ModuleConfig variant of the same name and type`() {
        val variantTypes = moduleConfigVariants().associate { it.name to it.type }

        val unmatched = localModuleConfigSections().filter { variantTypes[it.name] != it.type }.map { it.name }

        assertEquals(emptyList(), unmatched, "these LocalModuleConfig sections have no matching ModuleConfig variant")
    }

    @Test
    fun `installing a profile writes every module config section it carries`() = runTest {
        val radioController = FakeRadioController()
        val sections = localModuleConfigSections()
        val moduleConfig =
            LocalModuleConfig.Builder()
                .also { builder ->
                    sections.forEach { builder.javaClass.getField(it.name).set(builder, defaultOf(it)) }
                }
                .build()

        InstallProfileUseCase(radioController, FakeRadioConfigRepository())(
            destNum = 1234,
            profile = DeviceProfile.Builder().also { it.module_config = moduleConfig }.build(),
            currentUser = null,
            currentLoraConfig = null,
            isLocal = false,
        )

        val written = radioController.allModuleConfigs.associate { it.onlyVariant() }
        assertEquals(
            emptyList(),
            sections.map { it.name } - written.keys,
            "profile install never writes these module config sections",
        )
        assertEquals(radioController.allModuleConfigs.size, written.size, "a section was written more than once")
        sections.forEach { assertEquals(it.get(moduleConfig), written[it.name], "${it.name} was written changed") }
    }

    private fun ModuleConfig.onlyVariant(): Pair<String, Any> =
        moduleConfigVariants().mapNotNull { field -> field.get(this)?.let { field.name to it } }.single()

    private fun localModuleConfigSections(): List<Field> = LocalModuleConfig::class.java.declaredFields.filter {
        it.isAnnotationPresent(WireField::class.java) && Message::class.java.isAssignableFrom(it.type)
    }

    private fun moduleConfigVariants(): List<Field> = ModuleConfig::class.java.declaredFields.filter {
        it.getAnnotation(WireField::class.java)?.oneofName == "payload_variant"
    }

    private fun defaultOf(field: Field): Any? =
        (field.type.getField("ADAPTER").get(null) as ProtoAdapter<*>).decode(ByteArray(0))
}
