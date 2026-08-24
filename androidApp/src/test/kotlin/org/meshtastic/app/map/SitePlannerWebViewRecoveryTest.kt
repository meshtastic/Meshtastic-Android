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
package org.meshtastic.app.map

import android.app.Application
import android.content.res.Resources
import android.util.AndroidRuntimeException
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Classifier guard for the Site Planner WebView provider-update mitigation: only the two exception shapes the AOSP
 * provider-update race produces may be absorbed and retried; anything else must keep propagating.
 */
@RunWith(RobolectricTestRunner::class)
// Bare Application: booting the real MeshUtilApplication leaks its scopes across tests (#6644).
@Config(application = Application::class, sdk = [34])
class SitePlannerWebViewRecoveryTest {

    @Test
    fun `resources redirect race is recoverable`() {
        val race = Resources.NotFoundException("failed to redirect ResourcesImpl")
        assertTrue(isWebViewProviderUpdateException(race))
    }

    @Test
    fun `webview provider load failure wrapper is recoverable`() {
        val wrapper =
            AndroidRuntimeException(
                "android.webkit.WebViewFactory\$MissingWebViewPackageException: Failed to load WebView provider: No WebView installed",
            )
        assertTrue(isWebViewProviderUpdateException(wrapper))
    }

    @Test
    fun `unrelated AndroidRuntimeException is not swallowed`() {
        val unrelated = AndroidRuntimeException("Calling startActivity() from outside of an Activity context")
        assertFalse(isWebViewProviderUpdateException(unrelated))
    }

    @Test
    fun `unrelated RuntimeException is not swallowed`() {
        assertFalse(isWebViewProviderUpdateException(IllegalStateException("boom")))
    }
}
