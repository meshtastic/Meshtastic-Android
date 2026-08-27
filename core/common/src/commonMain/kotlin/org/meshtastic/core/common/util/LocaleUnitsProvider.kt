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

import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import org.koin.core.annotation.Single
import org.meshtastic.core.common.di.ApplicationCoroutineScope

/**
 * The display units, as values that change over time.
 *
 * Consumers depend on this rather than on [LocaleChangeNotifier] directly. The notifier only says *that* something
 * changed, which left every consumer to write the same "prime, then re-read" derivation by hand — and a consumer that
 * forgot the priming step, or was never given the notifier at all, silently kept stale units. Owning the read in one
 * place also makes the value injectable, so a test can flip metric to imperial instead of asserting on plumbing.
 *
 * The user's in-app units choice ([UnitsOverride]) is folded in here, so every consumer honors it by construction — a
 * call site that read the OS directly would follow the locale but ignore the setting, splitting the app the way mixed
 * unit sources always have.
 *
 * Both values are [StateFlow]s: they emit the current value on collection, again on every change, and expose a
 * synchronous `.value` for the few call sites that format outside a collector.
 */
interface LocaleUnitsProvider {
    val measurementSystem: StateFlow<MeasurementSystem>
    val temperatureUnit: StateFlow<TemperatureUnit>
}

@Single
class LocaleUnitsProviderImpl(
    localeChangeNotifier: LocaleChangeNotifier,
    overrideSource: UnitsOverrideSource,
    applicationCoroutineScope: ApplicationCoroutineScope,
) : LocaleUnitsProvider {

    /**
     * Shared, so the OS-wide change signal is subscribed to once however many screens are observing units. Left cold it
     * would register a fresh platform listener per collector and re-register on every screen re-entry.
     */
    private val reads =
        localeChangeNotifier.localeChanges.shareIn(
            scope = applicationCoroutineScope,
            started = SharingStarted.WhileSubscribed(),
            replay = 0,
        )

    override val measurementSystem: StateFlow<MeasurementSystem> =
        combine(
            reads.onStart { emit(Unit) }.map { getSystemMeasurementSystem() }.distinctUntilChanged(),
            overrideSource.override,
        ) { os, override ->
            when (override) {
                UnitsOverride.SYSTEM -> os
                UnitsOverride.METRIC -> MeasurementSystem.METRIC
                UnitsOverride.IMPERIAL -> MeasurementSystem.IMPERIAL
            }
        }
            .stateIn(
                scope = applicationCoroutineScope,
                started = SharingStarted.Eagerly,
                initialValue = initialMeasurementSystem(overrideSource.override.value),
            )

    /**
     * A forced system carries its temperature with it (metric → °C, imperial → °F), overriding even an explicit OS
     * regional temperature preference — a user who forces one system wants one system, not a blend. [SYSTEM] keeps the
     * OS resolution, where distance and temperature are deliberately decoupled (see [TemperatureUnit]).
     */
    override val temperatureUnit: StateFlow<TemperatureUnit> =
        combine(
            reads.onStart { emit(Unit) }.map { getSystemTemperatureUnit() }.distinctUntilChanged(),
            overrideSource.override,
        ) { os, override ->
            when (override) {
                UnitsOverride.SYSTEM -> os
                UnitsOverride.METRIC -> TemperatureUnit.CELSIUS
                UnitsOverride.IMPERIAL -> TemperatureUnit.FAHRENHEIT
            }
        }
            .stateIn(
                scope = applicationCoroutineScope,
                started = SharingStarted.Eagerly,
                initialValue = initialTemperatureUnit(overrideSource.override.value),
            )

    private fun initialMeasurementSystem(override: UnitsOverride): MeasurementSystem = when (override) {
        UnitsOverride.SYSTEM -> getSystemMeasurementSystem()
        UnitsOverride.METRIC -> MeasurementSystem.METRIC
        UnitsOverride.IMPERIAL -> MeasurementSystem.IMPERIAL
    }

    private fun initialTemperatureUnit(override: UnitsOverride): TemperatureUnit = when (override) {
        UnitsOverride.SYSTEM -> getSystemTemperatureUnit()
        UnitsOverride.METRIC -> TemperatureUnit.CELSIUS
        UnitsOverride.IMPERIAL -> TemperatureUnit.FAHRENHEIT
    }
}
