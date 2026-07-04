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
import org.meshtastic.core.repository.Notification
import java.util.concurrent.TimeUnit

private const val PROCESS_TIMEOUT_SECONDS = 5L

/**
 * Sends toast notifications on Windows via PowerShell and the WinRT ToastNotificationManager API.
 *
 * Uses a self-contained PowerShell script passed via `-Command`. All user content is injected through `[xml]` escaping
 * performed by PowerShell's XML parser — never through string interpolation in the script source. Title and message are
 * passed as PowerShell `-ArgumentList` parameters.
 */
class WindowsNotificationSender(private val appName: String = "Meshtastic") : NativeNotificationSender {

    override fun send(notification: Notification): Boolean = runCommand(buildCommand(notification))

    internal fun buildCommand(notification: Notification): List<String> = buildList {
        add("powershell.exe")
        add("-NoProfile")
        add("-NonInteractive")
        add("-Command")

        // Build a safe PowerShell script that takes $args[0] (title) and $args[1] (message) from -ArgumentList.
        // Content is XML-escaped by PowerShell's [xml] cast, so injection-safe.
        val silent = notification.isSilent
        val audioElement =
            if (silent) {
                "<audio silent='true'/>"
            } else {
                "<audio src='ms-winsoundevent:Notification.Default'/>"
            }

        @Suppress("MaxLineLength")
        val script = buildString {
            append("\$title = \$args[0]; \$msg = \$args[1]; ")
            append("[Windows.UI.Notifications.ToastNotificationManager,")
            append(" Windows.UI.Notifications, ContentType = WindowsRuntime] > \$null; ")
            append("[Windows.Data.Xml.Dom.XmlDocument, Windows.Data.Xml.Dom, ContentType = WindowsRuntime] > \$null; ")
            append("\$template = '<toast>")
            append("<visual><binding template=\"ToastGeneric\">")
            append("<text>{0}</text><text>{1}</text>")
            append("</binding></visual>")
            append(audioElement)
            append("</toast>'; ")
            append("\$xml = [Windows.Data.Xml.Dom.XmlDocument]::new(); ")
            // Use string.Format for safe substitution — XML-escapes automatically
            append("\$xml.LoadXml([string]::Format(\$template, ")
            append("[System.Security.SecurityElement]::Escape(\$title), ")
            append("[System.Security.SecurityElement]::Escape(\$msg))); ")
            val safeAppName = appName.replace("'", "''")
            append("\$notifier = [Windows.UI.Notifications.ToastNotificationManager]::")
            append("CreateToastNotifier('$safeAppName'); ")
            append("\$toast = [Windows.UI.Notifications.ToastNotification]::new(\$xml); ")
            append("\$notifier.Show(\$toast)")
        }

        add(script)

        // Title and message as positional arguments
        add(notification.title)
        add(notification.message)
    }

    private fun runCommand(command: List<String>): Boolean = try {
        val process = ProcessBuilder(command).redirectErrorStream(true).start()
        val completed = process.waitFor(PROCESS_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        if (!completed) {
            process.destroyForcibly()
            Logger.w { "powershell toast timed out after ${PROCESS_TIMEOUT_SECONDS}s" }
            false
        } else {
            val exitCode = process.exitValue()
            if (exitCode != 0) {
                val stderr = process.inputStream.bufferedReader().readText().take(MAX_STDERR_CHARS)
                Logger.w { "powershell toast exited $exitCode: $stderr" }
            }
            exitCode == 0
        }
    } catch (e: java.io.IOException) {
        Logger.w(e) { "Failed to run powershell toast" }
        false
    }

    companion object {
        private const val MAX_STDERR_CHARS = 200
    }
}
