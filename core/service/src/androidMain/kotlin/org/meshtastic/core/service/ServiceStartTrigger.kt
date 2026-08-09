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
package org.meshtastic.core.service

/**
 * Why [MeshService] is being asked to start.
 *
 * Android 12+ only permits `startForegroundService()` from an allowlisted context. The trigger identifies which
 * allowlist entry (if any) applies, so [ForegroundStartPolicy] can refuse a start that the platform would reject
 * instead of letting it throw.
 *
 * See
 * [the background-start exemption list](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start).
 */
enum class ServiceStartTrigger {
    /**
     * An activity is entering the started state (`MainActivity.onStart()`). The app is transitioning from a
     * user-visible state, which is the primary documented exemption.
     */
    UserInterface,

    /**
     * The selected radio changed. Normally the result of the user picking a device in the UI, but the transition
     * completes asynchronously and may land after the app has been backgrounded, at which point no exemption applies.
     */
    DeviceAddressChanged,

    /**
     * `ACTION_BOOT_COMPLETED` or `ACTION_MY_PACKAGE_REPLACED`. Both are explicitly exempt from the background-start
     * restriction.
     */
    BootCompleted,
}
