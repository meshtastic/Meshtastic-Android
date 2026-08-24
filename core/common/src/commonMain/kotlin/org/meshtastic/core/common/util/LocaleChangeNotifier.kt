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
package org.meshtastic.core.common.util

import kotlinx.coroutines.flow.Flow

/**
 * Signals that the OS locale or its regional preferences changed, so anything holding a formatted value can re-read it.
 *
 * A ViewModel outlives the configuration change a locale switch triggers, so it never re-runs its initializers and a
 * snapshot taken at construction goes stale — the user flips their temperature preference and the node list keeps
 * showing Fahrenheit until the screen is rebuilt. Consumers therefore derive units from this flow rather than reading
 * them once.
 *
 * Emits only on change; collectors that need a current value start with their own read.
 */
interface LocaleChangeNotifier {
    val localeChanges: Flow<Unit>
}
