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

import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify
import org.meshtastic.core.common.UiPreferences
import kotlin.test.BeforeTest
import kotlin.test.Test

class SetLocaleUseCaseTest {

    private val uiPreferences: UiPreferences = mock()
    private lateinit var useCase: SetLocaleUseCase

    @BeforeTest
    fun setUp() {
        useCase = SetLocaleUseCase(uiPreferences)
    }

    @Test
    fun `invoke calls setLocale on uiPreferences`() {
        every { uiPreferences.setLocale(any()) } returns Unit
        useCase("en")
        verify { uiPreferences.setLocale("en") }
    }
}
