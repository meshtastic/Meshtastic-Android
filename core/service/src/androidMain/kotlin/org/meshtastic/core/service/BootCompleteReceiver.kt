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

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import co.touchlab.kermit.Logger
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.meshtastic.core.repository.MeshPrefs

/** This receiver starts the MeshService on boot if a device was previously connected. */
class BootCompleteReceiver :
    BroadcastReceiver(),
    KoinComponent {

    private val meshPrefs: MeshPrefs by inject()

    override fun onReceive(context: Context, intent: Intent) {
        if (Intent.ACTION_BOOT_COMPLETED != intent.action) {
            return
        }
        val address = meshPrefs.deviceAddress.value
        if (address.isNullOrBlank() || address.equals("n", ignoreCase = true)) {
            Logger.d { "BootCompleteReceiver: no device previously connected, skipping service start" }
            return
        }

        Logger.i { "BootCompleteReceiver: starting MeshService for device $address" }
        MeshService.startService(context)
    }
}
