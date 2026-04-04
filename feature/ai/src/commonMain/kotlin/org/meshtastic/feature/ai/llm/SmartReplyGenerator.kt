/*
 * Copyright (c) 2026 Chris7X
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

package org.meshtastic.feature.ai.llm

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock

/**
 * Use case for generating smart replies in messaging context.
 */
class SmartReplyGenerator(
    private val repository: LlmRepository
) {
    
    /**
     * Generate quick reply suggestions for a received message.
     * Optimized for short, actionable responses suitable for mesh messaging.
     * 
     * @param receivedMessage The message to reply to
     * @param senderName Optional name of the sender
     * @param recentHistory Recent conversation context
     * @param languageCode ISO 639-1 language code (e.g., "en", "it", "de") for localized replies
     */
    suspend fun suggestReplies(
        receivedMessage: String,
        senderName: String?,
        recentHistory: List<MessageContext> = emptyList(),
        languageCode: String = "en"
    ): Result<List<String>> = withContext(Dispatchers.IO) {
        runCatching {
            if (!repository.isModelAvailable.first()) {
                return@runCatching getFallbackReplies(languageCode)
            }
            
            // Add the received message as the last message in the context
            val messagesWithContext = recentHistory.takeLast(MAX_CONTEXT_MESSAGES - 1).toMutableList()
            messagesWithContext.add(
                MessageContext(
                    text = receivedMessage,
                    isFromMe = false,
                    senderName = senderName,
                    timestamp = Clock.System.now().toEpochMilliseconds()
                )
            )
            
            val context = ConversationContext(
                recentMessages = messagesWithContext,
                currentNodeInfo = null
            )
            
            repository.generateSmartReplies(context, MAX_SUGGESTIONS, languageCode)
                .ifEmpty { getFallbackReplies(languageCode) }
        }
    }
    
    /**
     * Generate a contextual response for a question or request.
     */
    suspend fun generateResponse(
        message: String,
        context: ConversationContext
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val prompt = buildResponsePrompt(message, context)
            repository.generateResponse(prompt, maxTokens = 100)
                .first()
                .trim()
        }
    }
    
    private fun buildResponsePrompt(message: String, context: ConversationContext, languageCode: String = "en"): String {
        val historyContext = context.recentMessages
            .takeLast(3)
            .joinToString("\n") { msg ->
                val sender = if (msg.isFromMe) "Me" else (msg.senderName ?: "Other")
                "$sender: ${msg.text}"
            }
        
        val languageInstruction = when (languageCode) {
            "it" -> "Rispondi in italiano. "
            "de" -> "Antworte auf Deutsch. "
            "es" -> "Responde en español. "
            "fr" -> "Réponds en français. "
            else -> ""
        }
        
        return buildString {
            append("You are a helpful assistant for Meshtastic mesh network users. ")
            append("Provide short, practical responses suitable for radio messaging. ")
            append("Keep responses under 20 words. ")
            append(languageInstruction)
            append("\n\n")
            if (historyContext.isNotBlank()) {
                append("Recent messages:\n$historyContext\n\n")
            }
            append("Respond to: $message")
        }
    }
    
    private fun getFallbackReplies(languageCode: String = "en"): List<String> = when (languageCode) {
        "it" -> listOf("Ok", "Ricevuto", "Arrivo", "Manda posizione", "Va bene")
        "de" -> listOf("Ok", "Erhalten", "Unterwegs", "Position senden", "Wird gemacht")
        "es" -> listOf("Ok", "Recibido", "En camino", "Enviar ubicación", "Lo haré")
        "fr" -> listOf("Ok", "Reçu", "J'arrive", "Envoie position", "D'accord")
        else -> listOf("Ok", "Received", "On my way", "Send location", "Will do")
    }
    
    companion object {
        const val MAX_SUGGESTIONS = 3
        const val MAX_CONTEXT_MESSAGES = 5
    }
}
