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

import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.mock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.meshtastic.core.common.di.asServiceScope
import org.meshtastic.core.repository.RadioConfigRepository
import org.meshtastic.proto.Config
import org.meshtastic.proto.LocalConfig
import org.meshtastic.proto.LocalModuleConfig
import org.meshtastic.proto.ModuleConfig
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class MeshConfigHandlerImplTest {

    private val radioConfigRepository = mock<RadioConfigRepository>(MockMode.autofill)

    private val localConfigFlow = MutableStateFlow(LocalConfig.Builder().build())
    private val moduleConfigFlow = MutableStateFlow(LocalModuleConfig.Builder().build())

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var handler: MeshConfigHandlerImpl

    @BeforeTest
    fun setUp() {
        every { radioConfigRepository.localConfigFlow } returns localConfigFlow
        every { radioConfigRepository.moduleConfigFlow } returns moduleConfigFlow
    }

    private fun createHandler(scope: CoroutineScope): MeshConfigHandlerImpl =
        MeshConfigHandlerImpl(radioConfigRepository = radioConfigRepository, scope = scope.asServiceScope())

    @Test
    fun `start wires localConfig flow from repository`() = runTest(testDispatcher) {
        handler = createHandler(backgroundScope)
        val config =
            LocalConfig.Builder()
                .also { wb ->
                    wb.device =
                        Config.DeviceConfig.Builder()
                            .also { wb -> wb.role = Config.DeviceConfig.Role.ROUTER }
                            .build()
                }
                .build()
        localConfigFlow.value = config
        advanceUntilIdle()

        assertEquals(config, handler.localConfig.value)
    }

    @Test
    fun `start wires moduleConfig flow from repository`() = runTest(testDispatcher) {
        handler = createHandler(backgroundScope)
        val config =
            LocalModuleConfig.Builder()
                .also { wb -> wb.mqtt = ModuleConfig.MQTTConfig.Builder().also { wb -> wb.enabled = true }.build() }
                .build()
        moduleConfigFlow.value = config
        advanceUntilIdle()

        assertEquals(config, handler.moduleConfig.value)
    }
}
