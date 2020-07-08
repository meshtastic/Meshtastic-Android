package com.geeksville.mesh.ui

import android.content.Intent
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.os.Bundle
import android.os.RemoteException
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.ArrayAdapter
import android.widget.ImageView
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Observer
import com.geeksville.analytics.DataPair
import com.geeksville.android.GeeksvilleApplication
import com.geeksville.android.Logging
import com.geeksville.android.hideKeyboard
import com.geeksville.mesh.MeshProtos
import com.geeksville.mesh.R
import com.geeksville.mesh.model.Channel
import com.geeksville.mesh.model.ChannelOption
import com.geeksville.mesh.model.UIViewModel
import com.geeksville.mesh.service.MeshService
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.protobuf.ByteString
import kotlinx.android.synthetic.main.channel_fragment.*
import java.security.SecureRandom


// Make an image view dim
fun ImageView.setDim() {
    val matrix = ColorMatrix()
    matrix.setSaturation(0f) //0 means grayscale
    val cf = ColorMatrixColorFilter(matrix)
    colorFilter = cf
    imageAlpha = 64 // 128 = 0.5
}

/// Return image view to normal
fun ImageView.setOpaque() {
    colorFilter = null
    imageAlpha = 255
}

class ChannelFragment : ScreenFragment("Channel"), Logging {

    private val model: UIViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.channel_fragment, container, false)
    }

    /// Called when the lock/unlock icon has changed
    private fun onEditingChanged() {
        val isEditing = editableCheckbox.isChecked

        channelOptions.isEnabled = isEditing
        shareButton.isEnabled = !isEditing
        channelNameView.isEnabled = isEditing
        if (isEditing) // Dim the (stale) QR code while editing...
            qrView.setDim()
        else
            qrView.setOpaque()
    }

    /// Pull the latest data from the model (discarding any user edits)
    private fun setGUIfromModel() {
        val channel = UIViewModel.getChannel(model.radioConfig.value)

        editableCheckbox.isChecked = false // start locked
        if (channel != null) {
            qrView.visibility = View.VISIBLE
            channelNameEdit.visibility = View.VISIBLE
            channelNameEdit.setText(channel.name)

            // For now, we only let the user edit/save channels while the radio is awake - because the service
            // doesn't cache radioconfig writes.
            val connected = model.isConnected.value == MeshService.ConnectionState.CONNECTED
            editableCheckbox.isEnabled = connected

            qrView.setImageBitmap(channel.getChannelQR())
        } else {
            qrView.visibility = View.INVISIBLE
            channelNameEdit.visibility = View.INVISIBLE
            editableCheckbox.isEnabled = false
        }

        onEditingChanged() // we just locked the gui
        val modemConfigs = ChannelOption.values()
        val modemConfigList = modemConfigs.map { getString(it.configRes) }
        val adapter = ArrayAdapter(
            requireContext(),
            R.layout.dropdown_menu_popup_item,
            modemConfigList
        )

        filled_exposed_dropdown.setAdapter(adapter)
    }

    private fun shareChannel() {
        UIViewModel.getChannel(model.radioConfig.value)?.let { channel ->

            GeeksvilleApplication.analytics.track(
                "share",
                DataPair("content_type", "channel")
            ) // track how many times users share channels

            val sendIntent: Intent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_TEXT, channel.getChannelUrl().toString())
                putExtra(
                    Intent.EXTRA_TITLE,
                    getString(R.string.url_for_join)
                )
                type = "text/plain"
            }

            val shareIntent = Intent.createChooser(sendIntent, null)
            requireActivity().startActivity(shareIntent)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        channelNameEdit.on(EditorInfo.IME_ACTION_DONE) {
            requireActivity().hideKeyboard()
        }

        editableCheckbox.setOnCheckedChangeListener { _, checked ->
            if (!checked) {
                // User just locked it, we should warn and then apply changes to radio
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.change_channel)
                    .setMessage(R.string.are_you_sure_channel)
                    .setNeutralButton(R.string.cancel) { _, _ ->
                        setGUIfromModel()
                    }
                    .setPositiveButton(getString(R.string.accept)) { _, _ ->
                        // Generate a new channel with only the changes the user can change in the GUI
                        UIViewModel.getChannel(model.radioConfig.value)?.let { old ->
                            val newSettings = old.settings.toBuilder()
                            newSettings.name = channelNameEdit.text.toString().trim()

                            // Generate a new AES256 key (for any channel not named Default)
                            if (!newSettings.name.equals(
                                    Channel.defaultChannelName,
                                    ignoreCase = true
                                )
                            ) {
                                debug("ASSIGNING NEW AES256 KEY")
                                val random = SecureRandom()
                                val bytes = ByteArray(32)
                                random.nextBytes(bytes)
                                newSettings.psk = ByteString.copyFrom(bytes)
                            } else {
                                debug("ASSIGNING NEW default AES128 KEY")
                                newSettings.name = Channel.defaultChannelName // Fix any case errors
                                newSettings.psk = ByteString.copyFrom(Channel.channelDefaultKey)
                            }

                            val selectedChannelOptionString =
                                filled_exposed_dropdown.editableText.toString()
                            val modemConfig = getModemConfig(selectedChannelOptionString)

                            if (modemConfig != MeshProtos.ChannelSettings.ModemConfig.UNRECOGNIZED)
                                newSettings.modemConfig = modemConfig
                            // Try to change the radio, if it fails, tell the user why and throw away their redits
                            try {
                                model.setChannel(newSettings.build())
                                // Since we are writing to radioconfig, that will trigger the rest of the GUI update (QR code etc)
                            } catch (ex: RemoteException) {
                                errormsg("ignoring channel problem", ex)
                                
                                setGUIfromModel() // Throw away user edits

                                // Tell the user to try again
                                Snackbar.make(
                                    editableCheckbox,
                                    R.string.radio_sleeping,
                                    Snackbar.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                    .show()
            }

            onEditingChanged() // update GUI on what user is allowed to edit/share
        }

        // Share this particular channel if someone clicks share
        shareButton.setOnClickListener {
            shareChannel()
        }

        model.radioConfig.observe(viewLifecycleOwner, Observer {
            setGUIfromModel()
        })

        // If connection state changes, we might need to enable/disable buttons
        model.isConnected.observe(viewLifecycleOwner, Observer {
            setGUIfromModel()
        })
    }

    private fun getModemConfig(selectedChannelOptionString: String): MeshProtos.ChannelSettings.ModemConfig {
        for (item in ChannelOption.values()) {
            if (getString(item.configRes) == selectedChannelOptionString)
                return item.modemConfig
        }

        return MeshProtos.ChannelSettings.ModemConfig.UNRECOGNIZED
    }
}
