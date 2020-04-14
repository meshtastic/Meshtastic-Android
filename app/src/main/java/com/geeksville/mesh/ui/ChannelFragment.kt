package com.geeksville.mesh.ui

import android.content.Intent
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.os.Bundle
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
import com.geeksville.mesh.R
import com.geeksville.mesh.model.UIViewModel
import com.geeksville.mesh.service.MeshService
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.android.synthetic.main.channel_fragment.*


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

        channelOptions.isEnabled = false // Not yet ready
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

        val adapter = ArrayAdapter(
            requireContext(),
            R.layout.dropdown_menu_popup_item,
            arrayOf("Item 1", "Item 2", "Item 3", "Item 4")
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
                /* Snackbar.make(
                    editableCheckbox,
                    "Changing channels is not yet supported",
                    Snackbar.LENGTH_SHORT
                ).show() */

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
                            // FIXME, regenerate a new preshared key!
                            model.setChannel(newSettings.build())
                            // Since we are writing to radioconfig, that will trigger the rest of the GUI update (QR code etc)
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
}
