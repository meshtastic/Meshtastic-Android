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
package org.meshtastic.core.common.di

// The two bindings that cannot be told apart by type: `Lifecycle` is an abstract class, so it cannot be wrapped by
// delegation, and the flavor flag is a `Boolean`. Everything else uses a distinct type instead of a qualifier — see
// [ServiceScope]. Reference the constant, since a misspelled literal resolves to nothing and fails at runtime.

/** `androidx.lifecycle.Lifecycle` of the whole process, not of any single Activity. */
const val PROCESS_LIFECYCLE = "ProcessLifecycle"

/** Whether Google Play services are present — `true` in the google flavor, `false` in fdroid. */
const val GOOGLE_SERVICES_AVAILABLE = "googleServicesAvailable"
