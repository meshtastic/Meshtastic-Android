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
package org.meshtastic.desktop

import java.awt.Desktop
import java.util.concurrent.atomic.AtomicReference

/** Published by the composition so [installQuitHandler] can end the Compose loop from AppKit's quit thread. */
private val exitApplicationRef = AtomicReference<(() -> Unit)?>(null)

/** Hands the running composition's `exitApplication` to [installQuitHandler]. */
internal fun publishExitApplication(exitApplication: () -> Unit) = exitApplicationRef.set(exitApplication)

/**
 * Routes macOS's AppKit quit (Cmd+Q and the app menu) into `exitApplication` so the shutdown in `main` runs at all.
 * Without it AppKit kills the JVM directly and every teardown is skipped. CMP-6359, still open.
 */
internal fun installQuitHandler() {
    if (!Desktop.isDesktopSupported()) return
    val desktop = Desktop.getDesktop()
    if (!desktop.isSupported(Desktop.Action.APP_QUIT_HANDLER)) return
    desktop.setQuitHandler { _, response ->
        val exitApplication = exitApplicationRef.get()
        if (exitApplication == null) {
            response.performQuit()
        } else {
            // Cancels the native quit because the shutdown this unblocks ends in exitProcess().
            exitApplication()
            response.cancelQuit()
        }
    }
}
