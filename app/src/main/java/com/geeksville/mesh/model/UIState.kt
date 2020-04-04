package com.geeksville.mesh.model

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.os.RemoteException
import androidx.compose.mutableStateOf
import androidx.core.content.edit
import com.geeksville.android.BuildUtils.isEmulator
import com.geeksville.android.Logging
import com.geeksville.mesh.IMeshService
import com.geeksville.mesh.MeshProtos
import com.geeksville.mesh.service.MeshService
import com.geeksville.mesh.ui.getInitials

/// FIXME - figure out how to merge this staate with the AppStatus Model
object UIState : Logging {

    /// Kinda ugly - created in the activity but used from Compose - figure out if there is a cleaner way GIXME
    // lateinit var googleSignInClient: GoogleSignInClient

    var meshService: IMeshService? = null

    /// Are we connected to our radio device
    val isConnected = mutableStateOf(MeshService.ConnectionState.DISCONNECTED)

    /// various radio settings (including the channel)
    private val radioConfig = mutableStateOf<MeshProtos.RadioConfig?>(null)

    /// our name in hte radio
    /// Note, we generate owner initials automatically for now
    /// our activity will read this from prefs or set it to the empty string
    var ownerName: String = "MrInIDE Ownername"

    /// If the app was launched because we received a new channel intent, the Url will be here
    var requestedChannelUrl: Uri? = null

    var savedInstanceState: Bundle? = null

    /**
     * Return the current channel info
     * FIXME, we should sim channels at the MeshService level if we are running on an emulator,
     * for now I just fake it by returning a canned channel.
     */
    fun getChannel(): Channel? {
        val channel = radioConfig.value?.channelSettings?.let { Channel(it) }

        return if (channel == null && isEmulator)
            Channel.emulated
        else
            channel
    }

    fun getPreferences(context: Context): SharedPreferences =
        context.getSharedPreferences("ui-prefs", Context.MODE_PRIVATE)

    /// Set the radio config (also updates our saved copy in preferences)
    fun setRadioConfig(context: Context, c: MeshProtos.RadioConfig) {
        radioConfig.value = c

        getPreferences(context).edit(commit = true) {
            this.putString("channel-url", getChannel()!!.getChannelUrl().toString())
        }
    }

    // clean up all this nasty owner state management FIXME
    fun setOwner(context: Context, s: String? = null) {

        if (s != null) {
            ownerName = s

            // note: we allow an empty userstring to be written to prefs
            getPreferences(context).edit(commit = true) {
                putString("owner", s)
            }
        }

        // Note: we are careful to not set a new unique ID
        if (ownerName.isNotEmpty())
            try {
                meshService?.setOwner(
                    null,
                    ownerName,
                    getInitials(ownerName)
                ) // Note: we use ?. here because we might be running in the emulator
            } catch (ex: RemoteException) {
                errormsg("Can't set username on device, is device offline? ${ex.message}")
            }
    }
}
