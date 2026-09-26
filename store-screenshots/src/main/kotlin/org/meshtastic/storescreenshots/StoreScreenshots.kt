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
package org.meshtastic.storescreenshots

import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.Configurator
import androidx.test.uiautomator.UiAutomatorTestScope
import androidx.test.uiautomator.uiAutomator
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * The store-listing screenshots, taken from the real debug app connected to Demo Mode's hidden showcase mesh.
 *
 * Every screen is reached by a deep link rather than by tapping labelled tabs, so the flow does not depend on the
 * display language. Each capture is the full screen, status bar included, taken once the app's window has stopped
 * changing.
 */
@RunWith(AndroidJUnit4::class)
class StoreScreenshots {

    private val arguments = InstrumentationRegistry.getArguments()
    private val appId = requireNotNull(arguments.getString("targetAppId")) { "targetAppId argument missing" }
    // Shared media storage: the one app directory the shell user can read, so [save] can copy out of it.
    @Suppress("DEPRECATION")
    private val mediaDir = InstrumentationRegistry.getInstrumentation().context.externalMediaDirs.first()

    @Test
    fun capturesEveryStoreScreen() = uiAutomator {
        // Compose screens with an animation never go idle; waiting for idle would stall every step.
        Configurator.getInstance().setWaitForIdleTimeout(0)
        prepareDevice()
        try {
            FormFactor.entries.forEach { formFactor ->
                shell("wm size ${formFactor.widthPx}x${formFactor.heightPx}")
                shell("wm density ${formFactor.densityDpi}")
                cleanStatusBar()
                connectToShowcase()
                Shot.entries.forEach { shot -> capture(formFactor, shot) }
            }
        } finally {
            shell("wm size reset")
            shell("wm density reset")
            demo("exit")
            shell("pm enable $LAUNCHER")
        }
    }

    /**
     * No system dialog over a stalled launcher on a cold, software-rendered emulator, and no launcher at all: on a
     * large screen its taskbar would sit along the bottom of every shot.
     */
    private fun UiAutomatorTestScope.prepareDevice() {
        shell("settings put global hide_error_dialogs 1")
        shell("pm disable-user --user 0 $LAUNCHER")
        shell("settings put global sysui_demo_allowed 1")
        // SystemUI reads the setting asynchronously; a demo command sent before it has is dropped.
        SystemClock.sleep(SYSTEM_UI_SETTLE_MS)
    }

    /** SystemUI demo mode, sent again after every display change, which can rebuild the status bar. */
    private fun UiAutomatorTestScope.cleanStatusBar() {
        // The first burst after demo mode is allowed is dropped on a cold emulator (measured: the first form factor
        // kept the real clock, the later ones took the demo); a second burst after a settle lands.
        repeat(DEMO_BURSTS) {
            SystemClock.sleep(SYSTEM_UI_SETTLE_MS)
            demo("enter")
            demo("clock -e hhmm 0941")
            demo("battery -e level 100 -e plugged false")
            demo("network -e wifi show -e level 4 -e fully true -e mobile show -e datatype none -e level 4")
            demo("notifications -e visible false")
        }
        SystemClock.sleep(SYSTEM_UI_SETTLE_MS)
    }

    private fun UiAutomatorTestScope.demo(command: String) =
        shell("am broadcast -a com.android.systemui.demo -e command $command")

    /**
     * Relaunches the app into the showcase mesh and waits for our node's name. The launch switches skip onboarding and
     * the trust dialog; if either still appears it is logged and handled rather than failing the run.
     */
    private fun UiAutomatorTestScope.connectToShowcase() {
        repeat(CONNECT_ATTEMPTS) { attempt ->
            shell("am force-stop $appId")
            open("connections?address=$SHOWCASE_ADDRESS", clearTask = true)
            val deadline = SystemClock.uptimeMillis() + CONNECT_TIMEOUT_MS
            while (SystemClock.uptimeMillis() < deadline) {
                if (onElementOrNull(POLL_MS) { hasText(SHOWCASE_NODE_NAME) } != null) return
                if (onElementOrNull(0) { hasText(TRUST_DIALOG_TITLE) } != null) {
                    Log.w(TAG, "the trust dialog survived skip_connect_confirm on attempt ${attempt + 1}; confirming")
                    onElementOrNull(0) { hasText(TRUST_DIALOG_CONFIRM) }?.click()
                }
                if (onElementOrNull(0) { hasText(ONBOARDING_START) } != null) {
                    Log.w(TAG, "onboarding survived skip_onboarding on attempt ${attempt + 1}; relaunching")
                    break
                }
            }
        }
        error("the app never showed $SHOWCASE_NODE_NAME after $CONNECT_ATTEMPTS launches")
    }

