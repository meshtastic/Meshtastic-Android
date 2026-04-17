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
package org.meshtastic.feature.auto

import android.content.Intent
import androidx.car.app.Screen
import androidx.car.app.Session

/** Android Auto session that hosts the [MeshtasticCarScreen] root screen. */
class MeshtasticCarSession : Session() {

    override fun onCreateScreen(intent: Intent): Screen {
        val screen = MeshtasticCarScreen(carContext)
        applyIntent(intent, screen)
        return screen
    }

    /**
     * Called by the Android Auto host when the session is re-activated from an existing
     * [MessagingStyle][androidx.core.app.NotificationCompat.MessagingStyle] notification tap or a
     * launcher shortcut. Switches the root screen to the Messages tab.
     *
     * The deep-link URI (`meshtastic://meshtastic/messages/<contactKey>`) carries the originating
     * contact key, but `androidx.car.app.model.ListTemplate` does not currently expose a
     * programmatic scroll API, so we cannot focus a specific conversation row.
     */
    override fun onNewIntent(intent: Intent) {
        val screen = screenManager.top as? MeshtasticCarScreen ?: return
        applyIntent(intent, screen)
    }

    private fun applyIntent(intent: Intent, screen: MeshtasticCarScreen) {
        if (intent.data?.lastPathSegment != null) screen.selectMessagesTab()
    }
}
