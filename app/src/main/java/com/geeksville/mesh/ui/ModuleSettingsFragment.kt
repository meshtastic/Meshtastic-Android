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
import com.google.accompanist.themeadapter.material.MdcTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class ModuleSettingsFragment : ScreenFragment("Module Settings"), Logging {

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
                            ModuleSettingsItemList(model)
                        }
                    }
                }
            }
        return binding.root
    }
}
