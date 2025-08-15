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
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.geeksville.mesh.android.ServiceClient
import com.geeksville.mesh.concurrent.handledLaunch
import com.geeksville.mesh.service.ServiceRepository
import dagger.hilt.android.scopes.ActivityScoped
import kotlinx.coroutines.Job
import javax.inject.Inject

@ActivityScoped
class MeshServiceClient
@Inject
constructor(
    /**
     * Activity is injected here instead of LifecycleOwner because ApplicationModule defines its own LifecycleOwner
     * which overrides the default binding for @ActivityScoped. The solution to this is to add a qualifier to the
     * LifecycleOwner provider in ApplicationModule.
     */
    activity: Activity,
    private val serviceRepository: ServiceRepository,
) : ServiceClient<IMeshService>(IMeshService.Stub::asInterface) {

    // TODO Use the default binding for @AcitvityScoped
    private val lifecycleOwner: LifecycleOwner = activity as LifecycleOwner

    // TODO Inject this for ease of testing
    private var serviceSetupJob: Job? = null

    override fun onConnected(service: IMeshService) {
        serviceSetupJob?.cancel()
        serviceSetupJob =
            lifecycleOwner.lifecycleScope.handledLaunch {
                serviceRepository.setMeshService(service)
                debug("connected to mesh service, connectionState=${serviceRepository.connectionState.value}")
            }
    }

    override fun onDisconnected() {
        serviceSetupJob?.cancel()
        serviceRepository.setMeshService(null)
    }
}
