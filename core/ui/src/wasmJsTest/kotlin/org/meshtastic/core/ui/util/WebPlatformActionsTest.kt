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
@file:Suppress(
    // Every js("...") snippet references its Kotlin parameters inside the JS source, which detekt cannot see.
    "UnusedParameter",
)

package org.meshtastic.core.ui.util

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlinx.coroutines.await
import kotlinx.coroutines.test.runTest
import kotlin.js.Promise
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WebPlatformActionsTest {

    @AfterTest fun restoreBrowser() = restoreStubs()

    @Test
    fun withoutWebShareTheTextGoesToTheFallback() {
        stubShare(available = false, rejectWith = null)
        var copied = false

        shareOrElse("text", "subject") { copied = true }

        assertTrue(copied)
    }

    @Test
    fun webShareGetsTheTitleAndText() = runTest {
        stubShare(available = true, rejectWith = null)
        var copied = false

        shareOrElse("text", "subject") { copied = true }
        settle().await<JsAny?>()

        assertEquals("subject|text", lastShared())
        assertFalse(copied)
    }

    @Test
    fun closingTheShareSheetCopiesNothing() = runTest {
        stubShare(available = true, rejectWith = "AbortError")
        var copied = false

        shareOrElse("text", "subject") { copied = true }
        settle().await<JsAny?>()

        assertFalse(copied)
    }

    @Test
    fun aRefusedShareFallsBackToTheClipboard() = runTest {
        stubShare(available = true, rejectWith = "NotAllowedError")
        var copied = false

        shareOrElse("text", "subject") { copied = true }
        settle().await<JsAny?>()

        assertTrue(copied)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun openMapOpensOpenStreetMapInANewTab() = runComposeUiTest {
        stubWindowOpen()

        setContent {
            val openMap = rememberOpenMap()
            LaunchedEffect(Unit) { openMap(51.5074, -0.1278, "Node") }
        }
        waitForIdle()

        assertEquals(
            "https://www.openstreetmap.org/?mlat=51.507400&mlon=-0.127800#map=17/51.507400/-0.127800|_blank",
            lastOpened(),
        )
    }
}

private fun stubShare(available: Boolean, rejectWith: String?) {
    js(
        """{
        globalThis.meshtasticTestRestore = globalThis.meshtasticTestRestore || [];
        for (const name of ['share', 'canShare']) {
            const own = Object.getOwnPropertyDescriptor(navigator, name);
            globalThis.meshtasticTestRestore.push(() => {
                if (own) Object.defineProperty(navigator, name, own); else delete navigator[name];
            });
        }
        globalThis.meshtasticLastShared = null;
        const share = (data) => {
            globalThis.meshtasticLastShared = data.title + '|' + data.text;
            return rejectWith ? Promise.reject(new DOMException('stub', rejectWith)) : Promise.resolve();
        };
        Object.defineProperty(navigator, 'share', { value: available ? share : undefined, configurable: true });
        Object.defineProperty(navigator, 'canShare', { value: available ? () => true : undefined, configurable: true });
    }""",
    )
}

private fun stubWindowOpen() {
    js(
        """{
        globalThis.meshtasticTestRestore = globalThis.meshtasticTestRestore || [];
        const original = window.open;
        globalThis.meshtasticTestRestore.push(() => { window.open = original; });
        globalThis.meshtasticLastOpened = null;
        window.open = (url, target) => { globalThis.meshtasticLastOpened = url + '|' + target; return null; };
    }""",
    )
}

private fun restoreStubs() {
    js(
        "{ (globalThis.meshtasticTestRestore || []).reverse().forEach((f) => f()); globalThis.meshtasticTestRestore = []; }",
    )
}

private fun lastShared(): String? = js("globalThis.meshtasticLastShared")

private fun lastOpened(): String? = js("globalThis.meshtasticLastOpened")

// Lets the stubbed share promise settle and its handlers run.
private fun settle(): Promise<JsAny?> = js("new Promise((resolve) => setTimeout(() => resolve(null), 0))")
