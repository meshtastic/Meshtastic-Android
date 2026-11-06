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
package org.meshtastic.feature.widget

import android.app.Application
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.RemoteViews
import android.widget.TextView
import androidx.core.view.children
import androidx.glance.appwidget.composeForPreview
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.meshtastic.core.testing.FakeNodeRepository
import org.meshtastic.core.testing.FakeServiceRepository
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertContains

/** Composes the widget-picker preview the way `GlanceAppWidgetManager.setWidgetPreviews` does. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class WidgetPreviewTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @After
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun `the node tracker picker preview composes without a glance id`() = runTest {
        val views = NodeTrackerWidget().composeForPreview(context, AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN)
        assertContains(texts(views), "BCS1")
    }

    @Test
    fun `the local stats picker preview composes without a glance id`() = runTest {
        startKoin {
            modules(module { single { LocalStatsWidgetStateProvider(FakeNodeRepository(), FakeServiceRepository()) } })
        }
        val views = LocalStatsWidget().composeForPreview(context, AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN)
        assertContains(texts(views), "ME")
    }

    /** Inflates [views] and returns every string a TextView in it shows, so a test sees the mock state rendered. */
    private fun texts(views: RemoteViews): List<String> {
        val root = views.apply(context, FrameLayout(context))
        return generateSequence(listOf(root)) { level -> level.filterIsInstance<ViewGroup>().flatMap { it.children } }
            .takeWhile { it.isNotEmpty() }
            .flatten()
            .filterIsInstance<TextView>()
            .map { it.text.toString() }
            .toList()
    }
}
