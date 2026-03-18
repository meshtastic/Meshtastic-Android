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
package org.meshtastic.core.service

import android.app.Application
import dev.mokkery.MockMode
import dev.mokkery.mock
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.meshtastic.core.repository.LocationRepository

class AndroidLocationServiceTest {
    @Test
    fun testInitialization() = runTest {
        val mockContext = mock<Application>(MockMode.autofill)
        val mockRepo = mock<LocationRepository>(MockMode.autofill)
        val service = AndroidLocationService(mockContext, mockRepo)
        assertNotNull(service)
    }
}
