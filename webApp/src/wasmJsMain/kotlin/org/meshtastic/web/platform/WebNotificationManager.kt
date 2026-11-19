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
package org.meshtastic.web.platform

import co.touchlab.kermit.Logger
import kotlinx.browser.window
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.meshtastic.core.repository.Notification
import org.meshtastic.core.repository.NotificationManager
import org.meshtastic.core.repository.NotificationPrefs
import org.meshtastic.core.repository.allows
import org.w3c.notifications.NotificationOptions
import org.w3c.notifications.Notification as BrowserNotification

/**
 * Shows [Notification]s through the browser's Notifications API, gated on the same preferences as desktop. Without the
 * API or the permission a dispatch is a quiet false.
 *
 * The page asks for the permission only when the user switches a notification category on in settings, the one
 * notification control the web app has, and only while that click's user activation is live, which is what browsers
 * require before they show their prompt.
 */
class WebNotificationManager(private val prefs: NotificationPrefs, scope: CoroutineScope) : NotificationManager {

    private val shown = mutableMapOf<String, BrowserNotification>()
    private var untaggedCount = 0

    init {
        askWhenSwitchedOn(prefs.messagesEnabled, scope)
        askWhenSwitchedOn(prefs.nodeEventsEnabled, scope)
        askWhenSwitchedOn(prefs.lowBatteryEnabled, scope)
        askWhenSwitchedOn(prefs.reactionsEnabled, scope)
        askWhenSwitchedOn(prefs.newWaypointsEnabled, scope)
    }

    override suspend fun dispatch(notification: Notification): Boolean = prefs.allows(notification.category) &&
        notificationsSupported() &&
        notificationPermission() == "granted" &&
        show(notification)

    private fun show(notification: Notification): Boolean {
        val key = notification.tag ?: "untagged-${untaggedCount++}"
        var handle: BrowserNotification? = null
        handle =
            showNotification(
                title = notification.title,
                body = notification.message,
                tag = notification.tag,
                silent = notification.isSilent,
                onClose = { if (shown[key] === handle) shown.remove(key) },
            )
        // Null on Android Chrome, which only shows notifications from a service worker the web app does not register.
        handle?.let { shown[key] = it } ?: Logger.d { "Browser refused notification: ${notification.category}" }
        return handle != null
    }

    override fun cancel(category: Notification.Category, id: Int) {
        shown.remove(Notification.tagFor(category, id))?.close()
    }

    override fun cancelAll() {
        shown.values.toList().forEach(BrowserNotification::close)
        shown.clear()
    }

    private fun askWhenSwitchedOn(category: StateFlow<Boolean>, scope: CoroutineScope) {
        var previous = category.value
        category
            .onEach { enabled ->
                val switchedOn = enabled && !previous
                previous = enabled
                if (
                    switchedOn &&
                    notificationsSupported() &&
                    notificationPermission() == "default" &&
                    hasUserActivation()
                ) {
                    BrowserNotification.requestPermission().catch { null }
                }
            }
            .launchIn(scope)
    }
}

private fun notificationsSupported(): Boolean = js("typeof Notification !== 'undefined'")

private fun notificationPermission(): String = BrowserNotification.permission.toString()

/** True while the page holds transient user activation; assumed where `navigator.userActivation` is missing. */
private fun hasUserActivation(): Boolean = js("navigator.userActivation ? navigator.userActivation.isActive : true")

/**
 * Shows a notification that focuses the tab when clicked; null when the browser refuses to construct one. Android
 * Chrome shows notifications only from a service worker, and its constructor throws a raw JS `TypeError` that
 * Kotlin/Wasm surfaces as a bare [Throwable].
 */
@Suppress("TooGenericExceptionCaught", "SwallowedException")
private fun showNotification(
    title: String,
    body: String,
    tag: String?,
    silent: Boolean,
    onClose: () -> Unit,
): BrowserNotification? = try {
    BrowserNotification(title, NotificationOptions(body = body, tag = tag.orEmpty(), silent = silent)).apply {
        onclick = {
            window.focus()
            close()
        }
        addEventListener("close", { onClose() })
    }
} catch (t: Throwable) {
    null
}
