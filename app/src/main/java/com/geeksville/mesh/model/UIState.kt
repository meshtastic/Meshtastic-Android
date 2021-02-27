package com.geeksville.mesh.model

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.os.RemoteException
import android.view.Menu
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.geeksville.android.Logging
import com.geeksville.mesh.IMeshService
import com.geeksville.mesh.MeshProtos
import com.geeksville.mesh.MyNodeInfo
import com.geeksville.mesh.database.MeshtasticDatabase
import com.geeksville.mesh.database.PacketRepository
import com.geeksville.mesh.database.entity.Packet
import com.geeksville.mesh.service.MeshService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.max

/// Given a human name, strip out the first letter of the first three words and return that as the initials for
/// that user. If the original name is only one word, strip vowels from the original name and if the result is
/// 3 or more characters, use the first three characters. If not, just take the first 3 characters of the
/// original name.
fun getInitials(nameIn: String): String {
    val nchars = 3
    val minchars = 2
    val name = nameIn.trim()
    val words = name.split(Regex("\\s+")).filter { it.isNotEmpty() }

    val initials = when (words.size) {
        in 0..minchars - 1 -> {
            val nm = if (name.length >= 1)
                name.first() + name.drop(1).filterNot { c -> c.toLowerCase() in "aeiou" }
            else
                ""
            if (nm.length >= nchars) nm else name
        }
        else -> words.map { it.first() }.joinToString("")
    }
    return initials.take(nchars)
}

class UIViewModel(private val app: Application) : AndroidViewModel(app), Logging {

    private val repository: PacketRepository

    val allPackets: LiveData<List<Packet>>

    init {
        val packetsDao = MeshtasticDatabase.getDatabase(app).packetDao()
        repository = PacketRepository(packetsDao)
        allPackets = repository.allPackets
        debug("ViewModel created")
    }


    fun insertPacket(packet: Packet) = viewModelScope.launch(Dispatchers.IO) {
        repository.insert(packet)
    }

    fun deleteAllPacket() = viewModelScope.launch(Dispatchers.IO) {
        repository.deleteAll()
    }

    companion object {
        /**
         * Return the current channel info
         */
        fun getChannel(c: MeshProtos.RadioConfig?): Channel? {
            val channel = c?.channelSettings?.let { Channel(it) }

            return channel
        }

        fun getPreferences(context: Context): SharedPreferences =
            context.getSharedPreferences("ui-prefs", Context.MODE_PRIVATE)
    }

    private val context: Context get() = app.applicationContext

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

    var positionBroadcastSecs: Int?
        get() {
            radioConfig.value?.preferences?.let {
                if (it.locationShare == MeshProtos.LocationSharing.LocDisabled) return 0
                if (it.positionBroadcastSecs > 0) return it.positionBroadcastSecs
                // These default values are borrowed from the device code.
                if (it.isRouter) return 60 * 60
                return 15 * 60
            }
            return null
        }
        set(value) {
            val config = radioConfig.value
            if (value != null && config != null) {
                val builder = config.toBuilder()
                if (value > 0) {
                    builder.preferencesBuilder.positionBroadcastSecs = value
                    builder.preferencesBuilder.gpsUpdateInterval = value
                    builder.preferencesBuilder.sendOwnerInterval = max(1, 3600 / value).toInt()
                    builder.preferencesBuilder.locationShare =
                        MeshProtos.LocationSharing.LocEnabled
                } else {
                    builder.preferencesBuilder.positionBroadcastSecs = Int.MAX_VALUE
                    builder.preferencesBuilder.locationShare =
                        MeshProtos.LocationSharing.LocDisabled
                }
                setRadioConfig(builder.build())
            }
        }

    var lsSleepSecs: Int?
        get() {
            radioConfig.value?.preferences?.let {
                return it.lsSecs
            }
            return null
        }
        set(value) {
            val config = radioConfig.value
            if (value != null && config != null) {
                val builder = config.toBuilder()
                builder.preferencesBuilder.lsSecs = value
                setRadioConfig(builder.build())
            }
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

