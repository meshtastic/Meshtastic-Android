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

import android.content.Intent
import android.util.Log
import androidx.core.net.toUri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.Configurator
import androidx.test.uiautomator.UiDevice
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Test
import org.junit.runner.RunWith
import tools.fastlane.screengrab.Screengrab
import tools.fastlane.screengrab.UiAutomatorScreenshotStrategy
import tools.fastlane.screengrab.cleanstatusbar.CleanStatusBar
import tools.fastlane.screengrab.locale.LocaleTestRule

/**
 * spike-screengrab (throwaway). Drives the REAL app on the emulator through deep links and captures one
 * screengrab per screen. The radio connection is established BEFORE this runs (connect deep link + trust dialog
 * tapped by the harness); the foreground service keeps it alive across the ActivityScenario launch.
 *
 * Instrumentation args (all optional): nodeNum (node detail target), nodeName (text to wait for on nodes/detail),
 * host (sim address text to wait for on connections), mapSettleMs (fixed settle for GL map tiles).
 */
@RunWith(AndroidJUnit4::class)
class ScreengrabSpikeTest {

    @Test
    fun captureAllScreens() {
        // Not ActivityScenario: its launch() blocks on Instrumentation.waitForIdleSync(), and a main thread fed
        // 2 pkt/s by the sim never goes idle, so it hangs forever (observed twice, 475 s+). Plain startActivity
        // plus bounded polling is the only shape that works against a live radio.
        Log.i(TAG, "launching MainActivity")
        deepLink("meshtastic://meshtastic/nodes")
        run {
            waitFor(By.pkg(pkg).depth(0), "launch")

            deepLink("meshtastic://meshtastic/nodes")
            waitForName(nodeName, "nodes")
            Screengrab.screenshot("01_nodes")

            // Broadcast contactKey is "$channel$to" with to = "^all" (MeshDataHandlerImpl.contactKey), so 0^all.
            deepLink("meshtastic://meshtastic/messages/0%5Eall")
            waitForName(messagesProbe, "messages")
            Screengrab.screenshot("02_messages")

            deepLink("meshtastic://meshtastic/map")
            // Tiles render into a GL surface UiAutomator cannot see; a fixed settle is the only option here.
            Thread.sleep(mapSettleMs)
            Log.i(TAG, "map: settled ${mapSettleMs}ms")
            Screengrab.screenshot("03_map")

            deepLink("meshtastic://meshtastic/nodes/$nodeNum")
            waitForName(nodeName, "node_detail")
            Screengrab.screenshot("04_node_detail")

            deepLink("meshtastic://meshtastic/connections")
            waitForName(host, "connections")
            Screengrab.screenshot("05_connections")
        }
    }

    private fun deepLink(uri: String) {
        val intent = launchIntent(uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        InstrumentationRegistry.getInstrumentation().targetContext.startActivity(intent)
        Log.i(TAG, "deep link fired: $uri")
    }

    private fun launchIntent(uri: String): Intent =
        Intent(Intent.ACTION_VIEW, uri.toUri())
            .setClassName(pkg, "org.meshtastic.app.MainActivity")
            .putExtra("skip_onboarding", true)

    // Hand-rolled poll: the sim pushes 2 pkt/s so the window never goes idle, and UiAutomator's own wait() leans
    // on waitForIdle. The Configurator idle timeout is clamped low in beforeAll so each hasObject() stays bounded.
    // Compose exposes node names as text on some rows and only as contentDescription on others; accept either.
    private fun waitForName(name: String, screen: String) =
        waitFor(listOf(By.textContains(name), By.descContains(name)), screen)

    private fun waitFor(selector: BySelector, screen: String) = waitFor(listOf(selector), screen)

    private fun waitFor(selectors: List<BySelector>, screen: String) {
        val selector = selectors.first()
        val start = System.currentTimeMillis()
        var found = false
        while (System.currentTimeMillis() - start < TIMEOUT_MS) {
            if (selectors.any { device.hasObject(it) }) {
                found = true
                break
            }
            Thread.sleep(POLL_MS)
        }
        val elapsed = System.currentTimeMillis() - start
        if (found) {
            Log.i(TAG, "$screen: found $selector after ${elapsed}ms")
        } else {
            Log.w(TAG, "$screen: wait for $selector timed out after ${elapsed}ms - capturing anyway")
        }
        Thread.sleep(SETTLE_MS)
    }

    companion object {
        private const val TAG = "ScreengrabSpike"
        private const val TIMEOUT_MS = 20_000L
        private const val SETTLE_MS = 1_000L
        private const val POLL_MS = 500L

        @JvmField
        @ClassRule
        val localeTestRule = LocaleTestRule()

        private val device: UiDevice by lazy { UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()) }
        private val pkg: String by lazy { InstrumentationRegistry.getInstrumentation().targetContext.packageName }
        private val args by lazy { InstrumentationRegistry.getArguments() }
        private val nodeNum: String by lazy { args.getString("nodeNum", "269023308") }
        private val nodeName: String by lazy { args.getString("nodeName", "Burro") }
        private val messagesProbe: String by lazy { args.getString("messagesProbe", "RPLY") }
        private val host: String by lazy { args.getString("host", "Replay Observer") }
        private val mapSettleMs: Long by lazy { args.getString("mapSettleMs", "20000").toLong() }
        private val cleanStatusBar: Boolean by lazy { args.getString("cleanStatusBar", "false").toBoolean() }

        @JvmStatic
        @BeforeClass
        fun beforeAll() {
            Configurator.getInstance().apply {
                waitForIdleTimeout = 1_000L
                waitForSelectorTimeout = 0L
                actionAcknowledgmentTimeout = 500L
            }
            Screengrab.setDefaultScreenshotStrategy(UiAutomatorScreenshotStrategy())
            if (cleanStatusBar) {
                runCatching { CleanStatusBar().setClock("1200").setBatteryLevel(100).enable() }
                    .onFailure { Log.w(TAG, "CleanStatusBar failed", it) }
            }
        }

        @JvmStatic
        @AfterClass
        fun afterAll() {
            if (cleanStatusBar) runCatching { CleanStatusBar.disable() }
        }
    }
}
