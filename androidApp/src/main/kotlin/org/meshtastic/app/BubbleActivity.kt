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

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.compose.viewmodel.koinViewModel
import org.meshtastic.core.ui.theme.AppTheme
import org.meshtastic.core.ui.theme.MODE_DYNAMIC
import org.meshtastic.core.ui.viewmodel.UIViewModel
import org.meshtastic.feature.messaging.MessageScreen
import org.meshtastic.feature.messaging.MessageViewModel

/**
 * Hosts a single conversation inside a notification bubble.
 *
 * Bubbles require their activity to be resizeable, embeddable and document-launched, which the launcher activity cannot
 * be without changing how the whole app behaves in recents — so this is a separate, deliberately small host. It renders
 * only [MessageScreen]: a bubble is a conversation, and every route out of one (node details, quick chat, filter
 * settings) belongs in the full app, so those simply collapse the bubble instead.
 */
class BubbleActivity : AppCompatActivity() {

    private val model: UIViewModel by viewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val contactKey = intent?.data?.lastPathSegment
        if (contactKey.isNullOrEmpty()) {
            finish()
            return
        }

        setContent {
            val theme by model.theme.collectAsStateWithLifecycle()
            val dark =
                when (theme) {
                    AppCompatDelegate.MODE_NIGHT_YES -> true
                    AppCompatDelegate.MODE_NIGHT_NO -> false
                    else -> isSystemInDarkTheme()
                }
            AppTheme(dynamicColor = theme == MODE_DYNAMIC, darkTheme = dark) {
                val messageViewModel: MessageViewModel = koinViewModel(key = "bubble-messages-$contactKey")
                messageViewModel.setContactKey(contactKey)
                MessageScreen(
                    contactKey = contactKey,
                    message = "",
                    viewModel = messageViewModel,
                    navigateToNodeDetails = { finish() },
                    navigateToQuickChatOptions = { finish() },
                    navigateToFilterSettings = { finish() },
                    onNavigateBack = { finish() },
                )
            }
        }
    }
}
