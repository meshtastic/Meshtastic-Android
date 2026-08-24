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

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.meshtastic.core.common.di.ApplicationCoroutineScope
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale
import kotlin.test.assertEquals

/**
 * Covers the provider itself. Its consumers are tested against a fake of it, so without this the priming and
 * de-duplication that make those consumers correct would be asserted nowhere.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LocaleUnitsProviderImplTest {

    private val changes = MutableSharedFlow<Unit>()
    private lateinit var originalLocale: Locale

    /**
     * The provider's `shareIn` launches into whatever scope it is handed. Handing it the test's own scope would leave
     * that coroutine running when the test body ends, and `runTest` fails rather than returns — hence
     * `backgroundScope`, which is cancelled for us at the end of the test.
     */
    private class TestScope(scope: CoroutineScope) :
        ApplicationCoroutineScope,
        CoroutineScope by scope

    private val notifier =
        object : LocaleChangeNotifier {
            override val localeChanges: Flow<Unit> = changes
        }

    @Before
    fun setUp() {
        originalLocale = Locale.getDefault()
    }

    @After
    fun tearDown() {
        Locale.setDefault(originalLocale)
    }

    /** Without the priming emission a screen would show no units until the user next changed their locale. */
    @Test
    fun `emits the current value before any change arrives`() = runTest(UnconfinedTestDispatcher()) {
        Locale.setDefault(Locale.US)
        val provider = LocaleUnitsProviderImpl(notifier, TestScope(backgroundScope))

        assertEquals(MeasurementSystem.IMPERIAL, provider.measurementSystem.first())
        assertEquals(TemperatureUnit.FAHRENHEIT, provider.temperatureUnit.first())
    }

    @Test
    fun `re-reads the locale when a change arrives`() = runTest(UnconfinedTestDispatcher()) {
        Locale.setDefault(Locale.US)
        val provider = LocaleUnitsProviderImpl(notifier, TestScope(backgroundScope))

        val seen = mutableListOf<MeasurementSystem>()
        val collector = launch { provider.measurementSystem.take(2).toList(seen) }

        Locale.setDefault(Locale.GERMANY)
        changes.emit(Unit)
        collector.join()

        assertEquals(listOf(MeasurementSystem.IMPERIAL, MeasurementSystem.METRIC), seen)
    }

    /** A locale change that does not move the needle must not churn every observing screen. */
    @Test
    fun `a change that leaves the units alone emits nothing further`() = runTest(UnconfinedTestDispatcher()) {
        Locale.setDefault(Locale.US)
        val provider = LocaleUnitsProviderImpl(notifier, TestScope(backgroundScope))

        val seen = mutableListOf<MeasurementSystem>()
        val collector = launch { provider.measurementSystem.toList(seen) }

        changes.emit(Unit)

        assertEquals(listOf(MeasurementSystem.IMPERIAL), seen)
        collector.cancel()
    }
}
