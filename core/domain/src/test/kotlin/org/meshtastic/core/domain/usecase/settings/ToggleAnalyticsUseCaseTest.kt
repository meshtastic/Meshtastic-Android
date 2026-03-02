/*
 * Copyright (c) 2025 Meshtastic LLC
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

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Before
import org.junit.Test
import org.meshtastic.core.prefs.analytics.AnalyticsPrefs

class ToggleAnalyticsUseCaseTest {

    private lateinit var analyticsPrefs: AnalyticsPrefs
    private lateinit var useCase: ToggleAnalyticsUseCase

    @Before
    fun setUp() {
        analyticsPrefs = mockk(relaxed = true)
        useCase = ToggleAnalyticsUseCase(analyticsPrefs)
    }

    @Test
    fun `invoke toggles analytics from false to true`() {
        // Arrange
        every { analyticsPrefs.analyticsAllowed } returns false

        // Act
        useCase()

        // Assert
        verify { analyticsPrefs.analyticsAllowed = true }
    }

    @Test
    fun `invoke toggles analytics from true to false`() {
        // Arrange
        every { analyticsPrefs.analyticsAllowed } returns true

        // Act
        useCase()

        // Assert
        verify { analyticsPrefs.analyticsAllowed = false }
    }
}
