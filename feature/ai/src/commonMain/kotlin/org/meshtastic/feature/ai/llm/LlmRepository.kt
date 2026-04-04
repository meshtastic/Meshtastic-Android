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

import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for on-device LLM inference.
 * Implementations use LiteRT-LM (Android) or other runtimes.
 */
interface LlmRepository {
    
    /**
     * Check if the LLM model is available and loaded.
     */
    val isModelAvailable: Flow<Boolean>
    
    /**
     * Generate a text response from a prompt.
     * @param prompt The input prompt
     * @param maxTokens Maximum tokens to generate (default 50 for quick replies)
     * @return Flow of generated tokens (streaming response)
     */
    suspend fun generateResponse(
        prompt: String,
        maxTokens: Int = DEFAULT_MAX_TOKENS
    ): Flow<String>
    
    /**
     * Generate smart reply suggestions based on conversation context.
     * @param context The conversation context (recent messages, current node info)
     * @param maxSuggestions Maximum number of suggestions to generate
     * @param languageCode ISO 639-1 language code for localized replies (e.g., "en", "it", "de")
     * @return List of suggested quick replies
     */
    suspend fun generateSmartReplies(
        context: ConversationContext,
        maxSuggestions: Int = 3,
        languageCode: String = "en"
    ): List<String>
    
    /**
     * Fires a warm-up inference pass in background to pre-compile GPU kernels.
     * Call as early as possible (e.g., Application.onCreate or first screen shown).
     * Safe to call multiple times — no-op if already warmed up.
     */
    fun warmUp() { /* default no-op for non-Android implementations */ }

    /**
     * Release model resources (call when done with inference).
     */
    suspend fun close()
    
    companion object {
        const val DEFAULT_MAX_TOKENS = 50
    }
}

/**
 * Context for generating smart replies.
 */
data class ConversationContext(
    val recentMessages: List<MessageContext>,
    val currentNodeInfo: NodeContext? = null
)

/**
 * A message in the conversation context.
 */
data class MessageContext(
    val text: String,
    val isFromMe: Boolean,
    val senderName: String? = null,
    val timestamp: Long
)

/**
 * Context about the current mesh node.
 */
data class NodeContext(
    val nodeName: String,
    val nodeNum: Int,
    val isOnline: Boolean,
    val batteryLevel: Int? = null,
    val distance: Float? = null
)
