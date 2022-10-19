package com.geeksville.mesh.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.net.Uri
import android.os.Bundle
import android.os.RemoteException
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.ArrayAdapter
import android.widget.ImageView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.asLiveData
import com.geeksville.mesh.analytics.DataPair
import com.geeksville.mesh.android.GeeksvilleApplication
import com.geeksville.mesh.android.Logging
import com.geeksville.mesh.android.hideKeyboard
import com.geeksville.mesh.ChannelProtos
import com.geeksville.mesh.ConfigKt.loRaConfig
import com.geeksville.mesh.ConfigProtos
import com.geeksville.mesh.R
import com.geeksville.mesh.android.getCameraPermissions
import com.geeksville.mesh.android.hasCameraPermission
import com.geeksville.mesh.channelSet
import com.geeksville.mesh.copy
import com.geeksville.mesh.databinding.ChannelFragmentBinding
import com.geeksville.mesh.model.Channel
import com.geeksville.mesh.model.ChannelOption
import com.geeksville.mesh.model.ChannelSet
import com.geeksville.mesh.model.UIViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.protobuf.ByteString
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import dagger.hilt.android.AndroidEntryPoint
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

@AndroidEntryPoint
class ChannelFragment : ScreenFragment("Channel"), Logging {

    private var _binding: ChannelFragmentBinding? = null

    // This property is only valid between onCreateView and onDestroyView.
    private val binding get() = _binding!!

    private val model: UIViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = ChannelFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    /// Called when the lock/unlock icon has changed
    private fun onEditingChanged() {
        val isEditing = binding.editableCheckbox.isChecked

        binding.channelOptions.isEnabled = isEditing
        binding.shareButton.isEnabled = !isEditing
        binding.resetButton.isEnabled = isEditing
        binding.scanButton.isEnabled = isEditing
        binding.channelNameView.isEnabled = isEditing
        if (isEditing) // Dim the (stale) QR code while editing...
            binding.qrView.setDim()
        else
            binding.qrView.setOpaque()
    }

