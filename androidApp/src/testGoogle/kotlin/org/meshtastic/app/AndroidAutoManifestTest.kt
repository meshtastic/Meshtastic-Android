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
import org.xmlpull.v1.XmlPullParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Pins what the merged google-flavor manifest tells Google Play about Android Auto. Play reviews the app against the
 * car quality guidelines from this declaration alone, and it rejected Open and Production builds that also declared an
 * androidx.car.app service ("Category not permitted"), so notification messaging must stay the only thing declared.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class AndroidAutoManifestTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `the app declares Android Auto notification messaging and nothing else`() {
        val info = context.packageManager.getApplicationInfo(context.packageName, PackageManager.GET_META_DATA)
        val descriptor = info.metaData.getInt(CAR_APPLICATION)
        assertNotEquals(0, descriptor, "$CAR_APPLICATION must point at @xml/automotive_app_desc")

        var root: String? = null
        val uses = mutableListOf<String?>()
        val parser = context.resources.getXml(descriptor)
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType != XmlPullParser.START_TAG) continue
            when (parser.depth) {
                1 -> root = parser.name
                2 -> if (parser.name == "uses") uses += parser.getAttributeValue(null, "name")
            }
        }
        assertEquals("automotiveApp", root)
        assertEquals(listOf<String?>("notification"), uses)
    }

    @Test
    fun `no Android for Cars App Library service is declared`() {
        val carAppServices = context.packageManager.queryIntentServices(Intent(CAR_APP_SERVICE), 0)
        assertTrue(carAppServices.isEmpty(), "a templated car app service is Closed-track only and fails Play review")
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
