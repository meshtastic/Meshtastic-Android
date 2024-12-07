/*
 * Copyright (c) 2024 Meshtastic LLC
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

package com.geeksville.mesh.ui.components

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect

@Composable
fun rememberTimeTickWithLifecycle(): Long {
    val context = LocalContext.current
    var value by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val receiver = TimeBroadcastReceiver { value = System.currentTimeMillis() }

    LifecycleResumeEffect(Unit) {
        receiver.register(context)
        value = System.currentTimeMillis()

        onPauseOrDispose {
            receiver.unregister(context)
        }
    }

    return value
}

private class TimeBroadcastReceiver(
    val onTimeChanged: () -> Unit,
) : BroadcastReceiver() {
    private var registered = false

    override fun onReceive(context: Context, intent: Intent) {
        onTimeChanged()
    }

    fun register(context: Context) {
        if (!registered) {
            val filter = IntentFilter(Intent.ACTION_TIME_TICK)
            context.registerReceiver(this, filter)
            registered = true
        }
    }

    fun unregister(context: Context) {
        if (registered) {
            context.unregisterReceiver(this)
            registered = false
        }
    }
}
