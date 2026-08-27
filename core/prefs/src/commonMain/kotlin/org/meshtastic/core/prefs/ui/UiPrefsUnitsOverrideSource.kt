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
package org.meshtastic.core.prefs.ui

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.koin.core.annotation.Single
import org.meshtastic.core.common.util.UnitsOverride
import org.meshtastic.core.common.util.UnitsOverrideSource
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.repository.UiPrefs

/**
 * Adapts the stored units preference to the [UnitsOverrideSource] that `core:common`'s unit provider consumes.
 *
 * The indirection exists for the dependency direction: the provider lives in `core:common`, which the preferences
 * modules depend on, so the provider cannot see [UiPrefs] itself.
 */
@Single
class UiPrefsUnitsOverrideSource(uiPrefs: UiPrefs, dispatchers: CoroutineDispatchers) : UnitsOverrideSource {
    private val scope = CoroutineScope(SupervisorJob() + dispatchers.default)

    override val override: StateFlow<UnitsOverride> =
        uiPrefs.unitsOverride
            .map { UnitsOverride.fromValue(it) }
            .stateIn(scope, SharingStarted.Eagerly, UnitsOverride.fromValue(uiPrefs.unitsOverride.value))
}
