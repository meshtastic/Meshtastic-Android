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
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.LocalAppWidgetOptions
import androidx.glance.appwidget.testing.unit.GlanceAppWidgetUnitTest
import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.unit.hasContentDescriptionEqualTo
import androidx.glance.testing.unit.hasText
import androidx.test.core.app.ApplicationProvider
import org.junit.runner.RunWith
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.battery
import org.meshtastic.core.resources.disconnected
import org.meshtastic.core.resources.free_memory
import org.meshtastic.core.resources.getString
import org.meshtastic.core.resources.meshtastic_app_name
import org.meshtastic.core.resources.refresh
import org.meshtastic.core.resources.updated
import org.meshtastic.core.resources.uptime
import org.meshtastic.core.resources.widget_packets_bad
import org.meshtastic.core.resources.widget_packets_dropped
import org.meshtastic.core.resources.widget_packets_received
import org.meshtastic.core.resources.widget_packets_sent
import org.meshtastic.core.resources.widget_relays
import org.meshtastic.core.resources.widget_relays_canceled
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
    private val sentLabel: String = getString(Res.string.widget_packets_sent)
    private val receivedLabel: String = getString(Res.string.widget_packets_received)
    private val freeMemoryLabel: String = getString(Res.string.free_memory)
    private val relaysLabel: String = getString(Res.string.widget_relays)
    private val badLabel: String = getString(Res.string.widget_packets_bad)
    private val droppedLabel: String = getString(Res.string.widget_packets_dropped)
    private val canceledLabel: String = getString(Res.string.widget_relays_canceled)
    private val uptimeLabel: String = getString(Res.string.uptime)
    private val batteryLabel: String = getString(Res.string.battery)
    private val updatedLabel: String = getString(Res.string.updated)

    private val disconnected = LocalStatsWidgetUiState(connectionState = ConnectionState.Disconnected)

    private fun render(
        size: DpSize,
        state: LocalStatsWidgetUiState = createMockWidgetState(),
        fontScale: Float = 1f,
        options: Bundle = Bundle(),
        assertions: GlanceAppWidgetUnitTest.() -> Unit,
    ) = runGlanceAppWidgetUnitTest(timeout = TEST_TIMEOUT) {
        setAppWidgetSize(size)
        setContext(context.atFontScale(fontScale))
        provideComposable {
            CompositionLocalProvider(LocalAppWidgetOptions provides options) {
                LocalStatsWidget().WidgetContent(state)
            }
        }
        assertions()
    }

    /** What a launcher puts in the widget options: the cell in portrait (min width, max height) and in landscape. */
    private fun reportedCells(portrait: DpSize, landscape: DpSize): Bundle = Bundle().apply {
        putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, portrait.width.value.toInt())
        putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, portrait.height.value.toInt())
        putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, landscape.width.value.toInt())
        putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, landscape.height.value.toInt())
    }

    @Test
    fun `wide two-row layout carries the brand header refresh identity and traffic`() = render(WidgetSizes.BIG) {
        onNode(hasText(appName)).assertExists()
        onNode(hasContentDescriptionEqualTo(refreshLabel)).assertExists()
        onNode(hasText("ME")).assertExists()
        onNode(hasText("85%")).assertExists()
        onNode(hasText("2/3")).assertExists()
        onNode(hasText(sentLabel)).assertExists()
        onNode(hasText("145")).assertExists()
        onNode(hasText(receivedLabel)).assertDoesNotExist()
    }

    @Test
    fun `two-column two-row layout shows four readings at the tier floor`() = render(WidgetSizes.TWO_COLUMNS) {
        onNode(hasText(sentLabel)).assertExists()
        onNode(hasText("892")).assertExists()
        onNode(hasText(freeMemoryLabel)).assertExists()
        onNode(hasText(relaysLabel)).assertDoesNotExist()
    }

    @Test
    fun `a Pixel 6a 4x2 shows six readings in two columns`() = render(PIXEL_6A_4X2) {
        onNode(hasText(relaysLabel)).assertExists()
        onNode(hasText(badLabel)).assertExists()
        onNode(hasText(droppedLabel)).assertDoesNotExist()
        onNode(hasText(updatedLabel)).assertDoesNotExist()
    }

    @Test
    fun `a taller 4x2 cell shows every reading`() = render(EMULATOR_4X2) {
        onNode(hasText(canceledLabel)).assertExists()
        onNode(hasText(updatedLabel)).assertDoesNotExist()
    }

    @Test
    fun `the line budget comes from the reported cell the breakpoint fits in`() =
        render(WidgetSizes.TWO_COLUMNS, options = reportedCells(EMULATOR_4X2, DpSize(700.dp, 120.dp))) {
            onNode(hasText(canceledLabel)).assertExists()
        }

    @Test
    fun `in portrait the portrait cell wins over a landscape cell the breakpoint also fits`() =
        render(WidgetSizes.EXTRA_BIG, options = reportedCells(DpSize(266.dp, 470.dp), DpSize(300.dp, 265.dp))) {
            // The portrait cell holds every reading in one column; the landscape one, which fits too, holds five.
            onNode(hasText(canceledLabel)).assertExists()
            onNode(hasText(updatedLabel)).assertExists()
        }

    @Test
    fun `a preferred cell the breakpoint does not fit in falls back to the other orientation`() =
        render(WidgetSizes.TWO_COLUMNS, options = reportedCells(DpSize(200.dp, 100.dp), DpSize(400.dp, 234.dp))) {
            // 234dp holds five lines of two columns; the breakpoint alone would hold two.
            onNode(hasText(canceledLabel)).assertExists()
        }

    @Test
    fun `a reported cell is capped under the next breakpoint`() =
        render(WidgetSizes.BIG, options = reportedCells(DpSize(266.dp, 470.dp), DpSize(420.dp, 300.dp))) {
            // The two-row tier is shown under 260dp, which holds six lines, not the nine a 470dp cell would count.
            onNode(hasText(relaysLabel)).assertExists()
            onNode(hasText(droppedLabel)).assertDoesNotExist()
        }

    @Test
    fun `reported cells the breakpoint fits in neither of are ignored`() =
        render(WidgetSizes.TWO_COLUMNS, options = reportedCells(DpSize(200.dp, 100.dp), DpSize(400.dp, 60.dp))) {
            onNode(hasText(freeMemoryLabel)).assertExists()
            onNode(hasText(relaysLabel)).assertDoesNotExist()
        }

    @Test
    fun `a larger font scale counts fewer lines and columns instead of clipping`() =
        render(PIXEL_6A_4X2, fontScale = 1.5f) {
            onNode(hasText(uptimeLabel)).assertExists()
            onNode(hasText(sentLabel)).assertDoesNotExist()
        }

    @Test
    fun `two-column three-row layout shows every reading and the update time`() =
        render(WidgetSizes.EXTRA_TWO_COLUMNS) {
            onNode(hasText(droppedLabel)).assertExists()
            onNode(hasText(canceledLabel)).assertExists()
            onNode(hasText(updatedLabel)).assertExists()
        }

    @Test
    fun `narrow two-row layout keeps the brand header but not refresh or traffic`() = render(WidgetSizes.TALL) {
        onNode(hasText(appName)).assertExists()
        onNode(hasContentDescriptionEqualTo(refreshLabel)).assertDoesNotExist()
        onNode(hasText("18.5%")).assertExists()
        onNode(hasText("3.2%")).assertExists()
        onNode(hasText("2/3")).assertExists()
        onNode(hasText(sentLabel)).assertDoesNotExist()
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
    fun `three-row wide layout adds relays and the update time but not bad packets`() = render(WidgetSizes.EXTRA_BIG) {
        onNode(hasText(relaysLabel)).assertExists()
        onNode(hasText(updatedLabel)).assertExists()
        onNode(hasText(badLabel)).assertDoesNotExist()
    }

    @Test
    fun `three-row narrow layout adds the packet counts but not free memory`() = render(WidgetSizes.EXTRA_TALL) {
        onNode(hasText(sentLabel)).assertExists()
        onNode(hasText("892")).assertExists()
        onNode(hasText(freeMemoryLabel)).assertDoesNotExist()
    }

    @Test
    fun `free memory is shown in bytes units`() =
        render(WidgetSizes.EXTRA_BIG, createMockWidgetState().copy(heapFreeBytes = 130_172)) {
            onNode(hasText("130 kB")).assertExists()
        }

    @Test
    fun `a reported noise floor renders in dBm`() =
        render(WidgetSizes.EXTRA_BIG, createMockWidgetState().copy(noiseFloor = -107)) {
            onNode(hasText("-107 dBm")).assertExists()
        }

    @Test
    fun `no local stats yet leaves out traffic but keeps uptime`() =
        render(WidgetSizes.BIG, createMockWidgetState().copy(hasStats = false)) {
            onNode(hasText(sentLabel)).assertDoesNotExist()
            onNode(hasText(uptimeLabel)).assertExists()
        }

    @Test
    fun `the unlabeled battery value is announced with its label`() = render(WidgetSizes.TALL) {
        onNode(hasContentDescriptionEqualTo("$batteryLabel 85%")).assertExists()
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

    /** The same context with the user's font scale set, which [WidgetSizes.lines] reads from its configuration. */
    private fun Context.atFontScale(scale: Float): Context = if (scale == 1f) {
        this
    } else {
        createConfigurationContext(Configuration(resources.configuration).apply { fontScale = scale })
    }

    private companion object {
        /** A 4x2 cell on a Pixel 6a, and the taller one the API 36 emulator's Pixel launcher gives. */
        val PIXEL_6A_4X2 = DpSize(358.dp, 202.dp)
        val EMULATOR_4X2 = DpSize(360.dp, 234.dp)

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
