package com.geeksville.mesh.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.fragment.app.activityViewModels
import com.geeksville.android.Logging
import com.geeksville.android.hideKeyboard
import com.geeksville.mesh.R
import com.geeksville.mesh.databinding.AdvancedSettingsBinding
import com.geeksville.mesh.model.ChannelOption
import com.geeksville.mesh.model.UIViewModel
import com.geeksville.mesh.service.MeshService
import com.geeksville.util.exceptionToSnackbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class AdvancedSettingsFragment : ScreenFragment("Advanced Settings"), Logging {
    private val MAX_INT_DEVICE = 0xFFFFFFFF
    private var _binding: AdvancedSettingsBinding? = null

    private val binding get() = _binding!!

    private val model: UIViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = AdvancedSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        model.deviceConfig.observe(viewLifecycleOwner) {
            binding.positionBroadcastPeriodEditText.setText(model.positionBroadcastSecs.toString())
            binding.lsSleepEditText.setText(model.lsSleepSecs.toString())
            binding.positionBroadcastPeriodView.isEnabled = !model.gpsDisabled
            binding.positionBroadcastSwitch.isChecked = !model.gpsDisabled
            binding.lsSleepView.isEnabled = model.isPowerSaving ?: false && model.isESP32()
            binding.lsSleepSwitch.isChecked = model.isPowerSaving ?: false && model.isESP32()
        }

        model.connectionState.observe(viewLifecycleOwner) { connectionState ->
            val connected = connectionState == MeshService.ConnectionState.CONNECTED
            binding.positionBroadcastPeriodView.isEnabled = connected && !model.gpsDisabled
            binding.lsSleepView.isEnabled = connected && model.isPowerSaving ?: false
            binding.positionBroadcastSwitch.isEnabled = connected
            binding.lsSleepSwitch.isEnabled = connected && model.isESP32()
            binding.shutdownButton.isEnabled = connected
            binding.rebootButton.isEnabled = connected
        }

        binding.positionBroadcastPeriodEditText.on(EditorInfo.IME_ACTION_DONE) {
            val textEdit = binding.positionBroadcastPeriodEditText
            val n = textEdit.text.toString().toIntOrNull()
            val minBroadcastPeriodSecs =
                ChannelOption.fromConfig(model.deviceConfig.value?.lora?.modemPreset)?.minBroadcastPeriodSecs
                    ?: ChannelOption.defaultMinBroadcastPeriod

            if (n != null && n < MAX_INT_DEVICE && (n == 0 || n >= minBroadcastPeriodSecs)) {
                exceptionToSnackbar(requireView()) {
                    model.positionBroadcastSecs = n
                }
            } else {
                // restore the value in the edit field
                textEdit.setText(model.positionBroadcastSecs.toString())
                val errorText =
                    if (n == null || n < 0 || n >= MAX_INT_DEVICE)
                        "Bad value: ${textEdit.text.toString()}"
                    else
                        getString(R.string.broadcast_period_too_small).format(minBroadcastPeriodSecs)

                Snackbar.make(requireView(), errorText, Snackbar.LENGTH_LONG).show()
            }
            requireActivity().hideKeyboard()
        }

        binding.positionBroadcastSwitch.setOnCheckedChangeListener { view, isChecked ->
            if (view.isPressed) {
                model.gpsDisabled = !isChecked
                debug("User changed locationShare to $isChecked")
            }
        }

        binding.lsSleepEditText.on(EditorInfo.IME_ACTION_DONE) {
            val str = binding.lsSleepEditText.text.toString()
            val n = str.toIntOrNull()
            if (n != null && n < MAX_INT_DEVICE && n >= 0) {
                exceptionToSnackbar(requireView()) {
                    model.lsSleepSecs = n
                }
            } else {
                Snackbar.make(requireView(), "Bad value: $str", Snackbar.LENGTH_LONG).show()
            }
            requireActivity().hideKeyboard()
        }

        binding.lsSleepSwitch.setOnCheckedChangeListener { view, isChecked ->
            if (view.isPressed) {
                model.isPowerSaving = isChecked
                debug("User changed isPowerSaving to $isChecked")
            }
        }

        binding.shutdownButton.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setMessage("${getString(R.string.shutdown)}?")
                .setNeutralButton(R.string.cancel) { _, _ ->
                }
                .setPositiveButton(getString(R.string.okay)) { _, _ ->
                    model.requestShutdown()
                }
                .show()
        }

        binding.rebootButton.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setMessage("${getString(R.string.reboot)}?")
                .setNeutralButton(R.string.cancel) { _, _ ->
                }
                .setPositiveButton(getString(R.string.okay)) { _, _ ->
                    model.requestReboot()
                }
                .show()
        }
    }
}