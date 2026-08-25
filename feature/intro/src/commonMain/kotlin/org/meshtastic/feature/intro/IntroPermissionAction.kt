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

import org.meshtastic.core.ui.util.PermissionStatus

/** What an intro permission screen's primary button should do, given the live permission status. */
enum class IntroPermissionAction {
    /** The permission is held; move on to the next screen. */
    ADVANCE,

    /** The system will still show its dialog; launch the request. */
    REQUEST,

    /** The system will no longer show a dialog; the only recovery left is the app's settings page. */
    OPEN_SETTINGS,
}

/**
 * Maps a [PermissionStatus] to the primary action for an intro permission screen.
 *
 * Extracted as a pure function so the mapping is unit-testable without a composition or an `Activity`. The case that
 * matters is [PermissionStatus.PERMANENTLY_DENIED]: an earlier version of the intro flow branched on a
 * granted/not-granted boolean and called `request()` here, which Android answers with an immediate denial and no dialog
 * — leaving the screen's only visible action doing nothing at all.
 */
fun introPermissionAction(status: PermissionStatus): IntroPermissionAction = when (status) {
    PermissionStatus.GRANTED -> IntroPermissionAction.ADVANCE

    PermissionStatus.PERMANENTLY_DENIED -> IntroPermissionAction.OPEN_SETTINGS

    PermissionStatus.NOT_REQUESTED,
    PermissionStatus.DENIED_CAN_RETRY,
    -> IntroPermissionAction.REQUEST
}
