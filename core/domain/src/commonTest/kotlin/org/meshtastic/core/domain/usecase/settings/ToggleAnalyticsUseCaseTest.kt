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
package org.meshtastic.core.domain.usecase.settings

import org.meshtastic.core.testing.FakeAnalyticsPrefs
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ToggleAnalyticsUseCaseTest {

    private lateinit var analyticsPrefs: FakeAnalyticsPrefs
    private lateinit var useCase: ToggleAnalyticsUseCase

    @BeforeTest
    fun setUp() {
        analyticsPrefs = FakeAnalyticsPrefs()
        useCase = ToggleAnalyticsUseCase(analyticsPrefs)
    }

    @Test
    fun `invoke toggles from false to true`() {
        analyticsPrefs.setAnalyticsAllowed(false)
        useCase()
        assertEquals(true, analyticsPrefs.analyticsAllowed.value)
    }

    @Test
    fun `invoke toggles from true to false`() {
        analyticsPrefs.setAnalyticsAllowed(true)
        useCase()
        assertEquals(false, analyticsPrefs.analyticsAllowed.value)
    }
}
