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

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import org.meshtastic.core.model.SignalSurveySession
import org.meshtastic.core.repository.SignalSurveyBlocker
import org.meshtastic.core.ui.component.Quality
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class SignalSurveyContentTest {

    private fun session(id: Long, readings: Int, visible: Boolean = false, recording: Boolean = false) =
        SurveySessionUi(SignalSurveySession(id, 1_700_000_000_000, null, "LONG_FAST", readings), visible, recording)

    @Test
    fun `an idle survey with no sessions is off and shows no history`() = runComposeUiTest {
        setContent { SignalSurveyContent(SignalSurveyUiState(), SignalSurveyActions()) }

        onNodeWithTag(SIGNAL_SURVEY_SWITCH_TAG).assertIsOff()
        onNodeWithText("0 readings").assertIsDisplayed()
        onNodeWithContentDescription("Delete").assertDoesNotExist()
    }

    @Test
    fun `the heatmap switch reflects and changes the map style`() = runComposeUiTest {
        val asked = mutableListOf<Boolean>()
        val actions = SignalSurveyActions(onHeatmapChange = { asked += it })
        setContent { SignalSurveyContent(SignalSurveyUiState(heatmap = true), actions) }

        onNodeWithTag(SIGNAL_SURVEY_HEATMAP_TAG).assertIsOn()
        onNodeWithTag(SIGNAL_SURVEY_HEATMAP_TAG).performClick()

        assertEquals(listOf(false), asked)
    }

    @Test
    fun `the switch asks to start recording`() = runComposeUiTest {
        val asked = mutableListOf<Boolean>()
        val actions = SignalSurveyActions(onRecordingChange = { asked += it })
        setContent { SignalSurveyContent(SignalSurveyUiState(), actions) }

        onNodeWithTag(SIGNAL_SURVEY_SWITCH_TAG).performClick()

        assertEquals(listOf(true), asked)
    }

    @Test
    fun `a recording survey is on and counts the live session`() = runComposeUiTest {
        val state =
            SignalSurveyUiState(
                recording = true,
                sessions =
                listOf(session(2, readings = 12, visible = true, recording = true), session(1, readings = 40)),
            )
        setContent { SignalSurveyContent(state, SignalSurveyActions()) }

        onNodeWithTag(SIGNAL_SURVEY_SWITCH_TAG).assertIsOn()
        // Once in the status line and once on the session's own row; the older session keeps its own count.
        onAllNodesWithTextCount("12 readings", 2)
        onNodeWithText("40 readings").assertIsDisplayed()
    }

    @Test
    fun `a blocked recording says why in place of the count`() = runComposeUiTest {
        val state = SignalSurveyUiState(recording = true, blocker = SignalSurveyBlocker.NO_FIX)
        setContent { SignalSurveyContent(state, SignalSurveyActions()) }

        onNodeWithText("Waiting for a location fix").assertIsDisplayed()
        onNodeWithText("0 readings").assertDoesNotExist()
    }

    @Test
    fun `a missing location permission offers to grant it`() = runComposeUiTest {
        val fixed = mutableListOf<SignalSurveyBlocker>()
        val state = SignalSurveyUiState(recording = true, blocker = SignalSurveyBlocker.LOCATION_PERMISSION)
        setContent { SignalSurveyContent(state, SignalSurveyActions(onFixBlocker = { fixed += it })) }

        onNodeWithText("Allow location access to log readings").assertIsDisplayed()
        onNodeWithText("Grant permission").performClick()

        assertEquals(listOf(SignalSurveyBlocker.LOCATION_PERMISSION), fixed)
    }

    @Test
    fun `a blocker the user cannot fix from here offers no fix button`() = runComposeUiTest {
        val state = SignalSurveyUiState(recording = true, blocker = SignalSurveyBlocker.NOT_CONNECTED)
        setContent { SignalSurveyContent(state, SignalSurveyActions()) }

        onNodeWithText("Not connected").assertIsDisplayed()
        onNodeWithTag(SIGNAL_SURVEY_FIX_TAG).assertDoesNotExist()
    }

    @Test
    fun `an idle survey hides a blocker left over from the last run`() = runComposeUiTest {
        val state = SignalSurveyUiState(recording = false, blocker = SignalSurveyBlocker.LOCATION_OFF)
        setContent { SignalSurveyContent(state, SignalSurveyActions()) }

        onNodeWithTag(SIGNAL_SURVEY_FIX_TAG).assertDoesNotExist()
    }

    @Test
    fun `the legend names the four signal bands`() = runComposeUiTest {
        setContent { SignalSurveyContent(SignalSurveyUiState(), SignalSurveyActions()) }

        listOf("Good", "Fair", "Bad", "None").forEach { onNodeWithText(it).assertIsDisplayed() }
    }

    @Test
    fun `a session row toggles its layer and exports`() = runComposeUiTest {
        val shown = mutableListOf<Pair<Long, Boolean>>()
        val exported = mutableListOf<Long>()
        val actions =
            SignalSurveyActions(
                onVisibleChange = { id, visible -> shown += id to visible },
                onExport = { exported += it },
            )
        setContent { SignalSurveyContent(SignalSurveyUiState(sessions = listOf(session(5, readings = 3))), actions) }

        onNodeWithContentDescription("Show Layer").performClick()
        onNodeWithContentDescription("Export CSV").performClick()

        assertEquals(listOf(5L to true), shown)
        assertEquals(listOf(5L), exported)
    }

    @Test
    fun `a session is deleted only after the confirmation`() = runComposeUiTest {
        val deleted = mutableListOf<Long>()
        val state = SignalSurveyUiState(sessions = listOf(session(5, readings = 3)))
        setContent { SignalSurveyContent(state, SignalSurveyActions(onDelete = { deleted += it })) }

        onNodeWithContentDescription("Delete").performClick()
        assertEquals(emptyList(), deleted)
        onNodeWithText("Cancel").performClick()
        assertEquals(emptyList(), deleted)

        onNodeWithContentDescription("Delete").performClick()
        onNodeWithText("Delete this session and its readings?").assertIsDisplayed()
        onNodeWithTag(SIGNAL_SURVEY_CONFIRM_DELETE_TAG).performClick()
        assertEquals(listOf(5L), deleted)
    }

    @Test
    fun `the session being recorded cannot be deleted`() = runComposeUiTest {
        val state = SignalSurveyUiState(recording = true, sessions = listOf(session(5, readings = 3, recording = true)))
        setContent { SignalSurveyContent(state, SignalSurveyActions()) }

        onNodeWithContentDescription("Delete").assertIsNotEnabled()
        onNodeWithContentDescription("Export CSV").assertIsEnabled()
    }

    @Test
    fun `a tapped square shows its quality signal and counts`() = runComposeUiTest {
        val cell =
            SurveyCell(
                south = 0.0,
                west = 0.0,
                north = 1.0,
                east = 1.0,
                bestSnr = 4.5f,
                rssi = -101,
                samples = 3,
                senders = 2,
                lastHeardMillis = 1_700_000_000_000,
            )
        var dismissed = 0
        setContent { SignalSurveyCellCard(SurveyCellUi("a", cell, Quality.GOOD), SurveyLinks(), { dismissed++ }) }

        onNodeWithText("Good").assertIsDisplayed()
        onNodeWithText("SNR 4.5 dB").assertIsDisplayed()
        onNodeWithText("RSSI -101 dBm").assertIsDisplayed()
        onNodeWithText("3 readings").assertIsDisplayed()
        onNodeWithText("Heard 2 nodes").assertIsDisplayed()
        onNodeWithText("Heard directly here").assertDoesNotExist()
        onNodeWithContentDescription("Close").performClick()
        assertEquals(1, dismissed)
    }

    @Test
    fun `a tapped square names the nodes heard directly and counts those without a position`() = runComposeUiTest {
        val cell = SurveyCell(0.0, 0.0, 1.0, 1.0, 4.5f, -101, 3, 3, 0)
        val link = SurveyLink(7, "DEEP", 0.5, 0.5, 0.6, 0.6, Quality.FAIR, 11_000.0)
        val links = SurveyLinks(listOf(link), unplaced = 2)
        setContent { SignalSurveyCellCard(SurveyCellUi("a", cell, Quality.GOOD), links, {}) }

        onNodeWithText("Heard directly here").assertIsDisplayed()
        onNodeWithText("DEEP").assertIsDisplayed()
        onNodeWithText("2 more heard directly share no position").assertIsDisplayed()
    }

    private fun androidx.compose.ui.test.ComposeUiTest.onAllNodesWithTextCount(text: String, count: Int) {
        assertEquals(count, onAllNodes(androidx.compose.ui.test.hasText(text)).fetchSemanticsNodes().size)
    }
}