    private fun UiAutomatorTestScope.capture(formFactor: FormFactor, shot: Shot) {
        if (shot.readFirst) {
            // A thread with unread messages opens at the first of them, behind a "new messages below" divider and a
            // jump-to-latest pill. Opening it once marks it read; the capture then opens it at the latest message.
            open(shot.path)
            SystemClock.sleep(READ_WARM_UP_MS)
            open(Shot.Nodes.path)
            SystemClock.sleep(READ_WARM_UP_MS)
        }
        open(shot.path)
        SystemClock.sleep(shot.minimumWaitMs)
        val stable =
            waitForStableInActiveWindow(
                stableTimeoutMs = shot.stableTimeoutMs,
                stableIntervalMs = shot.stableIntervalMs,
                stablePollIntervalMs = POLL_MS,
                requireStableScreenshot = true,
            )
        check(device.currentPackageName == appId) {
            "${device.currentPackageName} is in front instead of $appId while capturing ${shot.fileName}"
        }
        val name = "${formFactor.folder}/${shot.fileName}.png"
        if (stable.isTimeout) Log.w(TAG, "$name never settled; capturing as is")
        val bitmap = requireNotNull(device.takeScreenshot()) { "no screenshot for $name" }
        save(bitmap, name)
    }

    /** Launches through the debug build's shell-only alias, the one launch the app honours the switches on. */
    private fun UiAutomatorTestScope.open(path: String, clearTask: Boolean = false) {
        val flags = if (clearTask) "--activity-clear-task " else ""
        shell(
            "am start -W $flags-a android.intent.action.VIEW -d https://meshtastic.org/$path " +
                "-n $appId/org.meshtastic.app.AutomationLauncher " +
                "--ez skip_onboarding true --ez skip_connect_confirm true",
        )
    }

    private fun UiAutomatorTestScope.shell(command: String): String =
        ParcelFileDescriptor.AutoCloseInputStream(uiAutomation.executeShellCommand(command)).use {
            it.bufferedReader().readText()
        }

    /**
     * Writes into this package's media directory, then copies to [DEVICE_OUTPUT], which the shell owns: the media
     * directory goes with the uninstall at the end of a connected run, and the workflow pulls from the copy.
     */
    private fun UiAutomatorTestScope.save(bitmap: Bitmap, name: String) {
        val file = File(mediaDir, name)
        file.parentFile?.mkdirs()
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, it) }
        val target = "$DEVICE_OUTPUT/$name"
        shell("mkdir -p ${target.substringBeforeLast('/')}")
        shell("cp ${file.path} $target")
        Log.i(TAG, "saved $target")
    }

    private fun AccessibilityNodeInfo.hasText(value: String) = text?.toString() == value

    /** The three surfaces `fastlane supply` uploads, at the sizes the store asks for. */
    private enum class FormFactor(val folder: String, val widthPx: Int, val heightPx: Int, val densityDpi: Int) {
        Phone("phoneScreenshots", 1080, 1920, 400),
        SevenInch("sevenInchScreenshots", 1080, 1920, 288),
        TenInch("tenInchScreenshots", 2560, 1440, 320),
    }

    /** The five listing shots, named as fastlane lays them out. The map loads tiles for a while before it settles. */
    private enum class Shot(
        val fileName: String,
        val path: String,
        val minimumWaitMs: Long = 2_000,
        val stableTimeoutMs: Long = 30_000,
        val stableIntervalMs: Long = 2_000,
        val readFirst: Boolean = false,
    ) {
        // The primary channel's contact key, raw: `am start` takes it literally and Uri.parse accepts the caret.
        Messages("1_messages", "messages/0^all", readFirst = true),
        Nodes("2_nodes", "nodes"),
        Map("3_map", "map", minimumWaitMs = 45_000, stableTimeoutMs = 120_000, stableIntervalMs = 8_000),
        NodeDetail("4_node_detail", "nodes/$RIDGE_TOP_NUM"),
        Channels("5_channels", "channels"),
    }

    private companion object {
        const val TAG = "StoreScreenshots"

        /** Where the captures are left for `adb pull`, laid out as fastlane's `images/` folder. */
        const val DEVICE_OUTPUT = "/data/local/tmp/store-screenshots"

        /** Demo Mode's hidden showcase mesh; `MockScenario.SHOWCASE` in `:core:network`. */
        const val SHOWCASE_ADDRESS = "mshowcase"
        const val SHOWCASE_NODE_NAME = "Base Camp"

        /** Ridge Top, the showcase's router with a full detail page. */
        const val RIDGE_TOP_NUM = 0xe1e22a35.toInt()

        const val TRUST_DIALOG_TITLE = "Connect to this device?"
        const val TRUST_DIALOG_CONFIRM = "Connect"
        const val ONBOARDING_START = "Get started"

        /** Pixel Launcher on the google_apis emulator images: the stalls, and the tablet taskbar. */
        const val LAUNCHER = "com.google.android.apps.nexuslauncher"
        const val SYSTEM_UI_SETTLE_MS = 3_000L
        const val DEMO_BURSTS = 2

        const val READ_WARM_UP_MS = 3_000L

        const val CONNECT_ATTEMPTS = 3
        const val CONNECT_TIMEOUT_MS = 60_000L
        const val POLL_MS = 500L
        const val PNG_QUALITY = 100
    }
}
