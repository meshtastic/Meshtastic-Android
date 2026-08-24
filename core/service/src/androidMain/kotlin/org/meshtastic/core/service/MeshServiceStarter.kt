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

import android.content.Context
import co.touchlab.kermit.Logger

/**
 * Starts [MeshService] as a foreground service so the radio link stays alive for the duration of the connection.
 *
 * The start is gated on [ForegroundStartPolicy]: when the platform would reject it we do not call
 * `startForegroundService()` at all. That matters for more than tidiness — a rejected start still leaves
 * ActivityManager expecting the service to reach the foreground, and the resulting watchdog kills the process. Not
 * asking is the only way to be sure.
 *
 * There is deliberately no retry. Every context that can legally promote this app to the foreground already has its own
 * trigger, and the dominant one — the user opening the app — fires on every `MainActivity.onStart()`. A background
 * retry has no exemption the original caller lacked, so it can only fail again.
 */
fun MeshService.Companion.startService(context: Context, trigger: ServiceStartTrigger) {
    val appInForeground = isAppInForeground()
    if (!ForegroundStartPolicy.isForegroundStartAllowed(trigger, appInForeground)) {
        Logger.i {
            "Skipping MeshService start: trigger=$trigger is not permitted to start a foreground service while " +
                "backgrounded. Waiting for the next user-visible start."
        }
        return
    }

    Logger.i { "Starting MeshService (trigger=$trigger, appInForeground=$appInForeground)" }
    try {
        context.startForegroundService(createIntent(context))
    } catch (ex: IllegalStateException) {
        // ForegroundServiceStartNotAllowedException extends IllegalStateException (via ServiceStartNotAllowedException)
        // and only exists from API 31; catching the supertype keeps this call site free of an API-gated class
        // reference. The service was never created, so there is nothing to tear down here.
        Logger.w(ex) { "Rejected MeshService foreground start (trigger=$trigger)" }
    } catch (ex: SecurityException) {
        Logger.w(ex) { "Missing permission for MeshService foreground start (trigger=$trigger)" }
    }
}
