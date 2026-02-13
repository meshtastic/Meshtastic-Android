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
package org.meshtastic.core.ui.component

import android.content.Intent
import android.content.IntentFilter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import no.nordicsemi.android.common.core.registerReceiver

/**
 * Remembers a time tick that updates every minute. Uses [registerReceiver] from Nordic Common for automatic lifecycle
 * management.
 *
 * @return The current time in milliseconds, updating every minute.
 */
@Composable
fun rememberTimeTickWithLifecycle(): Long {
    var value by remember { mutableLongStateOf(System.currentTimeMillis()) }

    registerReceiver(IntentFilter(Intent.ACTION_TIME_TICK)) { value = System.currentTimeMillis() }

    return value
}
