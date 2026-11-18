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
@file:Suppress(
    // Every js("...") snippet references its Kotlin parameters inside the JS source, which detekt cannot see.
    "UnusedParameter",
)

package org.meshtastic.web.platform

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.meshtastic.core.repository.Notification
import org.meshtastic.core.repository.NotificationManager
import org.meshtastic.core.repository.NotificationPrefs

/**
 * Shows [Notification]s through the browser's Notifications API, gated on the same preferences as desktop. Without the
 * API or the permission a dispatch is a quiet false.
 *
 * The page asks for the permission only when the user switches a notification category on in settings, the one
 * notification control the web app has, and only while that click's user activation is live, which is what browsers
 * require before they show their prompt.
 */
class WebNotificationManager(private val prefs: NotificationPrefs, scope: CoroutineScope) : NotificationManager {

    private val shown = mutableMapOf<String, JsAny>()
    private var untaggedCount = 0

    init {
        askWhenSwitchedOn(prefs.messagesEnabled, scope)
        askWhenSwitchedOn(prefs.nodeEventsEnabled, scope)
        askWhenSwitchedOn(prefs.lowBatteryEnabled, scope)
        askWhenSwitchedOn(prefs.reactionsEnabled, scope)
        askWhenSwitchedOn(prefs.newWaypointsEnabled, scope)
    }

    override suspend fun dispatch(notification: Notification): Boolean = isEnabled(notification.category) &&
        notificationsSupported() &&
        notificationPermission() == "granted" &&
        show(notification)

    private suspend fun isEnabled(category: Notification.Category): Boolean = when (category) {
        Notification.Category.Message -> prefs.messagesEnabled.value
        Notification.Category.Reaction -> prefs.loadReactionsEnabled()
        Notification.Category.Waypoint -> prefs.loadNewWaypointsEnabled()
        Notification.Category.NodeEvent -> prefs.nodeEventsEnabled.value
        Notification.Category.MeshBeacon -> prefs.nodeEventsEnabled.value
        Notification.Category.Battery -> prefs.lowBatteryEnabled.value
        Notification.Category.Alert -> true
        Notification.Category.Client -> true
        Notification.Category.Service -> true
    }

    private fun show(notification: Notification): Boolean {
        val key = notification.id?.let(::keyFor) ?: "untagged-${untaggedCount++}"
        var handle: JsAny? = null
        handle =
            showNotification(
                title = notification.title,
                body = notification.message,
                tag = notification.id?.toString(),
                silent = notification.isSilent,
                onClose = { if (shown[key] === handle) shown.remove(key) },
            )
        // Null on Android Chrome, which only shows notifications from a service worker the web app does not register.
        handle?.let { shown[key] = it } ?: Logger.d { "Browser refused notification: ${notification.category}" }
        return handle != null
    }

    override fun cancel(id: Int) {
        shown.remove(keyFor(id))?.let(::closeNotification)
    }

    override fun cancelAll() {
        shown.values.toList().forEach(::closeNotification)
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
                    requestNotificationPermission()
                }
            }
            .launchIn(scope)
    }

    private fun keyFor(id: Int) = "id-$id"
}

private fun notificationsSupported(): Boolean = js("typeof Notification !== 'undefined'")

private fun notificationPermission(): String = js("Notification.permission")

/** True while the page holds transient user activation; assumed where `navigator.userActivation` is missing. */
private fun hasUserActivation(): Boolean = js("navigator.userActivation ? navigator.userActivation.isActive : true")

private fun requestNotificationPermission() {
    js("{ Notification.requestPermission().catch(() => {}); }")
}

/** Shows a notification that focuses the tab when clicked; null when the browser refuses to construct one. */
private fun showNotification(title: String, body: String, tag: String?, silent: Boolean, onClose: () -> Unit): JsAny? =
    js(
        """{
    try {
        const options = { body: body, silent: silent };
        if (tag) options.tag = tag;
        const n = new Notification(title, options);
        n.onclick = () => { window.focus(); n.close(); };
        n.onclose = () => onClose();
        return n;
    } catch (e) {
        return null;
    }
}""",
    )

private fun closeNotification(notification: JsAny) {
    js("notification.close()")
}
