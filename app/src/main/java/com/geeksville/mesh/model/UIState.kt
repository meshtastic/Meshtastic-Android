package com.geeksville.mesh.model

import android.util.Base64
import androidx.compose.mutableStateOf
import com.geeksville.mesh.IMeshService
import com.geeksville.mesh.MeshProtos

/// FIXME - figure out how to merge this staate with the AppStatus Model
object UIState {

    /// Kinda ugly - created in the activity but used from Compose - figure out if there is a cleaner way GIXME
    // lateinit var googleSignInClient: GoogleSignInClient

    var meshService: IMeshService? = null

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
