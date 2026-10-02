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
package org.meshtastic.desktop.tray

import org.meshtastic.desktop.notification.DesktopOS

/**
 * Whether closing the window may hide the app into the system tray instead of quitting.
 *
 * AWT's `SystemTray.isSupported()` — which Compose surfaces as `isTraySupported` — answers only whether a tray
 * *exists*, never whether the user can interact with the icon placed in it. On Linux those differ. Both GNOME and KDE
 * Plasma dropped the XEmbed system tray that AWT implements, and surface AWT's icon through an
 * XEmbed-to-StatusNotifierItem proxy (`xembedsniproxy` under Plasma). The proxy renders the icon but does not deliver
 * clicks back to AWT's `TrayIcon`, so neither the activation action nor the popup menu ever fires.
 *
 * Hiding into a tray like that strands the process with no reachable way to quit: no window to close, no tray menu to
 * quit from, and no keyboard shortcut either, because a hidden window stops receiving key events. So on Linux the close
 * button quits rather than hiding, which is what the no-tray branch already did for the same reason.
 *
 * [os] is injectable for tests; production callers take the host's value.
 */
internal fun canHideToTray(awtTraySupported: Boolean, os: DesktopOS = DesktopOS.current()): Boolean =
    awtTraySupported && os != DesktopOS.Linux
