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
import android.content.Context
import androidx.compose.ui.unit.DpSize
import androidx.glance.appwidget.testing.unit.GlanceAppWidgetUnitTest
import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.unit.hasContentDescriptionEqualTo
import androidx.glance.testing.unit.hasText
import androidx.test.core.app.ApplicationProvider
import org.junit.runner.RunWith
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.disconnected
import org.meshtastic.core.resources.free_memory
import org.meshtastic.core.resources.getString
import org.meshtastic.core.resources.meshtastic_app_name
import org.meshtastic.core.resources.refresh
import org.meshtastic.core.resources.widget_traffic
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds

/**
 * Unit tests for [LocalStatsWidget.WidgetContent] using Glance's own test harness.
 *
 * The harness does not inflate views, so these assert on the composed Glance tree rather than pixels. Robolectric is
 * required because the content resolves Compose Multiplatform string resources, which needs a real [Context].
 *
 * The load-bearing assertion across all of these is that *any* string renders at all: every one goes through the
 * [WidgetResourceEnvironment] bridge, which hand-provides the `androidx.compose.ui` composition locals that Glance
 * omits. If that bridge breaks — most likely because Compose Multiplatform starts reading another local — resolution
 * throws and these tests fail here instead of shipping a blank widget.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class LocalStatsWidgetContentTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    // Resolved here, at construction, rather than inside the test bodies: getString() wraps runBlocking, and
    // runGlanceAppWidgetUnitTest runs its block inside runTest, where a nested runBlocking risks deadlocking.
    private val appName: String = getString(Res.string.meshtastic_app_name)
    private val disconnectedLabel: String = getString(Res.string.disconnected)
    private val refreshLabel: String = getString(Res.string.refresh)
    private val trafficLabel: String = getString(Res.string.widget_traffic)
    private val freeMemoryLabel: String = getString(Res.string.free_memory)

    private val disconnected = LocalStatsWidgetUiState(connectionState = ConnectionState.Disconnected)

    private fun render(
        size: DpSize,
        state: LocalStatsWidgetUiState = createMockWidgetState(),
        assertions: GlanceAppWidgetUnitTest.() -> Unit,
    ) = runGlanceAppWidgetUnitTest(timeout = TEST_TIMEOUT) {
        setAppWidgetSize(size)
        setContext(context)
        provideComposable { LocalStatsWidget().WidgetContent(state) }
        assertions()
    }

    @Test
    fun `wide two-row layout carries the brand header refresh identity and traffic`() = render(WidgetSizes.BIG) {
        onNode(hasText(appName)).assertExists()
        onNode(hasContentDescriptionEqualTo(refreshLabel)).assertExists()
        onNode(hasText("ME")).assertExists()
        onNode(hasText("85%")).assertExists()
        onNode(hasText("2/3")).assertExists()
        onNode(hasText(trafficLabel)).assertExists()
        onNode(hasText("145 / 892")).assertExists()
    }

    @Test
    fun `narrow two-row layout keeps the brand header but not refresh or traffic`() = render(WidgetSizes.TALL) {
        onNode(hasText(appName)).assertExists()
        onNode(hasContentDescriptionEqualTo(refreshLabel)).assertDoesNotExist()
        onNode(hasText("18.5%")).assertExists()
        onNode(hasText("3.2%")).assertExists()
        onNode(hasText("2/3")).assertExists()
        onNode(hasText(trafficLabel)).assertDoesNotExist()
    }

    @Test
    fun `square layout shows the compact header identity and channel utilization`() = render(WidgetSizes.SQUARE) {
        onNode(hasText(appName)).assertExists()
        onNode(hasText("85%")).assertExists()
        onNode(hasText("18.5%")).assertExists()
        onNode(hasText("3.2%")).assertDoesNotExist()
        onNode(hasText("2/3")).assertDoesNotExist()
    }

    @Test
    fun `wide square layout adds air utilization and nodes online`() = render(WidgetSizes.WIDE) {
        onNode(hasText("3.2%")).assertExists()
        onNode(hasText("2/3")).assertExists()
    }

    @Test
    fun `one-row layout has no header text but keeps node battery and channel utilization`() = render(WidgetSizes.ROW) {
        onNode(hasText(appName)).assertDoesNotExist()
        onNode(hasText("ME")).assertExists()
        onNode(hasText("85%")).assertExists()
        onNode(hasText("18.5%")).assertExists()
        onNode(hasText("2/3")).assertDoesNotExist()
    }

    @Test
    fun `wide one-row layout adds nodes online`() = render(WidgetSizes.WIDE_ROW) {
        onNode(hasText("2/3")).assertExists()
    }

    @Test
    fun `three-row wide layout adds free memory`() = render(WidgetSizes.EXTRA_BIG) {
        onNode(hasText(freeMemoryLabel)).assertExists()
    }

    @Test
    fun `disconnected content keeps the header and renders the translated status`() =
        render(WidgetSizes.BIG, disconnected) {
            onNode(hasText(appName)).assertExists()
            onNode(hasText(disconnectedLabel)).assertExists()
        }

    @Test
    fun `one-row disconnected layout still renders the status`() = render(WidgetSizes.ROW, disconnected) {
        onNode(hasText(disconnectedLabel)).assertExists()
    }

    private companion object {
        /**
         * Well above Glance's 2-second `DEFAULT_TIMEOUT`, which is not a safe budget here. Booting Robolectric and
         * loading Compose Multiplatform resources comfortably exceeds two seconds whenever the machine is busy, and
         * `configureTestOptions()` sets `maxParallelForks` to every available processor in CI. The default timeout was
         * already observed failing one of these tests with `UncompletedCoroutinesError` during a full baseline run
         * while passing in isolation — the classic signature of a too-tight budget rather than a hung test.
         */
        val TEST_TIMEOUT = 30.seconds
    }
}
