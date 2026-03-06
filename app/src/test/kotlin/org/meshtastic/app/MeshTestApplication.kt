/*
 * Copyright (c) 2025-2026 Meshtastic LLC
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

import androidx.work.Configuration
import dagger.hilt.android.EntryPointAccessors

/**
 * A lightweight application class for Robolectric tests.
 *
 * It prevents heavy background initialization (WorkManager, DatabaseManager) by default to avoid resource leaks and
 * flaky native SQLite issues on the JVM.
 */
class MeshTestApplication : MeshUtilApplication() {

    override fun onCreate() {
        // Only run real onCreate logic if a test explicitly asks for it
        if (shouldInitialize) {
            super.onCreate()
        }
    }

    override fun onTerminate() {
        if (shouldInitialize) {
            val entryPoint = EntryPointAccessors.fromApplication(this, AppEntryPoint::class.java)
            entryPoint.databaseManager().close()
        }
        super.onTerminate()
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setMinimumLoggingLevel(android.util.Log.DEBUG).build()

    companion object {
        /** Set to true in a test @Before block if you need real DB/WorkManager init. */
        var shouldInitialize = false
    }
}
