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
package org.meshtastic.core.ui.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class AlertManagerTest {

    private val alertManager = AlertManager()

    @Test
    fun showAlert_updates_currentAlert_flow() {
        val title = "Test Title"
        val message = "Test Message"

        alertManager.showAlert(title = title, message = message)

        val alertData = alertManager.currentAlert.value
        assertNotNull(alertData)
        assertEquals(title, alertData.title)
        assertEquals(message, alertData.message)
    }

    @Test
    fun dismissAlert_clears_currentAlert_flow() {
        alertManager.showAlert(title = "Title")
        assertNotNull(alertManager.currentAlert.value)

        alertManager.dismissAlert()
        assertNull(alertManager.currentAlert.value)
    }

    @Test
    fun onConfirm_triggers_and_dismisses_alert() {
        var confirmClicked = false
        alertManager.showAlert(title = "Confirm Test", onConfirm = { confirmClicked = true })

        alertManager.currentAlert.value?.onConfirm?.invoke()

        assertEquals(true, confirmClicked)
        assertNull(alertManager.currentAlert.value)
    }

    @Test
    fun onDismiss_triggers_and_dismisses_alert() {
        var dismissClicked = false
        alertManager.showAlert(title = "Dismiss Test", onDismiss = { dismissClicked = true })

        alertManager.currentAlert.value?.onDismiss?.invoke()

        assertEquals(true, dismissClicked)
        assertNull(alertManager.currentAlert.value)
    }

    @Test
    fun showAlert_inside_onConfirm_is_dismissed_by_wrapping_dismissAlert() {
        // Documents the known race condition: AlertManager wraps onConfirm to call
        // dismissAlert() AFTER the user callback, so a showAlert() inside onConfirm
        // gets immediately cleared. Callers must defer via launch {} to work around this.
        alertManager.showAlert(
            title = "First",
            onConfirm = {
                // This simulates an error path where onConfirm shows a follow-up alert
                alertManager.showAlert(title = "Second", message = "Error details")
            },
        )

        // Trigger the wrapped onConfirm (user callback + dismissAlert)
        alertManager.currentAlert.value?.onConfirm?.invoke()

        // The second alert is wiped by dismissAlert() — currentAlert is null
        assertNull(
            alertManager.currentAlert.value,
            "showAlert inside onConfirm is cleared by the wrapping dismissAlert; callers must defer via launch {}",
        )
    }
}
