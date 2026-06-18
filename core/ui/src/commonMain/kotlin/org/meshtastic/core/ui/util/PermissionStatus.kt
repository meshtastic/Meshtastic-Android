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
package org.meshtastic.core.ui.util

/**
 * The UX-relevant state of a runtime permission, as recommended by the Android permissions guidance
 * (https://developer.android.com/training/permissions/requesting).
 *
 * - [GRANTED] — the permission is held; proceed.
 * - [NOT_REQUESTED] — the user has never been prompted; request directly (no rationale needed yet).
 * - [DENIED_CAN_RETRY] — the user denied once but the system will still show the dialog; show a
 *   rationale and offer to re-request.
 * - [PERMANENTLY_DENIED] — the system will no longer show the dialog ("Don't allow" twice, or
 *   "Don't ask again"); the only recovery is the app's settings screen.
 */
enum class PermissionStatus {
    GRANTED,
    NOT_REQUESTED,
    DENIED_CAN_RETRY,
    PERMANENTLY_DENIED,
}

/**
 * Pure classifier for a runtime permission's UX state. Kept platform-agnostic and side-effect-free so it can be
 * unit-tested in `commonTest` without an Android `Activity`.
 *
 * **Invariant:** [hasRequested] MUST reflect a *completed* request — it should be persisted from the launcher's
 * result callback, never merely when `launch()` is invoked. On Android, `launch()` does not show a dialog once a
 * permission is permanently denied, and a user can background the app before a dialog resolves; setting the flag
 * pre-emptively would misclassify a first-run user as [PERMANENTLY_DENIED].
 *
 * Note that [shouldShowRationale] is `false` both *before* the first prompt and *after* a permanent denial — which
 * is exactly why [hasRequested] is required to disambiguate the two cases.
 */
fun computePermissionStatus(granted: Boolean, hasRequested: Boolean, shouldShowRationale: Boolean): PermissionStatus =
    when {
        granted -> PermissionStatus.GRANTED
        !hasRequested -> PermissionStatus.NOT_REQUESTED
        shouldShowRationale -> PermissionStatus.DENIED_CAN_RETRY
        else -> PermissionStatus.PERMANENTLY_DENIED
    }

/**
 * A reactive snapshot of a runtime permission plus the actions a caller can take. Produced by the
 * `rememberXxxPermissionState()` composables and recomputed on `ON_RESUME` so it stays fresh when the user returns
 * from a permission dialog or the system settings screen.
 */
data class PermissionUiState(val status: PermissionStatus, val request: () -> Unit, val openAppSettings: () -> Unit) {
    val isGranted: Boolean
        get() = status == PermissionStatus.GRANTED
}
