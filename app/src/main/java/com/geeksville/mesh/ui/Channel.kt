package com.geeksville.mesh.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import androidx.compose.Composable
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Observer
import androidx.ui.core.ContextAmbient
import androidx.ui.foundation.Text
import androidx.ui.input.ImeAction
import androidx.ui.layout.*
import androidx.ui.material.MaterialTheme
import androidx.ui.material.OutlinedButton
import androidx.ui.tooling.preview.Preview
import androidx.ui.unit.dp
import com.geeksville.analytics.DataPair
import com.geeksville.android.GeeksvilleApplication
import com.geeksville.android.Logging
import com.geeksville.mesh.R
import com.geeksville.mesh.model.Channel
import com.geeksville.mesh.model.UIViewModel
import com.geeksville.mesh.model.toHumanString
import com.google.android.material.textfield.TextInputEditText

object ChannelLog : Logging


class ChannelFragment : ScreenFragment("Channel"), Logging {

    private val model: UIViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.channel_fragment, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        model.radioConfig.observe(viewLifecycleOwner, Observer { config ->
            val channel = UIViewModel.getChannel(config)
            val channelNameEdit = view.findViewById<TextInputEditText>(R.id.channelNameEdit)

            if (channel != null) {
                channelNameEdit.visibility = View.VISIBLE
                channelNameEdit.setText(channel.name)
            } else {
                channelNameEdit.visibility = View.INVISIBLE
            }

            val adapter = ArrayAdapter(
                requireContext(),
                R.layout.dropdown_menu_popup_item,
                arrayOf("Item 1", "Item 2", "Item 3", "Item 4")
            )

            val editTextFilledExposedDropdown =
                view.findViewById<AutoCompleteTextView>(R.id.filled_exposed_dropdown)
            editTextFilledExposedDropdown.setAdapter(adapter)
        })
    }
}


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
                            context.startActivity(shareIntent)
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


@Preview
@Composable
fun previewChannel() {
    // another bug? It seems modaldrawerlayout not yet supported in preview
    MaterialTheme(colors = palette) {
        ChannelContent(Channel.emulated)
    }
}
