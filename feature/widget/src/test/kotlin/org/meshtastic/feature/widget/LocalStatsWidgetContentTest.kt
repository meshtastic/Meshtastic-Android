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
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.unit.hasContentDescriptionEqualTo
import androidx.glance.testing.unit.hasText
import androidx.test.core.app.ApplicationProvider
import org.junit.runner.RunWith
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.disconnected
import org.meshtastic.core.resources.getString
import org.meshtastic.core.resources.refresh
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
    private val disconnectedLabel: String = getString(Res.string.disconnected)
    private val refreshLabel: String = getString(Res.string.refresh)

    @Test
    fun `connected content renders node identity and footer counts`() =
        runGlanceAppWidgetUnitTest(timeout = TEST_TIMEOUT) {
            setAppWidgetSize(BIG_SQUARE)
            setContext(context)

            provideComposable { LocalStatsWidget().WidgetContent(createMockWidgetState()) }

            // Values straight from createMockWidgetState(): short name, battery, and onlineNodes/totalNodes.
            onNode(hasText("ME")).assertExists()
            onNode(hasText("85%")).assertExists()
            onNode(hasText("2/3")).assertExists()
        }

    @Test
    fun `disconnected content renders the translated disconnected status`() =
        runGlanceAppWidgetUnitTest(timeout = TEST_TIMEOUT) {
            setAppWidgetSize(BIG_SQUARE)
            setContext(context)

            provideComposable {
                LocalStatsWidget()
                    .WidgetContent(
                        LocalStatsWidgetUiState(connectionState = ConnectionState.Disconnected, showContent = false),
                    )
            }

            // Proves the Crowdin-managed string resolved through the WidgetResourceEnvironment bridge.
            onNode(hasText(disconnectedLabel)).assertExists()
        }

    @Test
    fun `refresh affordance is present and labelled for accessibility`() =
        runGlanceAppWidgetUnitTest(timeout = TEST_TIMEOUT) {
            setAppWidgetSize(BIG_SQUARE)
            setContext(context)

            provideComposable { LocalStatsWidget().WidgetContent(createMockWidgetState()) }

            // Glance's unit-test filters have no matcher for actionRunCallback, so this pins the button's
            // presence and its content description rather than the callback class it dispatches to.
            onNode(hasContentDescriptionEqualTo(refreshLabel)).assertExists()
        }

    @Test
    fun `narrow layout still renders content rather than collapsing`() =
        runGlanceAppWidgetUnitTest(timeout = TEST_TIMEOUT) {
            // Smallest entry in LocalStatsWidget's SizeMode.Responsive set, where isNarrow/isShort both kick in.
            setAppWidgetSize(SMALL_SQUARE)
            setContext(context)

            provideComposable { LocalStatsWidget().WidgetContent(createMockWidgetState()) }

            onNode(hasText("ME")).assertExists()
        }

    private companion object {
        val SMALL_SQUARE = DpSize(100.dp, 100.dp)
        val BIG_SQUARE = DpSize(250.dp, 250.dp)

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
