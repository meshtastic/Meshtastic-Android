package com.geeksville.mesh.ui

import androidx.compose.Model
import androidx.compose.mutableStateOf
import com.geeksville.mesh.service.MeshUser
import com.geeksville.mesh.service.NodeInfo
import com.geeksville.mesh.service.Position
import java.util.*

// defines the screens we have in the app
sealed class Screen {
    object Home : Screen()
    // object Settings : Screen()
}

@Model
object AppStatus {
    var currentScreen: Screen = Screen.Home
}

data class TextMessage(val date: Date, val from: String, val text: String)

/// FIXME - figure out how to merge this staate with the AppStatus Model
object UIState {

    private val testPositions = arrayOf(
        Position(32.776665, -96.796989, 35), // dallas
        Position(32.960758, -96.733521, 35), // richardson
        Position(
            32.912901,
            -96.781776,
            35
        ) // north dallas
    )

    val testNodeNoPosition = NodeInfo(
        8,
        MeshUser(
            "+6508765308".format(8),
            "Kevin MesterNoLoc",
            "KLO"
        ),
        null,
        12345
    )

    val testNodes = testPositions.mapIndexed { index, it ->
        NodeInfo(
            9 + index,
            MeshUser(
                "+65087653%02d".format(9 + index),
                "Kevin Mester$index",
                "KM$index"
            ),
            it,
            12345
        )
    }

    val testTexts = listOf(
        TextMessage(Date(), "+6508675310", "I found the cache"),
        TextMessage(Date(), "+6508675311", "Help! I've fallen and I can't get up.")
    )

    /// A map from nodeid to to nodeinfo
    val nodes = mutableStateOf(testNodes.map { it.user!!.id to it }.toMap())

    val messages = mutableStateOf(testTexts)

    /// Are we connected to our radio device
    var isConnected = mutableStateOf(false)

}

/**
 * Temporary solution pending navigation support.
 */
fun navigateTo(destination: Screen) {
    AppStatus.currentScreen = destination
}
