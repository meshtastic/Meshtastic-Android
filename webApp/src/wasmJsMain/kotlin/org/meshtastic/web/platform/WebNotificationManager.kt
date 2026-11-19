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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.await
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
 * Notifications go through the service worker registration when one is active, which Android Chrome requires and which
 * lets a click open the app after its tab is gone; a page-level notification is the fallback. Each lands in its
 * [Notification.tag] slot, so a newer one for the same conversation or node replaces the last, and a click opens its
 * [Notification.deepLink] through [NotificationClicks].
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
        (showInWorker(notification) || showInPage(notification))

    @Suppress("TooGenericExceptionCaught")
    private suspend fun showInWorker(notification: Notification): Boolean = try {
        showThroughWorker(
            title = notification.title,
            body = notification.message,
            tag = notification.tag,
            silent = notification.isSilent,
            renotify = !notification.isSilent && !notification.onlyAlertOnce,
            deepLink = notification.deepLink,
        )
            .await<JsBoolean>()
            .toBoolean()
    } catch (e: CancellationException) {
        throw e
    } catch (t: Throwable) {
        Logger.d(t) { "Service worker refused notification: ${notification.category}" }
        false
    }

    private fun showInPage(notification: Notification): Boolean {
        val key = notification.tag ?: "untagged-${untaggedCount++}"
        var handle: BrowserNotification? = null
        handle =
            showPageNotification(
                title = notification.title,
                body = notification.message,
                tag = notification.tag,
                silent = notification.isSilent,
                onClick = { notification.deepLink?.let(NotificationClicks::open) },
                onClose = { if (shown[key] === handle) shown.remove(key) },
            )
        handle?.let { shown[key] = it } ?: Logger.d { "Browser refused notification: ${notification.category}" }
        return handle != null
    }

    override fun cancel(category: Notification.Category, id: Int) {
        val tag = Notification.tagFor(category, id)
        shown.remove(tag)?.close()
        closeWorkerNotifications(tag)
    }

    override fun cancelAll() {
        shown.values.toList().forEach(BrowserNotification::close)
        shown.clear()
        closeWorkerNotifications(null)
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
 * Shows a page-level notification that focuses the tab when clicked; null when the browser refuses to construct one.
 * Android Chrome shows notifications only from a service worker, and its constructor throws a raw JS `TypeError` that
 * Kotlin/Wasm surfaces as a bare [Throwable].
 */
@Suppress("TooGenericExceptionCaught", "SwallowedException", "LongParameterList")
private fun showPageNotification(
    title: String,
    body: String,
    tag: String?,
    silent: Boolean,
    onClick: () -> Unit,
    onClose: () -> Unit,
): BrowserNotification? = try {
    BrowserNotification(title, NotificationOptions(body = body, tag = tag.orEmpty(), silent = silent)).apply {
        onclick = {
            window.focus()
            close()
            onClick()
        }
        addEventListener("close", { onClose() })
    }
} catch (t: Throwable) {
    null
}
