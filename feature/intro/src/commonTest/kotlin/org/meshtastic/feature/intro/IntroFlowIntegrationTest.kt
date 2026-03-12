/*
 * Copyright (c) 2025-2026 Meshtastic LLC
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
package org.meshtastic.feature.intro

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Integration tests for intro feature.
 *
 * Tests the complete onboarding flow and navigation logic.
 */
class IntroFlowIntegrationTest {

    private val viewModel = IntroViewModel()

    @Test
    fun testCompleteIntroFlowWithAllPermissions() {
        // Start at Welcome
        var nextKey = viewModel.getNextKey(Welcome, allPermissionsGranted = false)
        assertEquals(Bluetooth, nextKey)

        // Bluetooth -> Location
        nextKey = viewModel.getNextKey(Bluetooth, allPermissionsGranted = false)
        assertEquals(Location, nextKey)

        // Location -> Notifications
        nextKey = viewModel.getNextKey(Location, allPermissionsGranted = false)
        assertEquals(Notifications, nextKey)

        // Notifications -> CriticalAlerts (with all permissions)
        nextKey = viewModel.getNextKey(Notifications, allPermissionsGranted = true)
        assertEquals(CriticalAlerts, nextKey)

        // CriticalAlerts -> null (end)
        nextKey = viewModel.getNextKey(CriticalAlerts, allPermissionsGranted = true)
        assertNull(nextKey)
    }

    @Test
    fun testIntroFlowWithoutAllPermissions() {
        var nextKey = viewModel.getNextKey(Welcome, allPermissionsGranted = false)
        assertEquals(Bluetooth, nextKey)

        nextKey = viewModel.getNextKey(Bluetooth, allPermissionsGranted = false)
        assertEquals(Location, nextKey)

        nextKey = viewModel.getNextKey(Location, allPermissionsGranted = false)
        assertEquals(Notifications, nextKey)

        // Without all permissions, should end
        nextKey = viewModel.getNextKey(Notifications, allPermissionsGranted = false)
        assertNull(nextKey)
    }

    @Test
    fun testEachScreenNavigation() {
        // Welcome navigation
        assertEquals(Bluetooth, viewModel.getNextKey(Welcome, false))
        assertEquals(Bluetooth, viewModel.getNextKey(Welcome, true))

        // Bluetooth navigation (doesn't change based on permissions)
        assertEquals(Location, viewModel.getNextKey(Bluetooth, false))
        assertEquals(Location, viewModel.getNextKey(Bluetooth, true))

        // Location navigation (doesn't change based on permissions)
        assertEquals(Notifications, viewModel.getNextKey(Location, false))
        assertEquals(Notifications, viewModel.getNextKey(Location, true))
    }

    @Test
    fun testNotificationsScreenPermissionDependency() {
        // Notifications response depends on permissions
        assertNull(viewModel.getNextKey(Notifications, allPermissionsGranted = false))
        assertEquals(CriticalAlerts, viewModel.getNextKey(Notifications, allPermissionsGranted = true))
    }

    @Test
    fun testInvalidKeyHandling() {
        // Invalid key should return null
        val invalidKey = object : androidx.navigation3.runtime.NavKey {}
        val result = viewModel.getNextKey(invalidKey, allPermissionsGranted = false)
        assertNull(result)
    }

    @Test
    fun testCriticalAlertsIsTerminal() {
        // CriticalAlerts should always be terminal
        assertNull(viewModel.getNextKey(CriticalAlerts, allPermissionsGranted = false))
        assertNull(viewModel.getNextKey(CriticalAlerts, allPermissionsGranted = true))
    }

    @Test
    fun testPermissionProgressTracking() {
        // Simulate progressing through intro with permission grants
        var key = Welcome as androidx.navigation3.runtime.NavKey
        var progressCount = 0

        // Progress without all permissions first
        key = viewModel.getNextKey(key, allPermissionsGranted = false) ?: return
        progressCount++
        assertEquals(1, progressCount)

        key = viewModel.getNextKey(key, allPermissionsGranted = false) ?: return
        progressCount++
        assertEquals(2, progressCount)

        key = viewModel.getNextKey(key, allPermissionsGranted = false) ?: return
        progressCount++
        assertEquals(3, progressCount)

        // Should stop here without full permissions
        val nextAfterNotifications = viewModel.getNextKey(key, allPermissionsGranted = false)
        assertNull(nextAfterNotifications)
    }

    @Test
    fun testAlternativePath() {
        // Test that permissions can change response at notifications
        val notificationsWithoutPermissions = viewModel.getNextKey(Notifications, false)
        val notificationsWithPermissions = viewModel.getNextKey(Notifications, true)

        assertNull(notificationsWithoutPermissions)
        assertEquals(CriticalAlerts, notificationsWithPermissions)
    }
}
