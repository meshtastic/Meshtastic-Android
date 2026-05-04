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

import androidx.compose.runtime.compositionLocalOf
import org.meshtastic.core.model.EventEdition

/**
 * Provides the active [EventEdition] (if any) to the composition tree. When a connected device reports an event
 * firmware edition, this local is populated at the app root so that
 * [MainAppBar][org.meshtastic.core.ui.component.MainAppBar] can display event branding automatically — no per-screen
 * wiring needed.
 */
@Suppress("CompositionLocalAllowlist")
val LocalEventBranding = compositionLocalOf<EventEdition?> { null }
