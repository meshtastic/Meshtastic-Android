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

import org.meshtastic.core.repository.AppIconBadge

/**
 * The count on the installed app's icon through the Badging API. Browsers without it, and a page that is not installed,
 * show nothing.
 */
class WebAppIconBadge : AppIconBadge {
    override fun show(count: Int) = setAppBadge(count)
}

@Suppress("UnusedParameter") // read by name inside the js() body
private fun setAppBadge(count: Int): Unit =
    js(
        """{
        if (!('setAppBadge' in navigator)) return;
        (count > 0 ? navigator.setAppBadge(count) : navigator.clearAppBadge()).catch(() => {});
    }""",
    )
