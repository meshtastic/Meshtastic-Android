package com.geeksville.mesh.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Observer
import com.geeksville.analytics.DataPair
import com.geeksville.android.GeeksvilleApplication
import com.geeksville.android.Logging
import com.geeksville.mesh.R
import com.geeksville.mesh.model.UIViewModel
import com.google.android.material.snackbar.Snackbar
import kotlinx.android.synthetic.main.channel_fragment.*


object ChannelLog : Logging


class ChannelFragment : ScreenFragment("Channel"), Logging {

    private val model: UIViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.channel_fragment, container, false)
    }

    private fun onEditingChanged() {
        val isEditing = editableCheckbox.isChecked

        channelOptions.isEnabled = false // Not yet ready
        shareButton.isEnabled = !isEditing
        channelNameView.isEnabled = isEditing
        qrView.visibility =
            if (isEditing) View.INVISIBLE else View.VISIBLE // Don't show the user a stale QR code
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        onEditingChanged() // Set initial state

        editableCheckbox.setOnCheckedChangeListener { _, checked ->
            onEditingChanged()

            if (!checked) {
                // User just locked it, we should warn and then apply changes to radio FIXME not ready yet
                Snackbar.make(
                    editableCheckbox,
                    "Changing channels is not yet supported",
                    Snackbar.LENGTH_SHORT
                ).show()
            }
        }

        model.radioConfig.observe(viewLifecycleOwner, Observer { config ->
            val channel = UIViewModel.getChannel(config)

            if (channel != null) {
                qrView.visibility = View.VISIBLE
                channelNameEdit.visibility = View.VISIBLE
                channelNameEdit.setText(channel.name)
                editableCheckbox.isEnabled = true
                
                qrView.setImageBitmap(channel.getChannelQR())
                // Share this particular channel if someone clicks share
                shareButton.setOnClickListener {
                    GeeksvilleApplication.analytics.track(
                        "share",
                        DataPair("content_type", "channel")
                    ) // track how many times users share channels

                    val sendIntent: Intent = Intent().apply {
                        action = Intent.ACTION_SEND
                        putExtra(Intent.EXTRA_TEXT, channel.getChannelUrl().toString())
                        putExtra(
                            Intent.EXTRA_TITLE,
                            "A URL for joining a Meshtastic mesh"
                        )
                        type = "text/plain"
                    }

                    val shareIntent = Intent.createChooser(sendIntent, null)
                    requireActivity().startActivity(shareIntent)
                }
            } else {
                qrView.visibility = View.INVISIBLE
                channelNameEdit.visibility = View.INVISIBLE
                editableCheckbox.isEnabled = false
            }

            val adapter = ArrayAdapter(
                requireContext(),
                R.layout.dropdown_menu_popup_item,
                arrayOf("Item 1", "Item 2", "Item 3", "Item 4")
            )

            filled_exposed_dropdown.setAdapter(adapter)
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