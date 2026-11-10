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
package org.meshtastic.feature.settings.radio.component

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MqttRootSaveTest {
    @Test
    fun anEmptyRootIsAcceptedAsTheFirmwareDefault() {
        assertTrue(mqttRootAccepted(""))
    }

    @Test
    fun aValidRootIsAccepted() {
        assertTrue(mqttRootAccepted("msh/US/WA"))
    }

    @Test
    fun aWildcardRootIsRejected() {
        assertFalse(mqttRootAccepted("msh/US/#"))
        assertFalse(mqttRootAccepted("msh/+/WA"))
    }

    @Test
    fun aMalformedRootIsRejected() {
        assertFalse(mqttRootAccepted("/msh/US"))
        assertFalse(mqttRootAccepted("msh/US/"))
        assertFalse(mqttRootAccepted("msh/US/2"))
    }

    @Test
    fun aRejectedRootBlocksSave() {
        assertFalse(mqttSaveEnabled(editable = true, root = "msh/US/#"))
    }

    @Test
    fun anAcceptedRootSavesOnlyWhenEditable() {
        assertTrue(mqttSaveEnabled(editable = true, root = "msh/US/WA"))
        assertTrue(mqttSaveEnabled(editable = true, root = ""))
        assertFalse(mqttSaveEnabled(editable = false, root = "msh/US/WA"))
    }
}
