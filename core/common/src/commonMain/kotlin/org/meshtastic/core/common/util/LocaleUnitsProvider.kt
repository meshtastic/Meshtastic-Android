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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.shareIn
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
 * Both flows emit the current value on collection and again on every change.
 */
interface LocaleUnitsProvider {
    val measurementSystem: Flow<MeasurementSystem>
    val temperatureUnit: Flow<TemperatureUnit>
}

@Single
class LocaleUnitsProviderImpl(
    localeChangeNotifier: LocaleChangeNotifier,
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

    override val measurementSystem: Flow<MeasurementSystem> =
        reads.onStart { emit(Unit) }.map { getSystemMeasurementSystem() }.distinctUntilChanged()

    override val temperatureUnit: Flow<TemperatureUnit> =
        reads.onStart { emit(Unit) }.map { getSystemTemperatureUnit() }.distinctUntilChanged()
}
