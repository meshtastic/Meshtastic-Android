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
import kotlin.test.Test
import org.koin.test.verify.definition
import org.koin.test.verify.injectedParameters
import org.koin.test.verify.verify
import org.meshtastic.app.map.MapViewModel
import org.meshtastic.core.model.util.NodeIdLookup
import org.meshtastic.feature.node.metrics.MetricsViewModel

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
                ),
                injections =
                injectedParameters(
                    definition<MapViewModel>(SavedStateHandle::class),
                    definition<MetricsViewModel>(Int::class),
                ),
            )
    }
}
