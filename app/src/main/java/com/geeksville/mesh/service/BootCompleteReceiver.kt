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

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.geeksville.mesh.model.NO_DEVICE_SELECTED
import dagger.hilt.android.AndroidEntryPoint
import org.meshtastic.core.prefs.mesh.MeshPrefs
import javax.inject.Inject

/** This receiver starts the MeshService on boot if a device was previously connected. */
@AndroidEntryPoint
class BootCompleteReceiver : BroadcastReceiver() {

    @Inject lateinit var meshPrefs: MeshPrefs

    override fun onReceive(context: Context, intent: Intent) {
        if (Intent.ACTION_BOOT_COMPLETED != intent.action) {
            return
        }

        val deviceAddress = meshPrefs.deviceAddress
        if (!deviceAddress.isNullOrBlank() && !deviceAddress.equals(NO_DEVICE_SELECTED, ignoreCase = true)) {
            MeshService.startService(context)
        }
    }
}
