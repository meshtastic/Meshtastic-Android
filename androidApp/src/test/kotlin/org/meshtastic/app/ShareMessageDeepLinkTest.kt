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

import android.app.PendingIntent
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.runner.RunWith
import org.meshtastic.core.common.util.CommonUri
import org.meshtastic.core.navigation.ContactsRoute
import org.meshtastic.core.navigation.DeepLinkRouter
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ShareMessageDeepLinkTest {

    @After
    fun tearDown() {
        // No `application =` override, so Robolectric boots the manifest's real MeshUtilApplication and its
        // background init; stop it here or its failures land on whichever test runs next in this JVM.
        ApplicationProvider.getApplicationContext<MeshUtilApplication>().cancelBackgroundInit()
    }

    @Test
    fun `shared text round trips through the deep link query`() {
        val messages =
            listOf(
                "camp & ridge #4",
                "question? answer=yes + backup",
                "line one\nline two",
                "emoji 🦙 and non-Latin 北山",
            )

        messages.forEach { message ->
            val deepLink = createShareMessageDeepLink(message)

            assertEquals(message, deepLink.getQueryParameter("message"))
            assertEquals(setOf("message"), deepLink.queryParameterNames)
            assertNull(deepLink.fragment)
            assertEquals(
                listOf(ContactsRoute.Contacts, ContactsRoute.Share(message)),
                DeepLinkRouter.route(CommonUri.parse(deepLink.toString())),
            )
        }
    }

    @Test
    fun `share callback pending intent carries the encoded message deep link`() {
        val message = "camp & ridge #4? answer=yes + backup"
        val activity = Robolectric.buildActivity(MainActivity::class.java).get()

        val pendingIntent =
            ReflectionHelpers.callInstanceMethod<PendingIntent>(
                activity,
                "createShareIntent",
                ReflectionHelpers.ClassParameter.from(String::class.java, message),
            )
        val callbackIntent = shadowOf(pendingIntent).savedIntent

        assertEquals(Intent.ACTION_VIEW, callbackIntent.action)
        assertEquals(MainActivity::class.java.name, callbackIntent.component?.className)
        assertEquals(Intent.FLAG_ACTIVITY_SINGLE_TOP, callbackIntent.flags and Intent.FLAG_ACTIVITY_SINGLE_TOP)
        assertEquals(message, callbackIntent.data?.getQueryParameter("message"))
        assertEquals(setOf("message"), callbackIntent.data?.queryParameterNames)
        assertNull(callbackIntent.data?.fragment)
        assertEquals(
            listOf(ContactsRoute.Contacts, ContactsRoute.Share(message)),
            DeepLinkRouter.route(CommonUri.parse(callbackIntent.data.toString())),
        )
    }
}
