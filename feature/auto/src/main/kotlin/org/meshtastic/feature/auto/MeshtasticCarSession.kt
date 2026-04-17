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
        handleIntent(intent, screen)
        return screen
    }

    /**
     * Called by the Android Auto host when the session is re-activated from an
     * existing [MessagingStyle][androidx.core.app.NotificationCompat.MessagingStyle]
     * notification tap or a launcher shortcut.
     *
     * Parses the conversation [contactKey] from the deep-link URI
     * (`meshtastic://messages/<contactKey>`) and delegates to
     * [MeshtasticCarScreen.selectContactKey] so the correct tab is pre-selected.
     */
    override fun onNewIntent(intent: Intent) {
        val screen = screenManager.top as? MeshtasticCarScreen ?: return
        handleIntent(intent, screen)
    }

    private fun handleIntent(intent: Intent, screen: MeshtasticCarScreen) {
        // Deep-link URIs from MessagingStyle notifications look like:
        //   meshtastic://messages/0!abcd1234   (DM: channel=0, nodeId=!abcd1234)
        //   meshtastic://messages/2^all        (channel broadcast, e.g. contactKey "2^all")
        // Both channels and DMs now live in the same Messages tab, so we simply
        // switch to that tab regardless of the contact type.
        val contactKey = intent.data?.lastPathSegment ?: return
        screen.selectContactKey(contactKey)
    }
}
