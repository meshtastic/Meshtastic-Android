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
            editableCheckbox.isEnabled = true

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

        model.radioConfig.observe(viewLifecycleOwner, Observer { config ->
            setGUIfromModel()
        })
    }
}

/*
@Composable
fun ChannelContent(channel: Channel?) {

    val typography = MaterialTheme.typography
    val context = ContextAmbient.current

    Column(modifier = LayoutSize.Fill + LayoutPadding(16.dp)) {
        if (channel != null) {
            Row(modifier = LayoutGravity.Center) {

                Text(text = "Channel ", modifier = LayoutGravity.Center)

                if (channel.editable) {
                    // FIXME - limit to max length
                    StyledTextField(
                        value = channel.name,
                        onValueChange = { channel.name = it },
                        textStyle = typography.h4.copy(
                            color = palette.onSecondary.copy(alpha = 0.8f)
                        ),
                        imeAction = ImeAction.Done,
                        onImeActionPerformed = {
                            ChannelLog.errormsg("FIXME, implement channel edit button")
                        }
                    )
                } else {
                    Text(
                        text = channel.name,
                        style = typography.h4
                    )
                }
            }

            // simulated qr code
            // val image = imageResource(id = R.drawable.qrcode)
            val image = AndroidImage(channel.getChannelQR())

            ScaledImage(
                image = image,
                modifier = LayoutGravity.Center + LayoutSize.Min(200.dp, 200.dp)
            )

            Text(
                text = "Mode: ${channel.modemConfig.toHumanString()}",
                modifier = LayoutGravity.Center + LayoutPadding(bottom = 16.dp)
            )

            Row(modifier = LayoutGravity.Center) {

                OutlinedButton(onClick = {
                    channel.editable = !channel.editable
                }) {
                    if (channel.editable)
                        VectorImage(
                            id = R.drawable.ic_twotone_lock_open_24,
                            tint = palette.onBackground
                        )
                    else
                        VectorImage(
                            id = R.drawable.ic_twotone_lock_24,
                            tint = palette.onBackground
                        )
                }

                // Only show the share buttone once we are locked
                if (!channel.editable)
                    OutlinedButton(modifier = LayoutPadding(start = 24.dp),
                        onClick = {

                        }) {
                        VectorImage(
                            id = R.drawable.ic_twotone_share_24,
                            tint = palette.onBackground
                        )
                    }
            }
        }
    }
}

*/