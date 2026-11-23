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
import android.content.Intent
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.work.testing.WorkManagerTestInitHelper
import org.junit.After
import org.junit.runner.RunWith
import org.koin.androidx.viewmodel.ext.android.getViewModel
import org.meshtastic.core.model.util.primaryChannel
import org.meshtastic.core.ui.viewmodel.UIViewModel
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** Drives text shared into the app through MainActivity's own ACTION_SEND handling. */
@RunWith(RobolectricTestRunner::class)
@Config(application = InlineIoApplication::class, sdk = [34])
class SharedTextRoutingTest {

    @After
    fun tearDown() {
        WorkManagerTestInitHelper.closeWorkDatabase()
    }

    private fun share(text: String): MainActivity {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        val activity = Robolectric.buildActivity(MainActivity::class.java, intent).create().get()
        shadowOf(Looper.getMainLooper()).idle()
        return activity
    }

    private val app
        get() = shadowOf(ApplicationProvider.getApplicationContext<Application>())

    @Test
    fun `a shared channel link opens the channel import and sends no message`() {
        val activity = share("https://meshtastic.org/e/#CgMSAQESBggBQANIAQ")

        val requested = activity.getViewModel<UIViewModel>().requestChannelSet.value
        assertNotNull(requested)
        assertEquals("LongFast", requested.primaryChannel?.name)
        assertNull(app.nextStartedActivity)
    }

    @Test
    fun `other shared text opens the message composer and imports nothing`() {
        val activity = share("camp & ridge #4")

        assertNull(activity.getViewModel<UIViewModel>().requestChannelSet.value)
        val started = assertNotNull(app.nextStartedActivity)
        assertEquals(Intent.ACTION_VIEW, started.action)
        assertEquals("camp & ridge #4", started.data?.getQueryParameter("message"))
    }
}
