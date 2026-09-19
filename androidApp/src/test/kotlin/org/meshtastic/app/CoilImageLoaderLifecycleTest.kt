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
package org.meshtastic.app

import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.annotation.DelicateCoilApi
import org.junit.After
import org.junit.runner.RunWith
import org.koin.android.ext.android.get
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertSame

@RunWith(RobolectricTestRunner::class)
@Config(application = ImageLoaderOnlyApplication::class, sdk = [34])
class CoilImageLoaderLifecycleTest {
    @After
    @OptIn(DelicateCoilApi::class)
    fun tearDown() {
        SingletonImageLoader.reset()
        WorkManagerTestInitHelper.closeWorkDatabase()
    }

    @Test
    fun productionApplicationProvidesConfiguredKoinImageLoader() {
        val application = ApplicationProvider.getApplicationContext<MeshUtilApplication>()
        val configuredImageLoader = application.get<ImageLoader>()

        assertSame(configuredImageLoader, SingletonImageLoader.get(application))
    }
}

/**
 * Boots the production Application with no database open left in flight at teardown: [startBackgroundInit] is
 * suppressed, and WorkManager is initialized synchronously first so Koin's eager `workManagerFactory()` skips its own
 * `initialize`, whose `ForceStopRunnable` would otherwise open WorkDatabase on framework SQLite from a background
 * thread.
 *
 * Must not be private: Robolectric instantiates the `@Config` application through `AppComponentFactory`, whose
 * `Class.newInstance()` call cannot reach a package-private class.
 */
internal class ImageLoaderOnlyApplication : MeshUtilApplication() {
    override fun onCreate() {
        val configuration =
            Configuration.Builder().setExecutor(SynchronousExecutor()).setTaskExecutor(SynchronousExecutor()).build()
        WorkManagerTestInitHelper.initializeTestWorkManager(this, configuration)
        super.onCreate()
    }

    override fun startBackgroundInit() = Unit
}
