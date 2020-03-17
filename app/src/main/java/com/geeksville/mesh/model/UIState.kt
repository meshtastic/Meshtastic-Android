package com.geeksville.mesh.model

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.net.Uri
import android.os.RemoteException
import android.util.Base64
import androidx.compose.Model
import androidx.compose.mutableStateOf
import androidx.core.content.edit
import com.geeksville.android.BuildUtils.isEmulator
import com.geeksville.android.Logging
import com.geeksville.mesh.IMeshService
import com.geeksville.mesh.MeshProtos
import com.geeksville.mesh.MeshProtos.ChannelSettings.ModemConfig
import com.geeksville.mesh.ui.getInitials
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.journeyapps.barcodescanner.BarcodeEncoder
import java.net.MalformedURLException

@Model
data class Channel(
    var name: String,
    var modemConfig: ModemConfig,
    var settings: MeshProtos.ChannelSettings? = null
) {
    companion object {
        // Placeholder when emulating
        val emulated = Channel("Default", ModemConfig.Bw125Cr45Sf128)

        const val prefix = "https://www.meshtastic.org/c/"

        private const val base64Flags = Base64.URL_SAFE + Base64.NO_WRAP

        private fun urlToSettings(url: Uri): MeshProtos.ChannelSettings {
            val urlStr = url.toString()
            val pathRegex = Regex("$prefix(.*)")
            val (base64) = pathRegex.find(urlStr)?.destructured
                ?: throw MalformedURLException("Not a meshtastic URL")
            val bytes = Base64.decode(base64, base64Flags)

            return MeshProtos.ChannelSettings.parseFrom(bytes)
        }
    }

    constructor(c: MeshProtos.ChannelSettings) : this(c.name, c.modemConfig, c)

    constructor(url: Uri) : this(urlToSettings(url))

    /// Can this channel be changed right now?
    var editable = false

    /// Return an URL that represents the current channel values
    fun getChannelUrl(): String {
        // If we have a valid radio config use it, othterwise use whatever we have saved in the prefs

        val channelBytes = settings?.toByteArray() ?: ByteArray(0) // if unset just use empty
        val enc = Base64.encodeToString(channelBytes, base64Flags)

        return "$prefix$enc"
    }

    fun getChannelQR(): Bitmap {
        val multiFormatWriter = MultiFormatWriter()

        val bitMatrix =
            multiFormatWriter.encode(getChannelUrl(), BarcodeFormat.QR_CODE, 192, 192);
        val barcodeEncoder = BarcodeEncoder()
        return barcodeEncoder.createBitmap(bitMatrix)
    }
}


/**
 * a nice readable description of modem configs
 */
fun ModemConfig.toHumanString(): String = when (this) {
    ModemConfig.Bw125Cr45Sf128 -> "Medium range (but fast)"
    ModemConfig.Bw500Cr45Sf128 -> "Short range (but fast)"
    ModemConfig.Bw31_25Cr48Sf512 -> "Long range (but slower)"
    ModemConfig.Bw125Cr48Sf4096 -> "Very long range (but slow)"
    else -> this.toString()
}

/// FIXME - figure out how to merge this staate with the AppStatus Model
object UIState : Logging {

    /// Kinda ugly - created in the activity but used from Compose - figure out if there is a cleaner way GIXME
    // lateinit var googleSignInClient: GoogleSignInClient

    var meshService: IMeshService? = null

    /// Are we connected to our radio device
    val isConnected = mutableStateOf(false)

    /// various radio settings (including the channel)
    private val radioConfig = mutableStateOf<MeshProtos.RadioConfig?>(null)

    /// our name in hte radio
    /// Note, we generate owner initials automatically for now
    /// our activity will read this from prefs or set it to the empty string
    var ownerName: String = "MrInIDE Ownername"

    /// If the app was launched because we received a new channel intent, the Url will be here
    var requestedChannelUrl: Uri? = null

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
            this.putString("channel-url", getChannel()!!.getChannelUrl())
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
