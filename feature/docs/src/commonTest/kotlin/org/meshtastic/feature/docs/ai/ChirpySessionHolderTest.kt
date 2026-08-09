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
package org.meshtastic.feature.docs.ai

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.feature.docs.model.AIDocAssistantResult
import org.meshtastic.feature.docs.model.ChirpyRole
import org.meshtastic.feature.docs.model.ModelReadiness
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Requests are owned by the holder, not by the pane that submitted them: the docs layout disposes the list pane as soon
 * as a page opens, so a request launched from a `rememberCoroutineScope()` was cancelled mid-answer and stranded
 * `isLoading = true` in this app-wide singleton, wedging the composer for the rest of the process.
 */
class ChirpySessionHolderTest {

    @Test
    fun `submit streams the answer into the shared session`() = runTest {
        val results = listOf(partial("Mesh"), success("Meshtastic uses LoRa."))
        val holder = holderFor(FakeDocAssistant(stream = results.asFlow()))

        holder.sessionState = holder.sessionState.copy(draftQuestion = "What is Meshtastic?")
        holder.submit(currentPageId = null)
        advanceUntilIdle()

        assertEquals(listOf(ChirpyRole.USER, ChirpyRole.ASSISTANT), holder.sessionState.messages.map { it.role })
        assertEquals("Meshtastic uses LoRa.", holder.sessionState.messages.last().text)
        assertEquals("", holder.sessionState.draftQuestion)
        assertFalse(holder.sessionState.isLoading)
    }

    @Test
    fun `submit clears loading when the stream ends without a terminal result`() = runTest {
        val holder = holderFor(FakeDocAssistant(stream = emptyFlow()))

        holder.sessionState = holder.sessionState.copy(draftQuestion = "Anyone there?")
        holder.submit(currentPageId = null)
        advanceUntilIdle()

        assertFalse(holder.sessionState.isLoading, "a stream with no terminal result must not wedge the composer")
    }

    @Test
    fun `a thrown failure clears loading instead of wedging the composer`() = runTest {
        val holder = holderFor(FakeDocAssistant(stream = flow { throw IllegalStateException("assistant blew up") }))

        holder.sessionState = holder.sessionState.copy(draftQuestion = "Why?")
        holder.submit(currentPageId = null)
        advanceUntilIdle()

        assertFalse(holder.sessionState.isLoading, "a failed request must not block every later question")

        // The composer is usable again: the next question is accepted rather than dropped by the isLoading guard.
        holder.sessionState = holder.sessionState.copy(draftQuestion = "Again?")
        holder.submit(currentPageId = null)

        assertEquals(2, holder.sessionState.messages.count { it.role == ChirpyRole.USER })
    }

    @Test
    fun `submit ignores a blank draft and a request already in flight`() = runTest {
        val holder = holderFor(FakeDocAssistant(stream = listOf(partial("...")).asFlow()))

        holder.submit(currentPageId = null)
        assertTrue(holder.sessionState.messages.isEmpty())

        holder.sessionState = holder.sessionState.copy(draftQuestion = "First?")
        holder.submit(currentPageId = null)
        holder.sessionState = holder.sessionState.copy(draftQuestion = "Second?")
        holder.submit(currentPageId = null)

        assertEquals(1, holder.sessionState.messages.count { it.role == ChirpyRole.USER })
        assertEquals("Second?", holder.sessionState.draftQuestion)
    }

    @Test
    fun `introduce runs once and resets the assistant session`() = runTest {
        val assistant = FakeDocAssistant(stream = emptyFlow())
        val holder = holderFor(assistant)

        holder.introduce()
        advanceUntilIdle()
        holder.introduce()
        advanceUntilIdle()

        assertEquals(1, assistant.resetCount)
        assertEquals(1, holder.sessionState.messages.size)
        assertFalse(holder.sessionState.isLoading)
    }

    private fun kotlinx.coroutines.test.TestScope.holderFor(assistant: AIDocAssistant): ChirpySessionHolder {
        val dispatcher = StandardTestDispatcher(testScheduler)
        return ChirpySessionHolder(
            aiAssistant = assistant,
            dispatchers = CoroutineDispatchers(io = dispatcher, main = dispatcher, default = dispatcher),
        )
    }
}

private fun partial(answer: String) = AIDocAssistantResult.Partial(answer, emptyList(), usedOnDeviceModel = true)

private fun success(answer: String) = AIDocAssistantResult.Success(answer, emptyList(), usedOnDeviceModel = true)

private class FakeDocAssistant(
    private val stream: Flow<AIDocAssistantResult>,
    private val answer: AIDocAssistantResult = success("Hi, I'm Chirpy."),
) : AIDocAssistant {
    var resetCount = 0
        private set

    override val modelStatus: StateFlow<ModelReadiness> = MutableStateFlow(ModelReadiness.Available)

    override suspend fun isSupported(): Boolean = true

    override suspend fun answer(question: String, currentPageId: String?): AIDocAssistantResult = answer

    override fun answerStream(question: String, currentPageId: String?): Flow<AIDocAssistantResult> = stream

    override fun resetSession() {
        resetCount++
    }
}
