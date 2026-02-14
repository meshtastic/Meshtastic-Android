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
package com.geeksville.mesh

import android.content.Context
import android.content.Context.BIND_ABOVE_CLIENT
import android.content.Context.BIND_AUTO_CREATE
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import co.touchlab.kermit.Logger
import com.geeksville.mesh.android.BindFailedException
import com.geeksville.mesh.android.ServiceClient
import com.geeksville.mesh.concurrent.SequentialJob
import com.geeksville.mesh.service.MeshService
import com.geeksville.mesh.service.startService
import dagger.hilt.android.qualifiers.ActivityContext
import dagger.hilt.android.scopes.ActivityScoped
import kotlinx.coroutines.launch
import org.meshtastic.core.service.IMeshService
import org.meshtastic.core.service.ServiceRepository
import javax.inject.Inject

/** A Activity-lifecycle-aware [ServiceClient] that binds [MeshService] once the Activity is started. */
@ActivityScoped
class MeshServiceClient
@Inject
constructor(
    @ActivityContext private val context: Context,
    private val serviceRepository: ServiceRepository,
    private val serviceSetupJob: SequentialJob,
) : ServiceClient<IMeshService>(IMeshService.Stub::asInterface),
    DefaultLifecycleObserver {

    private val lifecycleOwner: LifecycleOwner = context as LifecycleOwner

    init {
        Logger.d { "Adding self as LifecycleObserver for $lifecycleOwner" }
        lifecycleOwner.lifecycle.addObserver(this)
    }

    // region ServiceClient overrides

    override fun onConnected(service: IMeshService) {
        serviceSetupJob.launch(lifecycleOwner.lifecycleScope) {
            serviceRepository.setMeshService(service)
            Logger.d { "connected to mesh service, connectionState=${serviceRepository.connectionState.value}" }
        }
    }

    override fun onDisconnected() {
        serviceSetupJob.cancel()
        serviceRepository.setMeshService(null)
    }

    // endregion

    // region DefaultLifecycleObserver overrides

    override fun onStart(owner: LifecycleOwner) {
        super.onStart(owner)
        Logger.d { "Lifecycle: ON_START" }

        owner.lifecycleScope.launch {
            try {
                bindMeshService()
            } catch (ex: BindFailedException) {
                Logger.e { "Bind of MeshService failed: ${ex.message}" }
            }
        }
    }

    override fun onDestroy(owner: LifecycleOwner) {
        super.onDestroy(owner)
        Logger.d { "Lifecycle: ON_DESTROY" }

        owner.lifecycle.removeObserver(this)
        Logger.d { "Removed self as LifecycleObserver to $lifecycleOwner" }
    }

    // endregion

    @Suppress("TooGenericExceptionCaught")
    private suspend fun bindMeshService() {
        Logger.d { "Binding to mesh service!" }
        try {
            MeshService.startService(context)
        } catch (ex: Exception) {
            Logger.e { "Failed to start service from activity - but ignoring because bind will work: ${ex.message}" }
        }

        connect(context, MeshService.createIntent(context), BIND_AUTO_CREATE or BIND_ABOVE_CLIENT)
    }
}
