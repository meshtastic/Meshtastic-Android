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
package org.meshtastic.core.common.util

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import org.koin.core.annotation.Single

/**
 * Listens for `ACTION_LOCALE_CHANGED`, which the platform also broadcasts when the user edits the Android 14+ regional
 * preferences (Settings > System > Languages & input > Regional preferences) — the documented way to notice a
 * temperature-unit change.
 *
 * Registered not-exported. Android documents that a receiver for system-only broadcasts need not pass a flag at all,
 * but a protected system broadcast is still delivered to a not-exported receiver, and passing the flag keeps the
 * platform's own lint check satisfied.
 */
@Single
class AndroidLocaleChangeNotifier(private val context: Application) : LocaleChangeNotifier {

    override val localeChanges: Flow<Unit> = callbackFlow {
        val receiver =
            object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    trySend(Unit)
                }
            }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(Intent.ACTION_LOCALE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        awaitClose { context.unregisterReceiver(receiver) }
    }
}
