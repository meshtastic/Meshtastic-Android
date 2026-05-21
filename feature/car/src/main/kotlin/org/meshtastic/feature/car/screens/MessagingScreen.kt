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
package org.meshtastic.feature.car.screens

import android.os.Handler
import android.os.Looper
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.Header
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import org.meshtastic.feature.car.R
import org.meshtastic.feature.car.model.MessagingUiState

class MessagingScreen(
    carContext: CarContext,
    private val stateProvider: () -> MessagingUiState,
    private val onConversationClick: (String) -> Unit,
    private val onChannelSelected: (Int) -> Unit,
) : Screen(carContext) {

    private val handler = Handler(Looper.getMainLooper())
    private var invalidationPending = false

    fun requestInvalidation() {
        if (!invalidationPending) {
            invalidationPending = true
            handler.postDelayed(
                {
                    invalidationPending = false
                    invalidate()
                },
                DEBOUNCE_MS,
            )
        }
    }

    override fun onGetTemplate(): Template {
        val state = stateProvider()

        val listBuilder = ItemList.Builder()

        state.conversations.take(MAX_CONVERSATIONS).forEach { conversation ->
            listBuilder.addItem(
                Row.Builder()
                    .setTitle(conversation.displayName)
                    .addText(conversation.lastMessage)
                    .setBrowsable(true)
                    .setOnClickListener { onConversationClick(conversation.contactKey) }
                    .build(),
            )
        }

        val templateBuilder =
            ListTemplate.Builder()
                .setSingleList(listBuilder.build())
                .setHeader(
                    Header.Builder()
                        .setTitle(carContext.getString(R.string.car_tab_messages))
                        .setStartHeaderAction(Action.BACK)
                        .build(),
                )

        if (state.conversations.isEmpty()) {
            templateBuilder.setLoading(false)
        }

        return templateBuilder.build()
    }

    companion object {
        private const val DEBOUNCE_MS = 300L
        private const val MAX_CONVERSATIONS = 10
    }
}
