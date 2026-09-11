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
import org.koin.plugin.module.dsl.koinApplication
import org.koin.test.verify.verify
import org.meshtastic.core.ble.BleLogFormat
import org.meshtastic.core.ble.BleLogLevel
import org.meshtastic.core.network.repository.MQTTRepository
import org.meshtastic.desktop.stub.NoopMQTTRepository
import org.meshtastic.feature.docs.translation.DocTranslationService
import org.meshtastic.feature.docs.translation.NoOpDocTranslator
import org.meshtastic.feature.messaging.translation.MessageTranslationService
import org.meshtastic.feature.messaging.translation.NoOpMessageTranslator
import kotlin.test.Test
import kotlin.test.assertIs

class DesktopKoinTest {

    @OptIn(KoinExperimentalAPI::class)
    @Test
    fun `verify desktop koin modules`() {
        // Validates the full Koin DI graph for the Desktop target: the core KMP modules (repositories, use cases,
        // ViewModels) plus the desktop-specific platform, datastore, runtime, stub and AI modules.
        DesktopKoinModule()
            .module()
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
    fun `desktop bindings win over the shared graph`() {
        // @Configuration modules load before the ones listed in @KoinApplication, and Koin is last-wins, so which
        // binding survives is ordering-dependent. MQTTRepository is the live case: core:network commonMain declares
        // MQTTRepositoryImpl, and desktop must shadow it. verify() only checks definitions exist, never who won.
        val app = koinApplication<DesktopKoinApp>()
        try {
            val koin = app.koin
            assertIs<NoopMQTTRepository>(koin.get<MQTTRepository>())
            assertIs<NoOpMessageTranslator>(koin.get<MessageTranslationService>())
            assertIs<NoOpDocTranslator>(koin.get<DocTranslationService>())
        } finally {
            app.close()
        }
    }

    @Test
    fun `typed bootstrap loads the module graph`() {
        // koinApplication<T>() is a K2 compiler plugin stub. If the plugin fails to transform it, the stub throws
        // NotImplementedError at runtime. This is the production bootstrap path Main.kt takes via startKoin<T>.
        val app = koinApplication<DesktopKoinApp>()
        app.close()
    }
}
