package com.geeksville.mesh.model

import android.content.Context
import android.util.Base64
import androidx.compose.mutableStateOf
import androidx.core.content.edit
import com.geeksville.mesh.IMeshService
import com.geeksville.mesh.MeshProtos
import com.geeksville.mesh.ui.getInitials

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
    var ownerName: String = "fixme readfromprefs"

    /// Return an URL that represents the current channel values
    val channelUrl
        get(): String {
            val channelBytes = radioConfig.value.channelSettings.toByteArray()
            val enc = Base64.encodeToString(channelBytes, Base64.URL_SAFE + Base64.NO_WRAP)

            return "https://www.meshtastic.org/c/$enc"
        }

    // clean up all this nasty owner state management FIXME
    fun setOwner(context: Context, s: String? = null) {
        
        if (s != null) {
            ownerName = s
            val prefs = context.getSharedPreferences("ui-prefs", Context.MODE_PRIVATE)
            prefs.edit(commit = true) {
                putString("owner", s)
            }
        }

        // Note: we are careful to not set a new unique ID
        meshService!!.setOwner(null, ownerName, getInitials(ownerName))
    }
}
