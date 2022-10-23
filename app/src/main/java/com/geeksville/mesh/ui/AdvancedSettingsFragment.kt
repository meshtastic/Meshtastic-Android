package com.geeksville.mesh.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.asLiveData
import com.geeksville.mesh.android.Logging
import com.geeksville.mesh.android.hideKeyboard
import com.geeksville.mesh.R
import com.geeksville.mesh.copy
import com.geeksville.mesh.databinding.AdvancedSettingsBinding
import com.geeksville.mesh.model.ChannelOption
import com.geeksville.mesh.model.UIViewModel
import com.geeksville.mesh.service.MeshService
import com.geeksville.mesh.util.exceptionToSnackbar
import com.google.android.material.composethemeadapter.MdcTheme
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
            .apply {
                deviceConfig.setContent {
                    MdcTheme {
                        // SettingsView(viewModel = model)
                        SettingsDescription()
                    }
                }
            }
        return binding.root
    }

    @Composable
    fun SettingsDescription() {
        Text("Hello Compose")
    }

//    override fun onViewCreated(view: View, savedInstanceState: Bundle?) { // TODO convert to Compose
//        super.onViewCreated(view, savedInstanceState)
//
//        model.localConfig.asLiveData().observe(viewLifecycleOwner) {
//            binding.positionBroadcastPeriodEditText.setText(model.config.position.positionBroadcastSecs.toString())
//            binding.lsSleepEditText.setText(model.config.power.lsSecs.toString())
//            binding.positionBroadcastPeriodView.isEnabled = model.config.position.gpsEnabled
//            binding.positionBroadcastSwitch.isChecked = model.config.position.gpsEnabled
//            binding.lsSleepView.isEnabled = model.config.power.isPowerSaving && model.isESP32()
//            binding.lsSleepSwitch.isChecked = model.config.power.isPowerSaving && model.isESP32()
//        }
//
//        model.connectionState.observe(viewLifecycleOwner) { connectionState ->
//            val connected = connectionState == MeshService.ConnectionState.CONNECTED
//            binding.positionBroadcastPeriodView.isEnabled = connected && model.config.position.gpsEnabled
//            binding.lsSleepView.isEnabled = connected && model.config.power.isPowerSaving
//            binding.positionBroadcastSwitch.isEnabled = connected
//            binding.lsSleepSwitch.isEnabled = connected && model.isESP32()
//        }
//
//        binding.positionBroadcastPeriodEditText.on(EditorInfo.IME_ACTION_DONE) {
//            val textEdit = binding.positionBroadcastPeriodEditText
//            val n = textEdit.text.toString().toIntOrNull()
//            val minBroadcastPeriodSecs =
//                ChannelOption.fromConfig(model.config.lora.modemPreset)?.minBroadcastPeriodSecs
//                    ?: ChannelOption.defaultMinBroadcastPeriod
//
//            if (n != null && n < MAX_INT_DEVICE && (n == 0 || n >= minBroadcastPeriodSecs)) {
//                exceptionToSnackbar(requireView()) {
//                    model.updatePositionConfig { it.copy { positionBroadcastSecs = n } }
//                }
//            } else {
//                // restore the value in the edit field
//                textEdit.setText(model.config.position.positionBroadcastSecs.toString())
//                val errorText =
//                    if (n == null || n < 0 || n >= MAX_INT_DEVICE)
//                        "Bad value: ${textEdit.text.toString()}"
//                    else
//                        getString(R.string.broadcast_period_too_small).format(minBroadcastPeriodSecs)
//
//                Snackbar.make(requireView(), errorText, Snackbar.LENGTH_LONG).show()
//            }
//            requireActivity().hideKeyboard()
//        }
//
//        binding.positionBroadcastSwitch.setOnCheckedChangeListener { btn, isChecked ->
//            if (btn.isPressed) {
//                model.updatePositionConfig { it.copy { gpsEnabled = isChecked } }
//                debug("User changed locationShare to $isChecked")
//            }
//        }
//
//        binding.lsSleepEditText.on(EditorInfo.IME_ACTION_DONE) {
//            val str = binding.lsSleepEditText.text.toString()
//            val n = str.toIntOrNull()
//            if (n != null && n < MAX_INT_DEVICE && n >= 0) {
//                exceptionToSnackbar(requireView()) {
//                    model.updatePowerConfig { it.copy { lsSecs = n } }
//                }
//            } else {
//                Snackbar.make(requireView(), "Bad value: $str", Snackbar.LENGTH_LONG).show()
//            }
//            requireActivity().hideKeyboard()
//        }
//
//        binding.lsSleepSwitch.setOnCheckedChangeListener { btn, isChecked ->
//            if (btn.isPressed) {
//                model.updatePowerConfig { it.copy { isPowerSaving = isChecked } }
//                debug("User changed isPowerSaving to $isChecked")
//            }
//        }
//    }
}
