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
package org.meshtastic.core.ui.component

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import org.meshtastic.core.navigation.ConnectionsRoute
import org.meshtastic.core.navigation.ContactsRoute
import org.meshtastic.core.navigation.MapRoute
import org.meshtastic.core.navigation.MultiBackstack
import org.meshtastic.core.navigation.NodesRoute
import org.meshtastic.core.navigation.SettingsRoute
import org.meshtastic.core.navigation.rememberMultiBackstack
import org.meshtastic.core.ui.theme.AppTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class MeshtasticNavDisplayTabStateTest {

    private val probes = mutableMapOf<String, ProbeViewModel>()

    @Test
    fun tabKeepsSavedStateAndViewModelsWhileAnotherTabIsShown() = runComposeUiTest {
        lateinit var multiBackstack: MultiBackstack
        setContent { multiBackstack = TwoTabHost() }

        onNodeWithText("nodes saveable=0").performClick()
        onNodeWithText("nodes saveable=1").assertIsDisplayed()
        val nodesViewModel = probes.getValue("nodes")
        assertEquals(1, nodesViewModel.clicks)

        runOnIdle { multiBackstack.navigateTopLevel(MapRoute.Map()) }
        waitForIdle()
        onNodeWithText("map tab").assertIsDisplayed()

        runOnIdle { multiBackstack.navigateTopLevel(NodesRoute.Nodes) }
        waitForIdle()

        assertFalse(nodesViewModel.cleared, "leaving the tab cleared its ViewModel")
        assertSame(nodesViewModel, probes.getValue("nodes"))
        assertEquals(1, probes.getValue("nodes").clicks)
        onNodeWithText("nodes saveable=1").assertIsDisplayed()
    }

    @Test
    fun poppedEntryDropsItsSavedStateAndViewModel() = runComposeUiTest {
        lateinit var multiBackstack: MultiBackstack
        setContent { multiBackstack = TwoTabHost() }

        runOnIdle { multiBackstack.activeBackStack.add(NodesRoute.NodeDetail(destNum = 1)) }
        waitForIdle()
        onNodeWithText("detail saveable=0").performClick()
        onNodeWithText("detail saveable=1").assertIsDisplayed()
        val detailViewModel = probes.getValue("detail")

        runOnIdle { multiBackstack.goBack() }
        waitForIdle()
        assertTrue(detailViewModel.cleared, "popping the entry kept its ViewModel")

        runOnIdle { multiBackstack.activeBackStack.add(NodesRoute.NodeDetail(destNum = 1)) }
        waitForIdle()
        onNodeWithText("detail saveable=0").assertIsDisplayed()
        assertNotSame(detailViewModel, probes.getValue("detail"))
    }

    @Test
    fun entryRemovedFromAHiddenTabDropsItsViewModel() = runComposeUiTest {
        lateinit var multiBackstack: MultiBackstack
        setContent { multiBackstack = TwoTabHost() }

        runOnIdle { multiBackstack.activeBackStack.add(NodesRoute.NodeDetail(destNum = 1)) }
        waitForIdle()
        val detailViewModel = probes.getValue("detail")

        runOnIdle { multiBackstack.navigateTopLevel(MapRoute.Map()) }
        waitForIdle()
        assertFalse(detailViewModel.cleared, "leaving the tab cleared its ViewModel")

        runOnIdle { multiBackstack.handleDeepLink(listOf(NodesRoute.Nodes)) }
        waitForIdle()
        onNodeWithText("nodes saveable=0").assertIsDisplayed()
        assertTrue(detailViewModel.cleared, "an entry removed while its tab was hidden kept its ViewModel")
    }

    @Composable
    private fun TwoTabHost(): MultiBackstack {
        val multiBackstack = rememberMultiBackstack(NodesRoute.Nodes)
        AppTheme {
            MeshtasticNavDisplay(
                multiBackstack = multiBackstack,
                entryProvider =
                entryProvider<NavKey> {
                    entry<NodesRoute.Nodes> { Probe(name = "nodes") }
                    entry<NodesRoute.NodeDetail> { Probe(name = "detail") }
                    entry<MapRoute.Map> { Text("map tab") }
                    entry<ContactsRoute.Contacts> { Text("messages tab") }
                    entry<SettingsRoute.Settings> { Text("settings tab") }
                    entry<ConnectionsRoute.Connections> { Text("connections tab") }
                },
            )
        }
        return multiBackstack
    }

    @Composable
    private fun Probe(name: String) {
        var saveable by rememberSaveable { mutableIntStateOf(0) }
        val viewModel = viewModel { ProbeViewModel(createSavedStateHandle()) }
        SideEffect { probes[name] = viewModel }
        Button(
            onClick = {
                saveable++
                viewModel.clicks++
            },
        ) {
            Text("$name saveable=$saveable")
        }
    }
}

private class ProbeViewModel(private val handle: SavedStateHandle) : ViewModel() {
    var cleared = false
        private set

    var clicks: Int
        get() = handle[CLICKS_KEY] ?: 0
        set(value) {
            handle[CLICKS_KEY] = value
        }

    override fun onCleared() {
        cleared = true
    }

    private companion object {
        const val CLICKS_KEY = "clicks"
    }
}
