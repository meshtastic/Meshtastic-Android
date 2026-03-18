package org.meshtastic.app.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.meshtastic.feature.settings.navigation.channelsGraph
import org.meshtastic.feature.connections.navigation.connectionsGraph
import org.meshtastic.feature.messaging.navigation.contactsGraph
import org.meshtastic.feature.firmware.navigation.firmwareGraph
import org.meshtastic.feature.map.navigation.mapGraph
import org.meshtastic.feature.node.navigation.nodesGraph
import org.meshtastic.feature.settings.navigation.settingsGraph
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NavigationAssemblyTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun verifyNavigationGraphsAssembleWithoutCrashing() {
        composeTestRule.setContent {
            val backStack = rememberNavBackStack(org.meshtastic.core.navigation.NodesRoutes.NodesGraph as NavKey)
            entryProvider<NavKey> {
                contactsGraph(backStack, emptyFlow())
                nodesGraph(
                    backStack = backStack,
                    scrollToTopEvents = emptyFlow(),
                    nodeMapScreen = { _, _ -> }
                )
                mapGraph(backStack)
                channelsGraph(backStack)
                connectionsGraph(backStack)
                settingsGraph(backStack)
                firmwareGraph(backStack)
            }
        }
    }
}
