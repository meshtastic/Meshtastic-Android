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
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.meshtastic.core.navigation.DEEP_LINK_BASE_URI
import org.meshtastic.core.ui.theme.AppTheme
import org.meshtastic.core.ui.theme.MODE_DYNAMIC
import org.meshtastic.core.ui.viewmodel.UIViewModel
import org.meshtastic.feature.messaging.MessageScreen
import org.meshtastic.feature.messaging.MessageViewModel

/**
 * Hosts a single conversation inside a notification bubble.
 *
 * Bubbles require their activity to be resizeable, embeddable and document-launched, which the launcher activity cannot
 * be without changing how the whole app behaves in recents — so this is a separate, deliberately small host rendering
 * only [MessageScreen].
 *
 * Anything that navigates out of the conversation hands off to the full app rather than just closing: a bubble that
 * vanished when you asked for node details would look like a crash. Only back — which for a bubble means "collapse me"
 * — finishes on its own.
 */
class BubbleActivity : AppCompatActivity() {

    private val model: UIViewModel by viewModel()
    private val messageViewModel: MessageViewModel by viewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val contactKey = intent?.data?.lastPathSegment
        if (contactKey.isNullOrEmpty()) {
            finish()
            return
        }
        messageViewModel.setContactKey(contactKey)

        setContent {
            val theme by model.theme.collectAsStateWithLifecycle()
            val dark =
                when (theme) {
                    AppCompatDelegate.MODE_NIGHT_YES -> true
                    AppCompatDelegate.MODE_NIGHT_NO -> false
                    else -> isSystemInDarkTheme()
                }
            AppTheme(dynamicColor = theme == MODE_DYNAMIC, darkTheme = dark) {
                MessageScreen(
                    contactKey = contactKey,
                    message = "",
                    viewModel = messageViewModel,
                    navigateToNodeDetails = { nodeNum -> openInApp("nodes/$nodeNum") },
                    // Quick chat and message filters have no deep link of their own, so the full app opens on this
                    // conversation — the screen those menu items live on.
                    navigateToQuickChatOptions = { openInApp("messages/$contactKey") },
                    navigateToFilterSettings = { openInApp("messages/$contactKey") },
                    onNavigateBack = { finish() },
                )
            }
        }
    }

    /** Opens [path] in the full app and collapses this bubble behind it. */
    private fun openInApp(path: String) {
        startActivity(
            Intent(Intent.ACTION_VIEW, "$DEEP_LINK_BASE_URI/$path".toUri(), this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
        )
        finish()
    }
}
