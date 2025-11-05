/*
 * Copyright (c) 2025 Meshtastic LLC
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

package com.geeksville.mesh

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

/**
 * The main application class for Meshtastic.
 *
 * This class is annotated with [HiltAndroidApp] to enable Hilt for dependency injection. It initializes core
 * application components, including analytics and platform-specific helpers, and manages analytics consent based on
 * user preferences.
 */
@HiltAndroidApp
class MeshUtilApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        initializeMaps(this)
    }
}

fun logAssert(executeReliableWrite: Boolean) {
    if (!executeReliableWrite) {
        val ex = AssertionError("Assertion failed")
        Timber.e(ex)
        throw ex
    }
}
