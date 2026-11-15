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
package org.meshtastic.feature.map.survey

import app.cash.turbine.test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.meshtastic.core.ui.component.Quality
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SignalSurveyViewModelTest {

    private val survey = FakeSignalSurveyRepository()
    private val visibility = SignalSurveyVisibility()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = SignalSurveyViewModel(survey, visibility, SignalSurveyCellSource(survey, visibility))

    @Test
    fun `a hidden session draws nothing and a shown one draws its cells`() = runTest {
        survey.add(1, reading(30.0, snr = 5f))
        val viewModel = viewModel()

        viewModel.cells.test {
            assertTrue(awaitItem().isEmpty())

            viewModel.setVisible(1, true)
            assertEquals(Quality.GOOD, awaitItem().single().quality)

            viewModel.setVisible(1, false)
            assertTrue(awaitItem().isEmpty())
        }
    }

    @Test
    fun `the session being recorded shows on the map and grows as readings arrive`() = runTest {
        survey.add(7)
        val viewModel = viewModel()

        viewModel.cells.test {
            assertTrue(awaitItem().isEmpty())

            survey.activeSessionId.value = 7
            survey.add(7, reading(30.0, snr = 5f))
            assertEquals(1, expectMostRecentItem().size)

            survey.add(7, reading(30.01, snr = 5f))
            assertEquals(2, awaitItem().size)
        }
        assertTrue(7 in visibility.sessionIds.value)
    }

    @Test
    fun `zooming out merges street cells into one larger cell`() = runTest {
        survey.add(1, reading(30.0001, snr = 5f), reading(30.0020, snr = 5f), reading(30.0040, snr = 5f))
        visibility.sessionIds.value = setOf(1)
        val viewModel = viewModel()

        viewModel.cells.test {
            assertEquals(3, expectMostRecentItem().size)

            viewModel.setCamera(zoom = 11.0, latitude = 30.0)
            assertEquals(1, awaitItem().size)
        }
    }

    @Test
    fun `each session is rated against its own preset`() = runTest {
        // -12 dB clears LongFast's floor and falls short of ShortFast's.
        survey.add(1, reading(30.0, snr = -12f), preset = "LONG_FAST")
        survey.add(2, reading(31.0, snr = -12f), preset = "SHORT_FAST")
        visibility.sessionIds.value = setOf(1, 2)

        viewModel().cells.test {
            val byLatitude = expectMostRecentItem().sortedBy { it.cell.south }
            assertEquals(Quality.GOOD, byLatitude[0].quality)
            assertTrue(byLatitude[1].quality != Quality.GOOD)
        }
    }

    @Test
    fun `a tapped cell is selected until dismissed`() = runTest {
        survey.add(1, reading(30.0, snr = 5f))
        visibility.sessionIds.value = setOf(1)
        val viewModel = viewModel()

        viewModel.selectedCell.test {
            assertNull(awaitItem())
            viewModel.cells.test {
                val id = expectMostRecentItem().single().id
                viewModel.selectCell(id)
            }
            assertEquals(5f, awaitItem()?.cell?.bestSnr)

            viewModel.selectCell(null)
            assertNull(awaitItem())
        }
    }

    @Test
    fun `deleting a session hides it and removes it`() = runTest {
        survey.add(1, reading(30.0, snr = 5f))
        visibility.sessionIds.value = setOf(1)
        val viewModel = viewModel()

        viewModel.delete(1)

        assertEquals(listOf(1L), survey.deleted)
        assertFalse(1 in visibility.sessionIds.value)
    }

    @Test
    fun `the state lists sessions newest first with their visibility and the one recording`() = runTest {
        survey.add(1, reading(30.0, snr = 5f))
        survey.add(2, reading(30.0, snr = 5f), reading(30.1, snr = 5f))
        visibility.sessionIds.value = setOf(1)
        survey.recording.value = true
        survey.activeSessionId.value = 2

        viewModel().state.test {
            val state = expectMostRecentItem()
            assertEquals(listOf(2L, 1L), state.sessions.map { it.session.id })
            assertEquals(listOf(true, false), state.sessions.map { it.recording })
            assertTrue(state.sessions.all { it.visible })
            // While recording the count is the live session's own.
            assertEquals(2, state.readingCount)
        }
    }

    @Test
    fun `an idle survey counts the readings of every session`() = runTest {
        survey.add(1, reading(30.0, snr = 5f))
        survey.add(2, reading(30.0, snr = 5f), reading(30.1, snr = 5f))

        viewModel().state.test { assertEquals(3, expectMostRecentItem().readingCount) }
    }

    @Test
    fun `the export is the csv of one session and nothing for an empty one`() = runTest {
        survey.add(1, reading(30.0, snr = 5f), reading(30.1, snr = 5f))
        survey.add(2)
        val viewModel = viewModel()

        val lines = viewModel.csv(1)?.decodeToString()?.trim()?.lines().orEmpty()

        assertEquals(SURVEY_CSV_HEADER, lines.first())
        assertEquals(3, lines.size)
        assertNull(viewModel.csv(2))
    }

    @Test
    fun `the heatmap swaps the squares for one point per street square`() = runTest {
        survey.add(1, reading(30.0001), reading(30.0002), reading(30.0040))
        visibility.sessionIds.value = setOf(1)
        val viewModel = viewModel()
        // Zoomed out so far that the squares merge, which the heatmap must not follow.
        viewModel.setCamera(zoom = 10.0, latitude = 30.0)

        viewModel.cells.test {
            assertEquals(1, expectMostRecentItem().size)
            viewModel.heatPoints.test {
                assertTrue(awaitItem().isEmpty())

                viewModel.setHeatmap(true)
                assertEquals(2, expectMostRecentItem().size)
            }
            assertTrue(expectMostRecentItem().isEmpty())
        }
        assertTrue(visibility.heatmap.value)
    }

    @Test
    fun `the record switch starts and stops the survey`() = runTest {
        val viewModel = viewModel()

        viewModel.setRecording(true)
        assertTrue(survey.recording.value)
        viewModel.setRecording(false)
        assertFalse(survey.recording.value)
    }
}