    /// Pull the latest data from the model (discarding any user edits)
    private fun setGUIfromModel() {
        val channels = model.channels.value
        val channel = channels.primaryChannel
        val connected = model.isConnected()

        // Only let buttons work if we are connected to the radio
        binding.editableCheckbox.isChecked = false // start locked
        onEditingChanged() // we just locked the gui
        binding.shareButton.isEnabled = connected

        if (channel != null) {
            binding.qrView.visibility = View.VISIBLE
            binding.channelNameEdit.visibility = View.VISIBLE
            binding.channelNameEdit.setText(channel.humanName)

            // For now, we only let the user edit/save channels while the radio is awake - because the service
            // doesn't cache DeviceConfig writes.
            binding.editableCheckbox.isEnabled = connected

            val bitmap = channels.qrCode
            if (bitmap != null)
                binding.qrView.setImageBitmap(bitmap)

            val modemPreset = channel.loraConfig.modemPreset
            val channelOption = ChannelOption.fromConfig(modemPreset)
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

        val modemPresets = ChannelOption.values()
        val modemPresetList = modemPresets.map { getString(it.configRes) }
        val adapter = ArrayAdapter(
            requireContext(),
            R.layout.dropdown_menu_popup_item,
            modemPresetList
        )

        binding.filledExposedDropdown.setAdapter(adapter)
    }

    private fun shareChannel() {
        model.channels.value.let { channels ->

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
                    requireView(),
                    R.string.no_app_found,
                    Snackbar.LENGTH_SHORT
                ).show()
            }
        }
    }

    /// Send new channel settings to the device
    private fun installSettings(
        newChannel: ChannelProtos.ChannelSettings,
        newLoRaConfig: ConfigProtos.Config.LoRaConfig
    ) {
        val newSet = ChannelSet(
            channelSet {
                settings.add(newChannel)
                loraConfig = newLoRaConfig
            })
        // Try to change the radio, if it fails, tell the user why and throw away their edits
        try {
            model.setChannels(newSet)
            // Since we are writing to DeviceConfig, that will trigger the rest of the GUI update (QR code etc)
        } catch (ex: RemoteException) {
            errormsg("ignoring channel problem", ex)

            setGUIfromModel() // Throw away user edits

            // Tell the user to try again
            Snackbar.make(
                requireView(),
                R.string.radio_sleeping,
                Snackbar.LENGTH_SHORT
            ).show()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val barcodeLauncher = registerForActivityResult(ScanContract()) { result ->
            if (result.contents != null) {
                model.setRequestChannelUrl(Uri.parse(result.contents))
            }
        }

        fun zxingScan() {
            debug("Starting zxing QR code scanner")
            val zxingScan = ScanOptions()
            zxingScan.setCameraId(0)
            zxingScan.setPrompt("")
            zxingScan.setBeepEnabled(false)
            zxingScan.setDesiredBarcodeFormats(ScanOptions.QR_CODE)
            barcodeLauncher.launch(zxingScan)
        }

        val requestPermissionAndScanLauncher =
            registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
                if (permissions.entries.all { it.value }) zxingScan()
            }

        fun requestPermissionAndScan() {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.camera_required)
                .setMessage(R.string.why_camera_required)
                .setNeutralButton(R.string.cancel) { _, _ ->
                    debug("Camera permission denied")
                }
                .setPositiveButton(getString(R.string.accept)) { _, _ ->
                    requestPermissionAndScanLauncher.launch(requireContext().getCameraPermissions())
                }
                .show()
        }

        binding.channelNameEdit.on(EditorInfo.IME_ACTION_DONE) {
            requireActivity().hideKeyboard()
        }

        binding.resetButton.setOnClickListener {
            // User just locked it, we should warn and then apply changes to radio
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.reset_to_defaults)
                .setMessage(R.string.are_you_shure_change_default)
                .setNeutralButton(R.string.cancel) { _, _ ->
                    setGUIfromModel() // throw away any edits
                }
                .setPositiveButton(R.string.apply) { _, _ ->
                    debug("Switching back to default channel")
                    installSettings(
                        Channel.default.settings,
                        Channel.default.loraConfig.copy {
                            region = model.region
                            txEnabled = model.txEnabled
                        }
                    )
                }
                .show()
        }

        binding.scanButton.setOnClickListener {
            if (requireContext().hasCameraPermission()) zxingScan()
            else requestPermissionAndScan()
        }

        // Note: Do not use setOnCheckedChanged here because we don't want to be called when we programmatically disable editing
        binding.editableCheckbox.setOnClickListener {

            /// We use this to determine if the user tried to install a custom name
            var originalName = ""

            val checked = binding.editableCheckbox.isChecked
            if (checked) {
                // User just unlocked for editing - remove the # goo around the channel name
                model.channels.value.primaryChannel?.let { ch ->
                    // Note: We are careful to show the empty string here if the user was on a default channel, so the user knows they should it for any changes
                    originalName = ch.settings.name
                    binding.channelNameEdit.setText(originalName)
                }
            } else {
                // User just locked it, we should warn and then apply changes to radio

                model.channels.value.primaryChannel?.let { oldPrimary ->
                    var newSettings = oldPrimary.settings
                    val newName = binding.channelNameEdit.text.toString().trim()

                    // Find the new modem config
                    val selectedModemPresetString =
                        binding.filledExposedDropdown.editableText.toString()
                    var newModemPreset = getModemPreset(selectedModemPresetString)
                    if (newModemPreset == ConfigProtos.Config.LoRaConfig.ModemPreset.UNRECOGNIZED) // Huh? didn't find it - keep same
                        newModemPreset = oldPrimary.loraConfig.modemPreset

                    // Generate a new AES256 key if the user changes channel name or the name is non-default and the settings changed
                    val shouldUseRandomKey =
                        newName != originalName || (newName.isNotEmpty() && newModemPreset != oldPrimary.loraConfig.modemPreset)
                    if (shouldUseRandomKey) {

                        // Install a new customized channel
                        debug("ASSIGNING NEW AES256 KEY")
                        val random = SecureRandom()
                        val bytes = ByteArray(32)
                        random.nextBytes(bytes)
                        newSettings = newSettings.copy {
                            name = newName.take(11) // proto max_size:12
                            psk = ByteString.copyFrom(bytes)
                        }
                    } else {
                        debug("Switching back to default channel")
                        newSettings = Channel.default.settings
                    }

                    // No matter what apply the speed selection from the user
                    val newLoRaConfig = model.config.lora.copy {
                        usePreset = true
                        modemPreset = newModemPreset
                        bandwidth = 0
                        spreadFactor = 0
                        codingRate = 0
                    }

                    val humanName = Channel(newSettings, newLoRaConfig).humanName
                    binding.channelNameEdit.setText(humanName)

                    val message = buildString {
                        append(getString(R.string.are_you_sure_channel))
                        if (!shouldUseRandomKey)
                            append("\n\n" + getString(R.string.warning_default_psk).format(humanName))
                    }

                    MaterialAlertDialogBuilder(requireContext())
                        .setTitle(R.string.change_channel)
                        .setMessage(message)
                        .setNeutralButton(R.string.cancel) { _, _ ->
                            setGUIfromModel()
                        }
                        .setPositiveButton(getString(R.string.accept)) { _, _ ->
                            // Generate a new channel with only the changes the user can change in the GUI

                            installSettings(newSettings, newLoRaConfig)
                        }
                        .show()
                }
            }

            onEditingChanged() // update GUI on what user is allowed to edit/share
        }

        // Share this particular channel if someone clicks share
        binding.shareButton.setOnClickListener {
            shareChannel()
        }

        model.channels.asLiveData().observe(viewLifecycleOwner) {
            setGUIfromModel()
        }

        // If connection state changes, we might need to enable/disable buttons
        model.connectionState.observe(viewLifecycleOwner) {
            setGUIfromModel()
        }
    }

    private fun getModemPreset(selectedChannelOptionString: String): ConfigProtos.Config.LoRaConfig.ModemPreset {
        for (item in ChannelOption.values()) {
            if (getString(item.configRes) == selectedChannelOptionString)
                return item.modemPreset
        }
        return ConfigProtos.Config.LoRaConfig.ModemPreset.UNRECOGNIZED
    }
}
