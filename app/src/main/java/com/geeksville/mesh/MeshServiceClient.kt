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

import android.app.Activity
import androidx.appcompat.app.AppCompatActivity.BIND_ABOVE_CLIENT
import androidx.appcompat.app.AppCompatActivity.BIND_AUTO_CREATE
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.geeksville.mesh.android.BindFailedException
import com.geeksville.mesh.android.ServiceClient
import com.geeksville.mesh.concurrent.handledLaunch
import com.geeksville.mesh.service.MeshService
import com.geeksville.mesh.service.startService
import dagger.hilt.android.scopes.ActivityScoped
import kotlinx.coroutines.Job
import org.meshtastic.core.service.IMeshService
import org.meshtastic.core.service.ServiceRepository
import timber.log.Timber
import javax.inject.Inject

/** A Activity-lifecycle-aware [ServiceClient] that binds [MeshService] once the Activity is started. */
@ActivityScoped
class MeshServiceClient
@Inject
constructor(
    /**
     * Ideally, this would be broken up into Context and LifecycleOwner. However, ApplicationModule defines its own
     * LifecycleOwner which overrides the default binding for @ActivityScoped. The solution to this is to add a
     * qualifier to the LifecycleOwner provider in ApplicationModule.
     */
    private val activity: Activity,
    private val serviceRepository: ServiceRepository,
) : ServiceClient<IMeshService>(IMeshService.Stub::asInterface),
    DefaultLifecycleObserver {

    // TODO Use the default binding for @ActivityScoped
    private val lifecycleOwner: LifecycleOwner = activity as LifecycleOwner

    // TODO Inject this for ease of testing
    private var serviceSetupJob: Job? = null

    init {
        Timber.d("Adding self as LifecycleObserver for $lifecycleOwner")
        lifecycleOwner.lifecycle.addObserver(this)
    }

    // region ServiceClient overrides

    override fun onConnected(service: IMeshService) {
        serviceSetupJob?.cancel()
        serviceSetupJob =
            lifecycleOwner.lifecycleScope.handledLaunch {
                serviceRepository.setMeshService(service)
                Timber.d("connected to mesh service, connectionState=${serviceRepository.connectionState.value}")
            }
    }

    override fun onDisconnected() {
        serviceSetupJob?.cancel()
        serviceRepository.setMeshService(null)
    }

    // endregion

    // region DefaultLifecycleObserver overrides

    override fun onStart(owner: LifecycleOwner) {
        super.onStart(owner)
        Timber.d("Lifecycle: ON_START")

        try {
            bindMeshService()
        } catch (ex: BindFailedException) {
            Timber.e("Bind of MeshService failed: ${ex.message}")
        }
    }

    override fun onDestroy(owner: LifecycleOwner) {
        super.onDestroy(owner)
        Timber.d("Lifecycle: ON_DESTROY")

        owner.lifecycle.removeObserver(this)
        Timber.d("Removed self as LifecycleObserver to $lifecycleOwner")
    }

    // endregion

    @Suppress("TooGenericExceptionCaught")
    private fun bindMeshService() {
        Timber.d("Binding to mesh service!")
        try {
            MeshService.startService(activity)
        } catch (ex: Exception) {
            Timber.e("Failed to start service from activity - but ignoring because bind will work: ${ex.message}")
        }

        connect(activity, MeshService.createIntent(activity), BIND_AUTO_CREATE + BIND_ABOVE_CLIENT)
    }
}
