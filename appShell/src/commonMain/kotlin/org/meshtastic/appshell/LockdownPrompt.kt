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
package org.meshtastic.appshell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.meshtastic.core.model.service.LockdownState
import org.meshtastic.core.model.util.TimeConstants
import org.meshtastic.core.ui.viewmodel.UIViewModel
import org.meshtastic.feature.settings.lockdown.LockdownDialog

/** Asks for a locked radio's passphrase, and disconnects once the radio acknowledges Lock Now. */
@Suppress("ViewModelForwarding")
@Composable
fun LockdownPrompt(uiViewModel: UIViewModel) {
    val lockdownState by uiViewModel.lockdownState.collectAsStateWithLifecycle()
    LockdownDialog(
        lockdownState = lockdownState,
        onSubmit = { passphrase, boots, hours, sessionMinutes ->
            uiViewModel.sendLockdownUnlock(passphrase, boots, hours, sessionMinutes * TimeConstants.SECONDS_PER_MINUTE)
        },
        onDisconnect = { uiViewModel.setDeviceAddress(NO_DEVICE_ADDRESS) },
    )
    LaunchedEffect(lockdownState) {
        if (lockdownState is LockdownState.LockNowAcknowledged) uiViewModel.setDeviceAddress(NO_DEVICE_ADDRESS)
    }
}

private const val NO_DEVICE_ADDRESS = "n"
