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
package org.meshtastic.core.datastore.di

import kotlinx.browser.localStorage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okio.ByteString.Companion.encodeUtf8
import org.meshtastic.core.datastore.LocalConfigDataSource
import org.meshtastic.core.datastore.ModuleConfigDataSource
import org.meshtastic.proto.Config
import org.meshtastic.proto.LocalStats
import org.meshtastic.proto.ModuleConfig
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CoreDatastoreWasmJsModuleTest {
    private val module = CoreDatastoreWasmJsModule()

    private val security =
        Config.Builder()
            .also { wb ->
                wb.security =
                    Config.SecurityConfig.Builder()
                        .also { sb ->
                            sb.private_key = "private".encodeUtf8()
                            sb.admin_key = listOf("admin".encodeUtf8())
                        }
                        .build()
            }
            .build()
    private val mqtt =
        ModuleConfig.Builder()
            .also { wb ->
                wb.mqtt =
                    ModuleConfig.MQTTConfig.Builder()
                        .also { mb ->
                            mb.username = "user"
                            mb.password = "secret"
                        }
                        .build()
            }
            .build()

    @BeforeTest @AfterTest
    fun clearStorage() = localStorage.clear()

    @Test
    fun `radio config with keys is not written to browser storage`() = runTest {
        LocalConfigDataSource(module.provideLocalConfigDataStore()).setLocalConfig(security)

        assertNull(localStorage.getItem(LOCAL_CONFIG_STORAGE_KEY))
    }

    @Test
    fun `module config with an mqtt login is not written to browser storage`() = runTest {
        ModuleConfigDataSource(module.provideModuleConfigDataStore()).setLocalModuleConfig(mqtt)

        assertNull(localStorage.getItem(MODULE_CONFIG_STORAGE_KEY))
    }

    @Test
    fun `radio config written in a session reads back in that session`() = runTest {
        val source = LocalConfigDataSource(module.provideLocalConfigDataStore())
        source.setLocalConfig(security)

        assertEquals(security.security, source.localConfigFlow.first().security)
    }

    @Test
    fun `module config written in a session reads back in that session`() = runTest {
        val source = ModuleConfigDataSource(module.provideModuleConfigDataStore())
        source.setLocalModuleConfig(mqtt)

        assertEquals(mqtt.mqtt, source.moduleConfigFlow.first().mqtt)
    }

    @Test
    fun `a radio config stored by an earlier version is removed when the store is created`() = runTest {
        localStorage.setItem(LOCAL_CONFIG_STORAGE_KEY, "legacy")
        localStorage.setItem("unrelated", "kept")

        val source = LocalConfigDataSource(module.provideLocalConfigDataStore())

        assertNull(localStorage.getItem(LOCAL_CONFIG_STORAGE_KEY))
        assertEquals("kept", localStorage.getItem("unrelated"))
        assertEquals(null, source.localConfigFlow.first().security)
    }

    @Test
    fun `a module config stored by an earlier version is removed when the store is created`() = runTest {
        localStorage.setItem(MODULE_CONFIG_STORAGE_KEY, "legacy")
        localStorage.setItem("unrelated", "kept")

        module.provideModuleConfigDataStore()

        assertNull(localStorage.getItem(MODULE_CONFIG_STORAGE_KEY))
        assertEquals("kept", localStorage.getItem("unrelated"))
    }

    @Test
    fun `radio stats still persist in browser storage`() = runTest {
        module.provideLocalStatsDataStore().updateData {
            LocalStats.Builder().also { wb -> wb.uptime_seconds = 42 }.build()
        }

        assertEquals(42, module.provideLocalStatsDataStore().data.first().uptime_seconds)
    }
}
