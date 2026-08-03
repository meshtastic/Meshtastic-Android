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
package org.meshtastic.desktop.di

import androidx.lifecycle.SavedStateHandle
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import org.koin.dsl.onClose
import org.koin.test.verify.verify
import org.meshtastic.core.ble.BleLogFormat
import org.meshtastic.core.ble.BleLogLevel
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(KoinExperimentalAPI::class)
class DesktopKoinTest {

    @Test
    fun `verify desktop koin modules`() {
        // This test validates the full Koin DI graph for the Desktop target.
        // It includes the main desktopModule (repositories, use cases, ViewModels, stubs)
        // and the desktopPlatformModule (DataStores, Room database, lifecycle).
        module { includes(desktopModule(), desktopPlatformModule()) }
            .verify(
                extraTypes =
                listOf(
                    SavedStateHandle::class,
                    CoroutineDispatcher::class,
                    // MeshBeaconRepository is built by a factory function that supplies an
                    // ApplicationCoroutineScope. Koin Verify introspects the class constructor anyway, so its
                    // plain CoroutineScope parameter has to be declared even though nothing resolves it.
                    CoroutineScope::class,
                    HttpClient::class,
                    HttpClientEngine::class,
                    // BleLoggingConfig is a data class assembled by a factory function. Koin Verify
                    // still introspects its constructor params, so the wrapping enums need to be
                    // declared as known types even though they're never resolved from the graph.
                    BleLogLevel::class,
                    BleLogFormat::class,
                ),
            )
    }

    @Test
    fun `closing a koin container fires onClose for instantiated singles`() {
        // Pins the mechanism desktopModule() relies on: LinuxNotificationSender's native teardown
        // (notify_uninit) runs only because Koin invokes onClose when the container closes, which
        // Main.kt triggers via stopKoin() once the Compose application loop returns. A Koin upgrade
        // that changed this would silently reinstate the leak, so it is asserted rather than assumed.
        var closed = false
        val app = koinApplication {
            modules(module { single<AutoCloseable> { AutoCloseable { closed = true } }.onClose { it?.close() } })
        }
        app.koin.get<AutoCloseable>() // onClose only fires for singles that were actually instantiated
        app.close()

        assertTrue(closed, "Expected Koin to invoke onClose when the container is closed")
    }
}
