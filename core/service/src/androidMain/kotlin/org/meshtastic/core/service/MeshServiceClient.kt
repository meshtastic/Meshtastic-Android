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
package org.meshtastic.core.service

import android.content.Context
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import co.touchlab.kermit.Logger
import kotlinx.coroutines.launch
import org.koin.core.annotation.Factory

/**
 * Activity-lifecycle-aware component that starts [MeshService] when the Activity becomes visible.
 *
 * With the SDK hard-cutover, AIDL binding is no longer used. This simply ensures the foreground service is running
 * while the UI is active.
 */
@Factory
class MeshServiceClient(
    private val context: Context,
) : DefaultLifecycleObserver {

    private val lifecycleOwner: LifecycleOwner = context as LifecycleOwner

    init {
        Logger.d { "Adding MeshServiceClient as LifecycleObserver for $lifecycleOwner" }
        lifecycleOwner.lifecycle.addObserver(this)
    }

    override fun onStart(owner: LifecycleOwner) {
        super.onStart(owner)
        Logger.d { "Lifecycle: ON_START — starting MeshService" }
        owner.lifecycleScope.launch {
            try {
                MeshService.startService(context)
            } catch (ex: Exception) {
                Logger.e { "Failed to start MeshService: ${ex.message}" }
            }
        }
    }

    override fun onDestroy(owner: LifecycleOwner) {
        super.onDestroy(owner)
        owner.lifecycle.removeObserver(this)
        Logger.d { "Removed MeshServiceClient as LifecycleObserver" }
    }
}
