package com.geeksville.mesh.ui

import android.util.Base64
import androidx.compose.Model
import androidx.compose.mutableStateOf
import com.geeksville.mesh.*
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import java.util.*


data class ScreenInfo(val icon: Int, val label: String)

// defines the screens we have in the app
object Screen {
    val settings = ScreenInfo(R.drawable.ic_twotone_settings_applications_24, "Settings")
    val channel = ScreenInfo(R.drawable.ic_twotone_contactless_24, "Channel")
    val users = ScreenInfo(R.drawable.ic_twotone_people_24, "Users")
    val messages = ScreenInfo(R.drawable.ic_twotone_message_24, "Messages")
}


@Model
object AppStatus {
    var currentScreen: ScreenInfo = Screen.messages
}

data class TextMessage(val date: Date, val from: String, val text: String)

/// FIXME - figure out how to merge this staate with the AppStatus Model
object UIState {

    /// Kinda ugly - created in the activity but used from Compose - figure out if there is a cleaner way GIXME
    lateinit var googleSignInClient: GoogleSignInClient

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
    val isConnected = mutableStateOf(false)

    /// various radio settings (including the channel)
    val radioConfig = mutableStateOf(MeshProtos.RadioConfig.getDefaultInstance())

    /// our name in hte radio
    /// Note, we generate owner initials automatically for now
    val ownerName = mutableStateOf("fixme readfromprefs")

    /// Return an URL that represents the current channel values
    val channelUrl
        get(): String {
            val channelBytes = radioConfig.value.channelSettings.toByteArray()
            val enc = Base64.encodeToString(channelBytes, Base64.URL_SAFE + Base64.NO_WRAP)

            return "https://www.meshtastic.org/c/$enc"
        }
}

/**
 * Temporary solution pending navigation support.
 */
fun navigateTo(destination: ScreenInfo) {
    AppStatus.currentScreen = destination
}
