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

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pins that the merged google-flavor manifest declares no Android Auto support. Play puts every track through the car
 * quality review on the `com.google.android.gms.car.application` entry alone, and it does not accept Meshtastic in an
 * Auto category, so any declaration gets the update rejected.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class AndroidAutoManifestTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `the app declares no Android Auto support`() {
        val info = context.packageManager.getApplicationInfo(context.packageName, PackageManager.GET_META_DATA)
        assertFalse(info.metaData?.containsKey(CAR_APPLICATION) == true, "$CAR_APPLICATION puts the app in Auto review")
    }

    @Test
    fun `no Android for Cars App Library service is declared`() {
        val carAppServices = context.packageManager.queryIntentServices(Intent(CAR_APP_SERVICE), 0)
        assertTrue(carAppServices.isEmpty(), "a car app service puts the app in Auto review")
    }

    @Test
    fun `the conversation action service is declared and not exported`() {
        val service =
            context.packageManager.getServiceInfo(
                ComponentName(context, "org.meshtastic.core.service.ConversationActionService"),
                0,
            )
        assertFalse(service.exported, "only our own notification actions may start it")
    }

    private companion object {
        const val CAR_APPLICATION = "com.google.android.gms.car.application"
        const val CAR_APP_SERVICE = "androidx.car.app.CarAppService"
    }
}
