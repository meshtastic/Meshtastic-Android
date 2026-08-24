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
package org.meshtastic.core.testing

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.meshtastic.core.common.util.LocaleUnitsProvider
import org.meshtastic.core.common.util.MeasurementSystem
import org.meshtastic.core.common.util.TemperatureUnit

/**
 * A [LocaleUnitsProvider] whose values a test sets directly, so a consumer can be observed actually switching units
 * rather than merely subscribing to a change signal.
 */
class FakeLocaleUnitsProvider(
    system: MeasurementSystem = MeasurementSystem.METRIC,
    temperature: TemperatureUnit = TemperatureUnit.CELSIUS,
) : LocaleUnitsProvider {

    private val systemState = MutableStateFlow(system)
    private val temperatureState = MutableStateFlow(temperature)

    override val measurementSystem: Flow<MeasurementSystem> = systemState
    override val temperatureUnit: Flow<TemperatureUnit> = temperatureState

    /** Acts out the user changing their regional preferences mid-session. */
    fun set(system: MeasurementSystem = systemState.value, temperature: TemperatureUnit = temperatureState.value) {
        systemState.value = system
        temperatureState.value = temperature
    }
}
