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
package org.meshtastic.app

import androidx.test.core.app.ApplicationProvider
import org.junit.runner.RunWith
import org.osmdroid.config.Configuration
import org.osmdroid.views.MapView
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals

class FlavorApplicationConfigurationTest {
    @Test
    fun `configureFlavorApplication helper sets osmdroid user agent`() {
        val configuration = Configuration.getInstance()
        val original = configuration.userAgentValue
        try {
            configureFlavorApplication("test.user.agent")
            assertEquals("test.user.agent", configuration.userAgentValue)
        } finally {
            configuration.userAgentValue = original
        }
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(application = MeshUtilApplication::class, sdk = [34])
class FlavorApplicationStartupTest {
    @Test
    fun `production startup configures osmdroid before first map creation`() {
        val configuration = Configuration.getInstance()
        val original = configuration.userAgentValue
        try {
            val application = ApplicationProvider.getApplicationContext<MeshUtilApplication>()

            assertEquals(application.packageName, BuildConfig.APPLICATION_ID)
            assertEquals(BuildConfig.APPLICATION_ID, configuration.userAgentValue)
            MapView(application).let { mapView ->
                try {
                    assertEquals(BuildConfig.APPLICATION_ID, configuration.userAgentValue)
                } finally {
                    mapView.onDetach()
                }
            }
        } finally {
            configuration.userAgentValue = original
        }
    }
}
