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

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.meshtastic.core.repository.Location
import org.meshtastic.core.repository.LocationRepository
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AndroidLocationServiceTest {
    @Test
    fun testInitialization() = runTest {
        val context = RuntimeEnvironment.getApplication()
        val service = AndroidLocationService(context, FakeLocationRepository())
        assertNotNull(service)
    }

    private class FakeLocationRepository : LocationRepository {
        override val receivingLocationUpdates = MutableStateFlow(false)

        override fun getLocations() = emptyFlow<Location>()
    }
}
