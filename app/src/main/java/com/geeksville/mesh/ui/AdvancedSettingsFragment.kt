package com.geeksville.mesh.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Observer
import com.geeksville.android.Logging
import com.geeksville.android.hideKeyboard
import com.geeksville.mesh.R
import com.geeksville.mesh.databinding.AdvancedSettingsBinding
import com.geeksville.mesh.model.ChannelOption
import com.geeksville.mesh.model.UIViewModel
import com.geeksville.mesh.service.MeshService
import com.geeksville.util.exceptionToSnackbar
import com.google.android.material.snackbar.Snackbar

class AdvancedSettingsFragment : ScreenFragment("Advanced Settings"), Logging {
    private val MAX_INT_DEVICE = 0xFFFFFFFF
    private var _binding: AdvancedSettingsBinding? = null

    private val binding get() = _binding!!

    private val model: UIViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = AdvancedSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        model.radioConfig.observe(viewLifecycleOwner, { _ ->
            binding.positionBroadcastPeriodEditText.setText(model.positionBroadcastSecs.toString())
            binding.lsSleepEditText.setText(model.lsSleepSecs.toString())
        })

        model.isConnected.observe(viewLifecycleOwner, Observer { connectionState ->
            val connected = connectionState == MeshService.ConnectionState.CONNECTED
            binding.positionBroadcastPeriodView.isEnabled = connected
            binding.lsSleepView.isEnabled = connected
        })

        binding.positionBroadcastPeriodEditText.on(EditorInfo.IME_ACTION_DONE) {
            val textEdit = binding.positionBroadcastPeriodEditText
            val n = textEdit.text.toString().toIntOrNull()
            val minBroadcastPeriodSecs =
                ChannelOption.fromConfig(model.primaryChannel?.modemConfig)?.minBroadcastPeriodSecs
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
    }
}