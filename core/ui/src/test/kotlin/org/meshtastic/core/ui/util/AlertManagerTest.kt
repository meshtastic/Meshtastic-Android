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
package org.meshtastic.core.ui.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class AlertManagerTest {

    private val alertManager = AlertManager()

    @Test
    fun `showAlert updates currentAlert flow`() {
        val title = "Test Title"
        val message = "Test Message"
        
        alertManager.showAlert(title = title, message = message)
        
        val alertData = alertManager.currentAlert.value
        assertNotNull(alertData)
        assertEquals(title, alertData?.title)
        assertEquals(message, alertData?.message)
    }

    @Test
    fun `dismissAlert clears currentAlert flow`() {
        alertManager.showAlert(title = "Title")
        assertNotNull(alertManager.currentAlert.value)
        
        alertManager.dismissAlert()
        assertNull(alertManager.currentAlert.value)
    }

    @Test
    fun `onConfirm triggers and dismisses alert`() {
        var confirmClicked = false
        alertManager.showAlert(
            title = "Confirm Test",
            onConfirm = { confirmClicked = true }
        )
        
        alertManager.currentAlert.value?.onConfirm?.invoke()
        
        assertEquals(true, confirmClicked)
        assertNull(alertManager.currentAlert.value)
    }

    @Test
    fun `onDismiss triggers and dismisses alert`() {
        var dismissClicked = false
        alertManager.showAlert(
            title = "Dismiss Test",
            onDismiss = { dismissClicked = true }
        )
        
        alertManager.currentAlert.value?.onDismiss?.invoke()
        
        assertEquals(true, dismissClicked)
        assertNull(alertManager.currentAlert.value)
    }
}
