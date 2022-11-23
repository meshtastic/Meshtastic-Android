package com.geeksville.mesh.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.activityViewModels
import com.geeksville.mesh.android.Logging
import com.geeksville.mesh.databinding.ComposeViewBinding
import com.geeksville.mesh.model.UIViewModel
import com.google.android.material.composethemeadapter.MdcTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class DeviceSettingsFragment : ScreenFragment("Advanced Settings"), Logging {

    private var _binding: ComposeViewBinding? = null
    private val binding get() = _binding!!
    private val model: UIViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = ComposeViewBinding.inflate(inflater, container, false)
            .apply {
                composeView.apply {
                    setViewCompositionStrategy(
                        ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
                    )
                    setContent {
                        MdcTheme {
                            DeviceSettingsItemList(model)
                        }
                    }
                }
            }
        return binding.root
    }
}
