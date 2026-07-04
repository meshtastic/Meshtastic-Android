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
package org.meshtastic.desktop.notification

import co.touchlab.kermit.Logger
import com.sun.jna.Function
import com.sun.jna.NativeLibrary
import com.sun.jna.Pointer
import org.meshtastic.core.repository.Notification
import java.util.UUID

/**
 * Sends notifications through macOS UserNotifications (`UNUserNotificationCenter`) via JNA + Objective-C runtime. This
 * ensures notifications are attributed to the app bundle instead of Script Editor.
 */
class MacOSNotificationSender private constructor(private val bridge: MacNotificationBridge) :
    NativeNotificationSender {
    constructor() : this(JnaMacNotificationBridge())

    internal constructor(bridge: MacNotificationBridge, unused: Unit = Unit) : this(bridge)

    @Volatile private var authorizationRequested = false

    override fun send(notification: Notification): Boolean {
        if (!bridge.isAvailable) return false

        if (!authorizationRequested) {
            synchronized(this) {
                if (!authorizationRequested) {
                    val authOk = bridge.requestAuthorization(DEFAULT_AUTHORIZATION_OPTIONS)
                    if (!authOk) {
                        Logger.w { "UNUserNotificationCenter authorization request failed" }
                    }
                    authorizationRequested = true
                }
            }
        }

        return bridge.post(
            title = notification.title,
            message = notification.message,
            subtitle = categorySubtitle(notification.category),
            playSound = !notification.isSilent,
        )
    }

    internal fun categorySubtitle(category: Notification.Category): String = when (category) {
        Notification.Category.Message -> "Message"
        Notification.Category.NodeEvent -> "Node Event"
        Notification.Category.Battery -> "Low Battery"
        Notification.Category.Alert -> "Alert"
        Notification.Category.Service -> "Service"
    }

    companion object {
        // UNAuthorizationOptions: badge(1 << 0), sound(1 << 1), alert(1 << 2)
        internal const val DEFAULT_AUTHORIZATION_OPTIONS = 0b111L
    }
}

internal interface MacNotificationBridge {
    val isAvailable: Boolean

    fun requestAuthorization(options: Long): Boolean

    fun post(title: String, message: String, subtitle: String, playSound: Boolean): Boolean
}

private class JnaMacNotificationBridge : MacNotificationBridge {
    private val objc: NativeLibrary?
    private val objcGetClass: Function?
    private val selRegisterName: Function?
    private val objcMsgSend: Function?

    init {
        val loaded =
            if (!isMacOs()) {
                LoadedBridge(null, null, null, null)
            } else {
                try {
                    NativeLibrary.getInstance("UserNotifications")
                    val nativeObjc = NativeLibrary.getInstance("objc")
                    LoadedBridge(
                        objc = nativeObjc,
                        objcGetClass = nativeObjc.getFunction("objc_getClass"),
                        selRegisterName = nativeObjc.getFunction("sel_registerName"),
                        objcMsgSend = nativeObjc.getFunction("objc_msgSend"),
                    )
                } catch (e: UnsatisfiedLinkError) {
                    Logger.w(e) { "Failed to initialize macOS notification bridge" }
                    LoadedBridge(null, null, null, null)
                } catch (e: SecurityException) {
                    Logger.w(e) { "Failed to initialize macOS notification bridge" }
                    LoadedBridge(null, null, null, null)
                }
            }

        objc = loaded.objc
        objcGetClass = loaded.objcGetClass
        selRegisterName = loaded.selRegisterName
        objcMsgSend = loaded.objcMsgSend
    }

    override val isAvailable: Boolean
        get() = objc != null && objcGetClass != null && selRegisterName != null && objcMsgSend != null

    override fun requestAuthorization(options: Long): Boolean = runCatching {
        val centerClass = classRef("UNUserNotificationCenter") ?: return false
        val center = msg(centerClass, selector("currentNotificationCenter")) ?: return false
        msg(center, selector("requestAuthorizationWithOptions:completionHandler:"), options, Pointer.NULL)
        true
    }
        .getOrElse { e ->
            Logger.w(e) { "Failed to request UNUserNotificationCenter authorization" }
            false
        }

    override fun post(title: String, message: String, subtitle: String, playSound: Boolean): Boolean = runCatching {
        val contentClass = classRef("UNMutableNotificationContent") ?: return false
        val requestClass = classRef("UNNotificationRequest") ?: return false
        val centerClass = classRef("UNUserNotificationCenter") ?: return false

        val content = msg(msg(contentClass, selector("alloc")), selector("init")) ?: return false
        msg(content, selector("setTitle:"), nsString(title) ?: return false)
        msg(content, selector("setBody:"), nsString(message) ?: return false)
        msg(content, selector("setSubtitle:"), nsString(subtitle) ?: return false)

        if (playSound) {
            val soundClass = classRef("UNNotificationSound")
            val defaultSound = soundClass?.let { msg(it, selector("defaultSound")) }
            if (defaultSound != null) {
                msg(content, selector("setSound:"), defaultSound)
            }
        }

        val request =
            msg(
                requestClass,
                selector("requestWithIdentifier:content:trigger:"),
                nsString(UUID.randomUUID().toString()) ?: return false,
                content,
                Pointer.NULL,
            ) ?: return false

        val center = msg(centerClass, selector("currentNotificationCenter")) ?: return false
        msg(center, selector("addNotificationRequest:withCompletionHandler:"), request, Pointer.NULL)
        true
    }
        .getOrElse { e ->
            Logger.w(e) { "Failed to post macOS notification" }
            false
        }

    private fun classRef(name: String): Pointer? = objcGetClass?.invoke(Pointer::class.java, arrayOf(name)) as? Pointer

    private fun selector(name: String): Pointer =
        selRegisterName?.invoke(Pointer::class.java, arrayOf(name)) as? Pointer
            ?: error("Unable to resolve selector '$name'")

    private fun nsString(value: String): Pointer? {
        val nsStringClass = classRef("NSString") ?: return null
        return msg(nsStringClass, selector("stringWithUTF8String:"), value)
    }

    private fun msg(receiver: Pointer?, selector: Pointer, vararg args: Any?): Pointer? {
        val function = objcMsgSend
        val target = receiver
        if (function == null || target == null) return null
        val callArgs = arrayOfNulls<Any>(args.size + 2)
        callArgs[0] = target
        callArgs[1] = selector
        args.copyInto(callArgs, destinationOffset = 2)
        return function.invoke(Pointer::class.java, callArgs) as? Pointer
    }

    private fun isMacOs(): Boolean =
        System.getProperty("os.name", "").lowercase().let { it.contains("mac") || it.contains("darwin") }

    private data class LoadedBridge(
        val objc: NativeLibrary?,
        val objcGetClass: Function?,
        val selRegisterName: Function?,
        val objcMsgSend: Function?,
    )
}
