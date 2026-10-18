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
package org.meshtastic.core.ui.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import co.touchlab.kermit.Logger

@Composable
actual fun rememberLiveUpdateSupport(): LiveUpdateSupport {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.BAKLAVA) return LiveUpdateSupport.Unavailable
    val context = LocalContext.current
    val turnedOff = remember(context) { mutableStateOf(!context.canPostPromotedNotifications()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { turnedOff.value = !context.canPostPromotedNotifications() }
    val openSettings = remember(context) { { context.openPromotionSettings() } }
    return LiveUpdateSupport(available = true, turnedOff = turnedOff.value, openSettings = openSettings)
}

private fun Context.canPostPromotedNotifications(): Boolean =
    NotificationManagerCompat.from(this).canPostPromotedNotifications()

private fun Context.openPromotionSettings() {
    val actions = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
            add(Settings.ACTION_APP_NOTIFICATION_PROMOTION_SETTINGS)
        }
        add(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
    }
    for (action in actions) {
        val intent = Intent(action).putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        try {
            startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        } catch (ex: ActivityNotFoundException) {
            Logger.w(ex) { "No activity for ${intent.action}" }
        }
    }
}
