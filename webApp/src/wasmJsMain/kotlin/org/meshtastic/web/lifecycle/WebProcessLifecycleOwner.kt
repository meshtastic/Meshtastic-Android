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

/**
 * Synthetic [LifecycleOwner] that stays permanently in [Lifecycle.State.RESUMED] — a browser tab has no Android-style
 * process lifecycle to observe. Same "always RESUMED" shape as desktopApp's own `DesktopProcessLifecycleOwner`; a real
 * implementation could listen to the Page Visibility API (`document.visibilityState`) to move to STARTED when the tab
 * is backgrounded, but nothing in this v0 slice needs that distinction yet.
 */
private class WebProcessLifecycleOwner : LifecycleOwner {
    private val registry = LifecycleRegistry(this)

    init {
        registry.currentState = Lifecycle.State.RESUMED
    }

    override val lifecycle: Lifecycle
        get() = registry
}

/** The process-wide [Lifecycle], always [Lifecycle.State.RESUMED]. */
fun webProcessLifecycle(): Lifecycle = WebProcessLifecycleOwner().lifecycle
