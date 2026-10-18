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

import android.app.ActivityManager

/**
 * Whether this process currently holds a user-visible component.
 *
 * Uses process importance rather than a lifecycle observer because importance is what ActivityManager itself consults
 * when deciding whether a foreground-service start is permitted, and it is correct even in a process that was revived
 * by a broadcast and has no activity at all.
 *
 * `IMPORTANCE_FOREGROUND` is the conservative choice: a false negative merely defers a service start to the next
 * trigger, whereas a false positive produces the exception this check exists to avoid.
 */
internal fun isAppInForeground(): Boolean {
    val state = ActivityManager.RunningAppProcessInfo()
    ActivityManager.getMyMemoryState(state)
    return state.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
}
