package com.geeksville.mesh.model

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.os.RemoteException
import android.util.Base64
import androidx.compose.mutableStateOf
import androidx.core.content.edit
import com.geeksville.android.Logging
import com.geeksville.mesh.IMeshService
import com.geeksville.mesh.MeshProtos
import com.geeksville.mesh.ui.getInitials
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.journeyapps.barcodescanner.BarcodeEncoder

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

    /// Return an URL that represents the current channel values
    fun getChannelUrl(context: Context): String {
        // If we have a valid radio config use it, othterwise use whatever we have saved in the prefs
        val radio = radioConfig.value
        if (radio != null) {
            val settings = radio.channelSettings
            val channelBytes = settings.toByteArray()
            val enc = Base64.encodeToString(channelBytes, Base64.URL_SAFE + Base64.NO_WRAP)

            return "https://www.meshtastic.org/c/$enc"
        } else {
            return getPreferences(context).getString(
                "owner",
                "https://www.meshtastic.org/c/unset"
            )!!
        }
    }

    fun getChannelQR(context: Context): Bitmap
    {
        val multiFormatWriter = MultiFormatWriter()

        val bitMatrix =
            multiFormatWriter.encode(getChannelUrl(context), BarcodeFormat.QR_CODE, 192, 192);
        val barcodeEncoder = BarcodeEncoder()
        return barcodeEncoder.createBitmap(bitMatrix)
    }

    fun getPreferences(context: Context): SharedPreferences =
        context.getSharedPreferences("ui-prefs", Context.MODE_PRIVATE)

    /// Set the radio config (also updates our saved copy in preferences)
    fun setRadioConfig(context: Context, c: MeshProtos.RadioConfig) {
        radioConfig.value = c

        getPreferences(context).edit(commit = true) {
            this.putString("channel-url", getChannelUrl(context))
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
