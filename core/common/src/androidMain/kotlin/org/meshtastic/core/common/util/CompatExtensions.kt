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

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Parcelable
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat

/** Retrieves a [Parcelable] extra from an [Intent] in a backward-compatible way. */
inline fun <reified T : Parcelable> Intent.getParcelableExtraCompat(key: String?): T? =
    IntentCompat.getParcelableExtra(this, key, T::class.java)

/** Registers a [BroadcastReceiver] using [ContextCompat] to ensure consistent behavior across Android versions. */
fun Context.registerReceiverCompat(
    receiver: BroadcastReceiver,
    filter: IntentFilter,
    flag: Int = ContextCompat.RECEIVER_EXPORTED,
) {
    ContextCompat.registerReceiver(this, receiver, filter, flag)
}
