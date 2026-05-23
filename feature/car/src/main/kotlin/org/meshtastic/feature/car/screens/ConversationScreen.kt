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

import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.constraints.ConstraintManager
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.Header
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.LongMessageTemplate
import androidx.car.app.model.ParkedOnlyOnClickListener
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import org.meshtastic.core.common.util.DateFormatter
import org.meshtastic.feature.car.R
import org.meshtastic.feature.car.service.MessageSnapshot

class ConversationScreen(
    carContext: CarContext,
    private val conversationName: String,
    private val messagesProvider: () -> List<MessageSnapshot>,
    private val onVoiceReply: () -> Unit,
    private val onQuickReply: (String) -> Unit,
    private val onReadAloud: () -> Unit,
) : Screen(carContext) {

    override fun onGetTemplate(): Template {
        val listLimit =
            carContext
                .getCarService(ConstraintManager::class.java)
                .getContentLimit(ConstraintManager.CONTENT_LIMIT_TYPE_LIST)
        val messages = messagesProvider().takeLast(listLimit.coerceAtMost(MAX_MESSAGES))

        val actionStrip = buildActionStrip()

        if (messages.size > MAX_LIST_MESSAGES) {
            return buildLongMessageTemplate(messages, actionStrip)
        }

        return buildListTemplate(messages, actionStrip)
    }

    private fun buildActionStrip(): ActionStrip = ActionStrip.Builder()
        .addAction(
            Action.Builder()
                .setTitle(carContext.getString(R.string.car_voice_reply))
                .setOnClickListener(
                    ParkedOnlyOnClickListener.create {
                        onVoiceReply()
                        CarToast.makeText(
                            carContext,
                            carContext.getString(R.string.car_message_sent),
                            CarToast.LENGTH_SHORT,
                        )
                            .show()
                    },
                )
                .build(),
        )
        .addAction(
            Action.Builder()
                .setTitle(carContext.getString(R.string.car_quick_reply))
                .setOnClickListener(
                    ParkedOnlyOnClickListener.create {
                        onQuickReply("")
                        CarToast.makeText(
                            carContext,
                            carContext.getString(R.string.car_message_sent),
                            CarToast.LENGTH_SHORT,
                        )
                            .show()
                    },
                )
                .build(),
        )
        .addAction(
            Action.Builder()
                .setTitle(carContext.getString(R.string.car_read_aloud))
                .setOnClickListener { onReadAloud() }
                .build(),
        )
        .build()

    private fun buildListTemplate(messages: List<MessageSnapshot>, actionStrip: ActionStrip): Template {
        val listBuilder = ItemList.Builder()
        messages.forEach { msg ->
            val timeText =
                if (msg.timestamp != 0L) {
                    " • ${DateFormatter.formatRelativeTime(msg.timestamp)}"
                } else {
                    ""
                }
            listBuilder.addItem(Row.Builder().setTitle(msg.senderName).addText("${msg.text}$timeText").build())
        }

        return ListTemplate.Builder()
            .setSingleList(listBuilder.build())
            .setHeader(Header.Builder().setTitle(conversationName).setStartHeaderAction(Action.BACK).build())
            .setActionStrip(actionStrip)
            .build()
    }

    private fun buildLongMessageTemplate(messages: List<MessageSnapshot>, actionStrip: ActionStrip): Template {
        val fullText =
            messages.joinToString("\n\n") { msg ->
                val time = if (msg.timestamp != 0L) DateFormatter.formatRelativeTime(msg.timestamp) else ""
                "${msg.senderName} • $time\n${msg.text}"
            }

        return LongMessageTemplate.Builder(fullText)
            .setTitle(conversationName)
            .setHeaderAction(Action.BACK)
            .setActionStrip(actionStrip)
            .build()
    }

    companion object {
        private const val MAX_MESSAGES = 5
        private const val MAX_LIST_MESSAGES = 5
    }
}
