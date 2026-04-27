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
 * Sends notifications via `osascript` on macOS, using AppleScript's `display notification` command.
 *
 * Content is passed as arguments to a pre-built script — never interpolated into the script source — to prevent
 * injection from untrusted message text. The `on run argv` handler receives title/message as positional args.
 */
class MacOSNotificationSender : NativeNotificationSender {

    override fun send(notification: Notification): Boolean = runCommand(buildCommand(notification))

    /**
     * Builds an `osascript` command that passes title and message as arguments to a safe `on run argv` handler.
     *
     * AppleScript's `on run argv` receives command-line arguments as a list, avoiding any need to escape quotes or
     * special characters in user content.
     */
    internal fun buildCommand(notification: Notification): List<String> = buildList {
        add("osascript")

        // Build the script as a safe handler that reads argv items
        val scriptLines = buildList {
            add("on run argv")
            add("  set notifTitle to item 1 of argv")
            add("  set notifMessage to item 2 of argv")
            add("  set notifSubtitle to item 3 of argv")
            add("  set isSilent to item 4 of argv")
            if (notification.isSilent) {
                add("  display notification notifMessage with title notifTitle subtitle notifSubtitle")
            } else {
                add(
                    "  display notification notifMessage with title notifTitle" +
                        " subtitle notifSubtitle sound name \"default\"",
                )
            }
            add("end run")
        }

        // Pass each line with -e
        for (line in scriptLines) {
            add("-e")
            add(line)
        }

        // Positional arguments after "--"
        add("--")
        add(notification.title)
        add(notification.message)
        add(categorySubtitle(notification.category))
        add(if (notification.isSilent) "true" else "false")
    }

    private fun categorySubtitle(category: Notification.Category): String = when (category) {
        Notification.Category.Message -> "Message"
        Notification.Category.NodeEvent -> "Node Event"
        Notification.Category.Battery -> "Low Battery"
        Notification.Category.Alert -> "Alert"
        Notification.Category.Service -> "Service"
    }

    private fun runCommand(command: List<String>): Boolean = try {
        val process = ProcessBuilder(command).redirectErrorStream(true).start()
        val completed = process.waitFor(PROCESS_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        if (!completed) {
            process.destroyForcibly()
            Logger.w { "osascript timed out after ${PROCESS_TIMEOUT_SECONDS}s" }
            false
        } else {
            val exitCode = process.exitValue()
            if (exitCode != 0) {
                val stderr = process.inputStream.bufferedReader().readText().take(MAX_STDERR_CHARS)
                Logger.w { "osascript exited $exitCode: $stderr" }
            }
            exitCode == 0
        }
    } catch (e: java.io.IOException) {
        Logger.w(e) { "Failed to run osascript" }
        false
    }

    companion object {
        private const val MAX_STDERR_CHARS = 200
    }
}
