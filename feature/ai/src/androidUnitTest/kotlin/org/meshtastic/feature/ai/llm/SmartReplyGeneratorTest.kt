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

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartReplyGeneratorTest {
    
    @Test
    fun `fallback replies returned when model unavailable`() = runBlocking {
        // Given a repository that reports model unavailable
        val mockRepository = object : LlmRepository {
            override val isModelAvailable = flowOf(false)
            
            override suspend fun generateResponse(prompt: String, maxTokens: Int) = flowOf("")
            
            override suspend fun generateSmartReplies(
                context: ConversationContext,
                maxSuggestions: Int
            ) = emptyList<String>()
            
            override suspend fun close() {}
        }
        
        val generator = SmartReplyGenerator(mockRepository)
        
        // When generating replies
        val result = generator.suggestReplies(
            receivedMessage = "Hello!",
            senderName = "Test",
            recentHistory = emptyList()
        )
        
        // Then fallback replies are returned
        assertTrue("Should return fallback replies", result.isSuccess)
        val replies = result.getOrThrow()
        assertFalse("Should have non-empty replies", replies.isEmpty())
        assertTrue("Should contain 'Ok'", replies.contains("Ok"))
    }
    
    @Test
    fun `context is built correctly`() {
        val context = ConversationContext(
            recentMessages = listOf(
                MessageContext(
                    text = "Hello",
                    isFromMe = false,
                    senderName = "Alice",
                    timestamp = System.currentTimeMillis()
                )
            ),
            currentNodeInfo = NodeContext(
                nodeName = "Node1",
                nodeNum = 123,
                isOnline = true,
                batteryLevel = 80
            )
        )
        
        assertTrue("Should have messages", context.recentMessages.isNotEmpty())
        assertTrue("Should have node info", context.currentNodeInfo != null)
        assertTrue("Node should be online", context.currentNodeInfo?.isOnline == true)
    }
}
