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
package org.meshtastic.feature.intro

import androidx.compose.runtime.staticCompositionLocalOf

/** Platform-agnostic permission state for the intro flow. */
interface IntroPermissionState {
    val isGranted: Boolean

    fun launchRequest()
}

/** Aggregated permission states needed by the intro onboarding flow. */
interface IntroPermissions {
    val bluetooth: IntroPermissionState
    val location: IntroPermissionState
    val notification: IntroPermissionState?
}

/** Provides platform-specific permission states to the intro nav graph. */
@Suppress("CompositionLocalAllowlist")
val LocalIntroPermissions = staticCompositionLocalOf<IntroPermissions> { error("IntroPermissions not provided") }
