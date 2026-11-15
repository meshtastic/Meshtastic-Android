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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.meshtastic.core.common.di.asServiceScope
import org.meshtastic.core.model.Node
import org.meshtastic.core.model.snrLimit
import org.meshtastic.core.testing.FakeNodeRepository
import org.meshtastic.core.ui.component.Quality
import org.meshtastic.proto.Config.LoRaConfig.ModemPreset
import org.meshtastic.proto.Position
import org.meshtastic.proto.User
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
    private val visibility = SignalSurveyVisibility(survey, CoroutineScope(Dispatchers.Unconfined).asServiceScope())

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val nodes = FakeNodeRepository()

    private fun viewModel() =
        SignalSurveyViewModel(survey, visibility, SignalSurveyCellSource(survey, visibility), nodes)

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
    fun `a tapped square stays selected as the map zooms and its square grows`() = runTest {
        survey.add(1, reading(30.0, snr = 5f))
        visibility.sessionIds.value = setOf(1)
        val viewModel = viewModel()

        viewModel.selectedCell.test {
            assertNull(awaitItem())
            viewModel.cells.test {
                viewModel.selectCell(expectMostRecentItem().single().id)
                viewModel.setCamera(zoom = 11.0, latitude = 30.0)
                cancelAndIgnoreRemainingEvents()
            }

            val selected = expectMostRecentItem()
            assertTrue(selected != null && selected.cell.north - selected.cell.south > 50 / 111_320.0 * 2)
        }
    }

    @Test
    fun `a tapped square zoomed into keeps the smaller square that holds its readings`() = runTest {
        // Off the center of the large square it falls in.
        survey.add(1, reading(30.0001, lon = -95.0001, snr = 5f))
        visibility.sessionIds.value = setOf(1)
        val viewModel = viewModel()
        viewModel.setCamera(zoom = 11.0, latitude = 30.0)

        viewModel.selectedCell.test {
            assertNull(awaitItem())
            viewModel.cells.test {
                viewModel.selectCell(expectMostRecentItem().single().id)
                viewModel.setCamera(zoom = 18.0, latitude = 30.0)
                cancelAndIgnoreRemainingEvents()
            }

            val selected = expectMostRecentItem()
            assertTrue(selected != null && selected.cell.north - selected.cell.south < 51 / 111_320.0)
        }
    }

    @Test
    fun `a tapped square links to the nodes heard directly that share a position`() = runTest {
        val relayed = reading(30.0, snr = 5f).copy(fromNum = 9, direct = false)
        survey.add(
            1,
            reading(30.0, snr = 5f).copy(fromNum = 1),
            // A few dB under LongFast's floor rates Fair.
            reading(30.0, snr = ModemPreset.LONG_FAST.snrLimit - 3f).copy(fromNum = 2),
            reading(30.0, snr = 5f).copy(fromNum = 3),
            relayed,
        )
        visibility.sessionIds.value = setOf(1)
        nodes.setNodes(
            listOf(placed(1, "NEAR", 30.001), placed(2, "FAR", 30.05), Node(num = 3), placed(9, "RELAY", 30.0)),
        )
        val viewModel = viewModel()

        viewModel.selectedLinks.test {
            assertTrue(awaitItem().links.isEmpty())
            viewModel.cells.test { viewModel.selectCell(expectMostRecentItem().single().id) }

            val links = expectMostRecentItem()
            // Nearest first; the relay's copy draws no line, and node 3 shares no position.
            assertEquals(listOf("NEAR", "FAR"), links.links.map { it.name })
            assertEquals(listOf(Quality.GOOD, Quality.FAIR), links.links.map { it.quality })
            assertEquals(1, links.unplaced)
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
    fun `the state marks each session's visibility and the one recording`() = runTest {
        survey.add(1, reading(30.0, snr = 5f))
        survey.add(2, reading(30.0, snr = 5f), reading(30.1, snr = 5f))
        survey.add(3, reading(30.0, snr = 5f))
        visibility.sessionIds.value = setOf(1)
        survey.recording.value = true
        survey.activeSessionId.value = 2

        viewModel().state.test {
            val byId = expectMostRecentItem().let { state -> state.sessions.associateBy { it.session.id } to state }
            val (sessions, state) = byId
            assertEquals(setOf(2L), sessions.filterValues { it.recording }.keys)
            // Session 2 is shown because it is recording; session 3 stays hidden.
            assertEquals(setOf(1L, 2L), sessions.filterValues { it.visible }.keys)
            // While recording the count is the live session's own.
            assertEquals(2, state.readingCount)
        }
    }

    @Test
    fun `a screen opened mid-recording leaves a hidden session hidden`() = runTest {
        survey.add(7, reading(30.0, snr = 5f))
        survey.activeSessionId.value = 7
        viewModel()
        viewModel().setVisible(7, false)

        // The AR view opens its own view model while the survey keeps recording.
        viewModel()

        assertFalse(7 in visibility.sessionIds.value)
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

        // The fake starts each session at its id.
        val lines = viewModel.csv(1, startedAtMillis = 1)?.decodeToString()?.trim()?.lines().orEmpty()

        assertEquals(SURVEY_CSV_HEADER, lines.first())
        assertEquals(listOf(30.0, 30.1), lines.drop(1).map { it.split(",")[1].toDouble() })
        assertNull(viewModel.csv(2, startedAtMillis = 2))
    }

    @Test
    fun `an export whose session start no longer matches writes nothing`() = runTest {
        survey.add(1, reading(30.0, snr = 5f))

        // Another radio's database can reuse the id for a different session.
        assertNull(viewModel().csv(1, startedAtMillis = 999))
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
                // Two readings share one street square, so it adds one point, not two.
                assertEquals(listOf(2, 1), expectMostRecentItem().sortedBy { it.cell.south }.map { it.cell.samples })
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

    private fun placed(num: Int, name: String, lat: Double) = Node(
        num = num,
        user = User.Builder().also { wb -> wb.short_name = name }.build(),
        position =
        Position.Builder()
            .also { wb ->
                wb.latitude_i = (lat * 1e7).toInt()
                wb.longitude_i = (-95.0 * 1e7).toInt()
            }
            .build(),
    )
}
