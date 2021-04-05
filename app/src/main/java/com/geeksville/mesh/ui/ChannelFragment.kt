package com.geeksville.mesh.ui

import android.content.ActivityNotFoundException
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
import com.geeksville.analytics.DataPair
import com.geeksville.android.GeeksvilleApplication
import com.geeksville.android.Logging
import com.geeksville.android.hideKeyboard
import com.geeksville.mesh.AppOnlyProtos
import com.geeksville.mesh.ChannelProtos
import com.geeksville.mesh.R
import com.geeksville.mesh.databinding.ChannelFragmentBinding
import com.geeksville.mesh.model.Channel
import com.geeksville.mesh.model.ChannelOption
import com.geeksville.mesh.model.ChannelSet
import com.geeksville.mesh.model.UIViewModel
import com.geeksville.mesh.service.MeshService
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.protobuf.ByteString
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

    private var _binding: ChannelFragmentBinding? = null

    // This property is only valid between onCreateView and onDestroyView.
    private val binding get() = _binding!!

    private val model: UIViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = ChannelFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    /// Called when the lock/unlock icon has changed
    private fun onEditingChanged() {
        val isEditing = binding.editableCheckbox.isChecked

        binding.channelOptions.isEnabled = isEditing
        binding.shareButton.isEnabled = !isEditing
        binding.channelNameView.isEnabled = isEditing
        if (isEditing) // Dim the (stale) QR code while editing...
            binding.qrView.setDim()
        else
            binding.qrView.setOpaque()
    }

    /// Pull the latest data from the model (discarding any user edits)
    private fun setGUIfromModel() {
        val channels = model.channels.value
        val channel = channels?.primaryChannel

        val connected = model.isConnected.value == MeshService.ConnectionState.CONNECTED

        // Only let buttons work if we are connected to the radio
        binding.shareButton.isEnabled = connected
        binding.resetButton.isEnabled = connected && Channel.default != channel

        binding.editableCheckbox.isChecked = false // start locked
        if (channel != null) {
            binding.qrView.visibility = View.VISIBLE
            binding.channelNameEdit.visibility = View.VISIBLE
            binding.channelNameEdit.setText(channel.humanName)

            // For now, we only let the user edit/save channels while the radio is awake - because the service
            // doesn't cache radioconfig writes.
            binding.editableCheckbox.isEnabled = connected

            val bitmap = channels.qrCode
            if (bitmap != null)
                binding.qrView.setImageBitmap(bitmap)

            val modemConfig = channel.modemConfig
            val channelOption = ChannelOption.fromConfig(modemConfig)
            binding.filledExposedDropdown.setText(
                getString(
                    channelOption?.configRes ?: R.string.modem_config_unrecognized
                ), false
            )

        } else {
            binding.qrView.visibility = View.INVISIBLE
            binding.channelNameEdit.visibility = View.INVISIBLE
            binding.editableCheckbox.isEnabled = false
        }

        onEditingChanged() // we just locked the gui
        val modemConfigs = ChannelOption.values()
        val modemConfigList = modemConfigs.map { getString(it.configRes) }
        val adapter = ArrayAdapter(
            requireContext(),
            R.layout.dropdown_menu_popup_item,
            modemConfigList
        )

        binding.filledExposedDropdown.setAdapter(adapter)
    }

    private fun shareChannel() {
        model.channels.value?.let { channels ->

            GeeksvilleApplication.analytics.track(
                "share",
                DataPair("content_type", "channel")
            ) // track how many times users share channels

            val sendIntent: Intent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_TEXT, channels.getChannelUrl().toString())
                putExtra(
                    Intent.EXTRA_TITLE,
                    getString(R.string.url_for_join)
                )
                type = "text/plain"
            }

            try {
                val shareIntent = Intent.createChooser(sendIntent, null)
                requireActivity().startActivity(shareIntent)
            } catch (ex: ActivityNotFoundException) {
                Snackbar.make(
                    binding.shareButton,
                    R.string.no_app_found,
                    Snackbar.LENGTH_SHORT
                ).show()
            }
        }
    }

    /// Send new channel settings to the device
    private fun installSettings(newChannel: ChannelProtos.ChannelSettings) {
        val newSet =
            ChannelSet(AppOnlyProtos.ChannelSet.newBuilder().addSettings(newChannel).build())
        // Try to change the radio, if it fails, tell the user why and throw away their redits
        try {
            model.setChannels(newSet)
            // Since we are writing to radioconfig, that will trigger the rest of the GUI update (QR code etc)
        } catch (ex: RemoteException) {
            errormsg("ignoring channel problem", ex)

            setGUIfromModel() // Throw away user edits

            // Tell the user to try again
            Snackbar.make(
                binding.editableCheckbox,
                R.string.radio_sleeping,
                Snackbar.LENGTH_SHORT
            ).show()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.channelNameEdit.on(EditorInfo.IME_ACTION_DONE) {
            requireActivity().hideKeyboard()
        }

        binding.resetButton.setOnClickListener { _ ->
            // User just locked it, we should warn and then apply changes to radio
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.reset_to_defaults)
                .setMessage(R.string.are_you_shure_change_default)
                .setNeutralButton(R.string.cancel) { _, _ ->
                    setGUIfromModel() // throw away any edits
                }
                .setPositiveButton(R.string.apply) { _, _ ->
                    debug("Switching back to default channel")
                    installSettings(Channel.default.settings)
                }
                .show()
        }

        // Note: Do not use setOnCheckedChanged here because we don't want to be called when we programmatically disable editing
        binding.editableCheckbox.setOnClickListener { _ ->

            /// We use this to determine if the user tried to install a custom name
            var originalName = ""

            val checked = binding.editableCheckbox.isChecked
            if (checked) {
                // User just unlocked for editing - remove the # goo around the channel name
                model.channels.value?.primaryChannel?.let { ch ->
                    // Note: We are careful to show the emptystring here if the user was on a default channel, so the user knows they should it for any changes
                    originalName = ch.settings.name
                    binding.channelNameEdit.setText(originalName)
                }
            } else {
                // User just locked it, we should warn and then apply changes to radio
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.change_channel)
                    .setMessage(R.string.are_you_sure_channel)
                    .setNeutralButton(R.string.cancel) { _, _ ->
                        setGUIfromModel()
                    }
                    .setPositiveButton(getString(R.string.accept)) { _, _ ->
                        // Generate a new channel with only the changes the user can change in the GUI
                        model.channels.value?.primaryChannel?.let { oldPrimary ->
                            var newSettings = oldPrimary.settings.toBuilder()
                            val newName = binding.channelNameEdit.text.toString().trim()

                            // Find the new modem config
                            val selectedChannelOptionString =
                                binding.filledExposedDropdown.editableText.toString()
                            var modemConfig = getModemConfig(selectedChannelOptionString)
                            if (modemConfig == ChannelProtos.ChannelSettings.ModemConfig.UNRECOGNIZED) // Huh? didn't find it - keep same
                                modemConfig = oldPrimary.settings.modemConfig

                            // Generate a new AES256 key if the user changes channel name or the name is non-default and the settings changed
                            if (newName != originalName || (newName.isNotEmpty() && modemConfig != oldPrimary.settings.modemConfig)) {
                                // Install a new customized channel
                                debug("ASSIGNING NEW AES256 KEY")
                                val random = SecureRandom()
                                val bytes = ByteArray(32)
                                random.nextBytes(bytes)
                                newSettings.name = newName
                                newSettings.psk = ByteString.copyFrom(bytes)
                            } else {
                                debug("Switching back to default channel")
                                newSettings = Channel.default.settings.toBuilder()
                            }

                            // No matter what apply the speed selection from the user
                            newSettings.modemConfig = modemConfig

                            installSettings(newSettings.build())
                        }
                    }
                    .show()
            }

            onEditingChanged() // update GUI on what user is allowed to edit/share
        }

        // Share this particular channel if someone clicks share
        binding.shareButton.setOnClickListener {
            shareChannel()
        }

        model.channels.observe(viewLifecycleOwner, {
            setGUIfromModel()
        })

        // If connection state changes, we might need to enable/disable buttons
        model.isConnected.observe(viewLifecycleOwner, {
            setGUIfromModel()
        })
    }

    private fun getModemConfig(selectedChannelOptionString: String): ChannelProtos.ChannelSettings.ModemConfig {
        for (item in ChannelOption.values()) {
            if (getString(item.configRes) == selectedChannelOptionString)
                return item.modemConfig
        }

        return ChannelProtos.ChannelSettings.ModemConfig.UNRECOGNIZED
    }
}
