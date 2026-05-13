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
package org.meshtastic.app.di

import android.app.Application
import android.content.Context
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.SavedStateHandle
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import kotlinx.coroutines.CoroutineDispatcher
import org.koin.plugin.module.dsl.koinApplication
import org.koin.test.verify.definition
import org.koin.test.verify.injectedParameters
import org.koin.test.verify.verify
import org.meshtastic.app.map.MapViewModel
import org.meshtastic.core.ble.BleLogFormat
import org.meshtastic.core.ble.BleLogLevel
import org.meshtastic.core.model.util.NodeIdLookup
import org.meshtastic.feature.node.metrics.MetricsViewModel
import kotlin.test.Test

class KoinVerificationTest {

    @Test
    fun verifyKoinConfiguration() {
        AppKoinModule()
            .module()
            .verify(
                extraTypes =
                listOf(
                    Application::class,
                    Context::class,
                    Lifecycle::class,
                    SavedStateHandle::class,
                    WorkerParameters::class,
                    WorkManager::class,
                    CoroutineDispatcher::class,
                    NodeIdLookup::class,
                    HttpClient::class,
                    HttpClientEngine::class,
                    // BleLoggingConfig is a data class assembled by a factory function. Koin Verify
                    // still introspects its constructor params, so the wrapping enums need to be
                    // declared as known types even though they're never resolved from the graph.
                    BleLogLevel::class,
                    BleLogFormat::class,
                ),
                injections =
                injectedParameters(
                    definition<MapViewModel>(SavedStateHandle::class),
                    definition<MetricsViewModel>(Int::class),
                ),
            )
    }

    @Test
    fun verifyTypedBootstrapLoadsModuleGraph() {
        // koinApplication<T>() is a K2 compiler plugin stub. If the plugin fails to
        // transform it, the stub throws NotImplementedError at runtime. This test
        // validates that the production bootstrap path is correctly transformed by
        // successfully creating and closing the generated Koin application.
        val app = koinApplication<AndroidKoinApp>()
        try {
            // No-op: reaching this point proves the typed bootstrap path did not
            // throw and the generated application could be created.
        } finally {
            app.close()
        }
    }
}
