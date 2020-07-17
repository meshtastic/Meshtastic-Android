package com.geeksville.mesh.model

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.os.RemoteException
import android.view.Menu
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import com.geeksville.android.BuildUtils.isEmulator
import com.geeksville.android.Logging
import com.geeksville.mesh.IMeshService
import com.geeksville.mesh.MeshProtos
import com.geeksville.mesh.MyNodeInfo
import com.geeksville.mesh.service.MeshService

/// Given a human name, strip out the first letter of the first three words and return that as the initials for
/// that user.
fun getInitials(name: String): String {
    val words = name.split(Regex("\\s+")).filter { it.isNotEmpty() }.take(3).map { it.first() }
        .joinToString("")

    return words
}

class UIViewModel(app: Application) : AndroidViewModel(app), Logging {
    init {
        debug("ViewModel created")
    }

    companion object {
        /**
         * Return the current channel info
         * FIXME, we should sim channels at the MeshService level if we are running on an emulator,
         * for now I just fake it by returning a canned channel.
         */
        fun getChannel(c: MeshProtos.RadioConfig?): Channel? {
            val channel = c?.channelSettings?.let { Channel(it) }

            return if (channel == null && isEmulator)
                Channel.emulated
            else
                channel
        }

        fun getPreferences(context: Context): SharedPreferences =
            context.getSharedPreferences("ui-prefs", Context.MODE_PRIVATE)
    }

    private val context = app.applicationContext

    var actionBarMenu: Menu? = null

    var meshService: IMeshService? = null

    val nodeDB = NodeDB(this)
    val messagesState = MessagesState(this)

    /// Are we connected to our radio device
    val isConnected =
        object :
            MutableLiveData<MeshService.ConnectionState>(MeshService.ConnectionState.DISCONNECTED) {
        }

    /// various radio settings (including the channel)
    val radioConfig = object : MutableLiveData<MeshProtos.RadioConfig?>(null) {
    }

    /// hardware info about our local device
    val myNodeInfo = object : MutableLiveData<MyNodeInfo>(null) {}

    override fun onCleared() {
        super.onCleared()
        debug("ViewModel cleared")
    }

    /// Set the radio config (also updates our saved copy in preferences)
    fun setRadioConfig(c: MeshProtos.RadioConfig) {
        debug("Setting new radio config!")
        meshService?.radioConfig = c.toByteArray()
        radioConfig.value =
            c // Must be done after calling the service, so we will will properly throw if the service failed (and therefore not cache invalid new settings)

        getPreferences(context).edit(commit = true) {
            this.putString("channel-url", getChannel(c)!!.getChannelUrl().toString())
        }
    }

    /** Update just the channel settings portion of our config (both in the device and in saved preferences) */
    fun setChannel(c: MeshProtos.ChannelSettings) {
        // When running on the emulator, radio config might not really be available, in that case, just ignore attempts to change the config
        radioConfig.value?.toBuilder()?.let { config ->
            config.channelSettings = c
            setRadioConfig(config.build())
        }
    }

    /// Kinda ugly - created in the activity but used from Compose - figure out if there is a cleaner way GIXME
    // lateinit var googleSignInClient: GoogleSignInClient

    /// our name in hte radio
    /// Note, we generate owner initials automatically for now
    /// our activity will read this from prefs or set it to the empty string
    val ownerName = object : MutableLiveData<String>("MrIDE Test") {
    }


    val bluetoothEnabled = object : MutableLiveData<Boolean>(false) {
    }


    /// If the app was launched because we received a new channel intent, the Url will be here
    var requestedChannelUrl: Uri? = null

    // clean up all this nasty owner state management FIXME
    fun setOwner(s: String? = null) {

        if (s != null) {
            ownerName.value = s

            // note: we allow an empty userstring to be written to prefs
            getPreferences(context).edit(commit = true) {
                putString("owner", s)
            }
        }

        // Note: we are careful to not set a new unique ID
        if (ownerName.value!!.isNotEmpty())
            try {
                meshService?.setOwner(
                    null,
                    ownerName.value,
                    getInitials(ownerName.value!!)
                ) // Note: we use ?. here because we might be running in the emulator
            } catch (ex: RemoteException) {
                errormsg("Can't set username on device, is device offline? ${ex.message}")
            }
    }
}

