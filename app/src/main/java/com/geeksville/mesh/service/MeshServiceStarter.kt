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

package com.geeksville.mesh.service

import android.app.ForegroundServiceStartNotAllowedException
import android.content.Context
import android.os.Build
import com.geeksville.mesh.BuildConfig
import timber.log.Timber

// / Helper function to start running our service
fun MeshService.Companion.startService(context: Context) {
    // Bind to our service using the same mechanism an external client would use (for testing coverage)
    // The following would work for us, but not external users:
    // val intent = Intent(this, MeshService::class.java)
    // intent.action = IMeshService::class.java.name

    // Before binding we want to explicitly create - so the service stays alive forever (so it can keep
    // listening for the bluetooth packets arriving from the radio. And when they arrive forward them
    // to Signal or whatever.
    Timber.i("Trying to start service debug=${BuildConfig.DEBUG}")

    val intent = createIntent(context)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        try {
            context.startForegroundService(intent)
        } catch (ex: ForegroundServiceStartNotAllowedException) {
            Timber.e("Unable to start service: ${ex.message}")
        }
    } else {
        context.startForegroundService(intent)
    }
}
