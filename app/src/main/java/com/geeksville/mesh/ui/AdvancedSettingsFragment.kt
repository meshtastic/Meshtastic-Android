package com.geeksville.mesh.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.activityViewModels
import com.geeksville.mesh.android.Logging
import com.geeksville.mesh.databinding.AdvancedSettingsBinding
import com.geeksville.mesh.model.UIViewModel
import com.google.android.material.composethemeadapter.MdcTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class AdvancedSettingsFragment : ScreenFragment("Advanced Settings"), Logging {

    private var _binding: AdvancedSettingsBinding? = null

    private val binding get() = _binding!!

    private val model: UIViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = AdvancedSettingsBinding.inflate(inflater, container, false)
            .apply {
                deviceConfig.apply {
                    setViewCompositionStrategy(
                        ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
                    )
                    setContent {
                        MdcTheme {
                            PreferenceScreen(model)
                        }
                    }
                }
            }
        return binding.root
    }

//        model.localConfig.asLiveData().observe(viewLifecycleOwner) {
//            binding.positionBroadcastPeriodView.isEnabled = model.config.position.gpsEnabled
//            binding.lsSleepView.isEnabled = model.config.power.isPowerSaving && model.isESP32()
//        }
//
//        model.connectionState.observe(viewLifecycleOwner) { connectionState ->
//            val connected = connectionState == MeshService.ConnectionState.CONNECTED
//            binding.positionBroadcastPeriodView.isEnabled = connected && model.config.position.gpsEnabled
//            binding.lsSleepView.isEnabled = connected && model.config.power.isPowerSaving
//        }
}
