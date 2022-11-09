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
}
