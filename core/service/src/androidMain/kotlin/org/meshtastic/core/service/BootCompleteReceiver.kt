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

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.compose.resources.StringResource
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.meshtastic.core.common.util.safeCatchingAll
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.repository.MeshPrefs
import org.meshtastic.core.repository.Notification
import org.meshtastic.core.repository.NotificationManager
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.boot_reconnect_blocked_message
import org.meshtastic.core.resources.boot_reconnect_blocked_title
import org.meshtastic.core.resources.getStringSuspend

/** This receiver starts the MeshService on boot if a device was previously connected. */
class BootCompleteReceiver :
    BroadcastReceiver(),
    KoinComponent {

    private val meshPrefs: MeshPrefs by inject()
    private val dispatchers: CoroutineDispatchers by inject()
    private val notificationManager: NotificationManager by inject()
    private val scope by lazy { CoroutineScope(SupervisorJob() + dispatchers.default) }

    @Suppress("TooGenericExceptionCaught")
    override fun onReceive(context: Context, intent: Intent) {
        // Only these two actions carry a background foreground-service-start exemption. The manifest also filters the
        // OEM quick-boot actions, which do not, so acting on those would guarantee a rejected start.
        if (intent.action !in EXEMPT_ACTIONS) {
            Logger.d { "BootCompleteReceiver: ignoring non-exempt action ${intent.action}" }
            return
        }

        val pendingResult = goAsync()
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                val address =
                    try {
                        withTimeout(PREFERENCES_LOAD_TIMEOUT_MILLIS) {
                            // Keep the IO read as a sibling so a queued dispatcher cannot delay timeout completion.
                            val preferencesLoad = scope.async(dispatchers.io) { meshPrefs.awaitDeviceAddress() }
                            try {
                                preferencesLoad.await()
                            } finally {
                                preferencesLoad.cancel()
                            }
                        }
                    } catch (_: TimeoutCancellationException) {
                        Logger.w { "BootCompleteReceiver: timed out loading the selected device" }
                        return@launch
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Logger.w(e) { "BootCompleteReceiver: failed to load the selected device" }
                        return@launch
                    }

                when (bootReconnectDecision(address, context.hasBluetoothConnectPermission())) {
                    BootReconnectDecision.NO_DEVICE -> {
                        Logger.d { "BootCompleteReceiver: no device previously connected, skipping service start" }
                        return@launch
                    }

                    BootReconnectDecision.BLE_PERMISSION_MISSING -> {
                        // Starting here would spin an invisible retry loop forever: the transport treats a missing
                        // permission as transient and drops the reason, and no user is present to notice. Tell them
                        // instead, once, with a tap target that leads to the screen where it can be fixed.
                        Logger.w { "BootCompleteReceiver: BLE device selected but Bluetooth permission is missing" }
                        notifyBluetoothPermissionMissing()
                        return@launch
                    }

                    BootReconnectDecision.START_SERVICE -> {
                        Logger.i { "BootCompleteReceiver: starting MeshService after ${intent.action}" }
                        MeshService.startService(context, ServiceStartTrigger.BootCompleted)
                    }
                }
            } finally {
                pendingResult.finish()
                scope.cancel()
            }
        }
    }

    /**
     * Posts the one thing the user can act on: a notification naming the missing permission and opening the Connections
     * screen, where the recovery card now lives.
     *
     * Best-effort by design. If POST_NOTIFICATIONS is also denied the dispatch simply returns false — there is no
     * surface left to reach an absent user through, and failing loudly here would help nobody.
     */
    private suspend fun notifyBluetoothPermissionMissing() {
        // Untranslated fallbacks rather than no notification, and a hard bound on the wait. A boot broadcast runs
        // before anything has warmed the Compose resource bundle and has only seconds of goAsync() budget before the
        // system kills it; losing the user's only signal to a slow or failed resource load would be the worse outcome.
        val title = resolveOrFallback(Res.string.boot_reconnect_blocked_title, UNTRANSLATED_BLOCKED_TITLE)
        val message = resolveOrFallback(Res.string.boot_reconnect_blocked_message, UNTRANSLATED_BLOCKED_MESSAGE)

        @Suppress("TooGenericExceptionCaught")
        try {
            notificationManager.dispatch(
                Notification(
                    title = title,
                    message = message,
                    type = Notification.Type.Warning,
                    category = Notification.Category.Service,
                    id = BLE_PERMISSION_NOTIFICATION_ID,
                    deepLinkUri = CONNECTIONS_DEEP_LINK,
                ),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.w(e) { "BootCompleteReceiver: could not post the missing-permission notification" }
        }
    }

    private suspend fun resolveOrFallback(resource: StringResource, fallback: String): String =
        withTimeoutOrNull(STRING_RESOLVE_TIMEOUT_MILLIS) {
            safeCatchingAll { getStringSuspend(resource) }.getOrDefault(fallback)
        } ?: fallback

    private companion object {
        const val PREFERENCES_LOAD_TIMEOUT_MILLIS = 5_000L

        /** A broadcast has seconds, not indefinite time; fall back to untranslated text rather than stall. */
        const val STRING_RESOLVE_TIMEOUT_MILLIS = 2_000L

        /** Stable id so a second boot replaces the notice rather than stacking another copy. */
        const val BLE_PERMISSION_NOTIFICATION_ID = 0x81E9

        const val CONNECTIONS_DEEP_LINK = "meshtastic://meshtastic/connections"

        const val UNTRANSLATED_BLOCKED_TITLE = "Meshtastic can't reconnect"
        const val UNTRANSLATED_BLOCKED_MESSAGE =
            "Nearby devices permission is off, so your radio cannot be reached over Bluetooth. " +
                "Tap to turn it back on."

        /**
         * `MY_PACKAGE_REPLACED` is filtered by the manifest so an in-place upgrade restores the radio link without
         * waiting for the user to reopen the app, and it is exempt from the background-start restriction just as
         * `BOOT_COMPLETED` is.
         */
        val EXEMPT_ACTIONS = setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED)
    }
}

/**
 * True when the app currently holds the permission a BLE connection needs.
 *
 * Pre-Android-12 there is no runtime Bluetooth permission — the platform gates the *scan* on location, but an already
 * bonded device can be reconnected without it — so a reconnect is never blocked on those releases.
 */
private fun Context.hasBluetoothConnectPermission(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
    ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) ==
    PackageManager.PERMISSION_GRANTED
