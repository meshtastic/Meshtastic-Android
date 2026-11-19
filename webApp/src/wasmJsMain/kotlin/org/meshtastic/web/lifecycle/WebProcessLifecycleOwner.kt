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
package org.meshtastic.web.lifecycle

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import kotlinx.browser.document

/**
 * The page's process lifecycle, following `document.visibilityState` the way Android's `ProcessLifecycleOwner` follows
 * the app moving to the background: [Lifecycle.State.RESUMED] while the tab is visible, [Lifecycle.State.CREATED] while
 * it is hidden (another tab in front, the window minimized, the screen locked). It lives as long as the page.
 */
internal class WebProcessLifecycleOwner : LifecycleOwner {
    private val registry = LifecycleRegistry(this)

    init {
        follow()
        document.addEventListener("visibilitychange", { follow() })
    }

    private fun follow() {
        registry.currentState = if (pageHidden()) Lifecycle.State.CREATED else Lifecycle.State.RESUMED
    }

    override val lifecycle: Lifecycle
        get() = registry
}

private fun pageHidden(): Boolean = js("document.hidden")
