package com.geeksville.mesh.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import com.geeksville.android.Logging
import com.geeksville.mesh.databinding.AdvancedSettingsBinding
import com.geeksville.mesh.databinding.QuickChatSettingsFragmentBinding
import com.geeksville.mesh.model.UIViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class QuickChatSettingsFragment : ScreenFragment("Quick Chat settings"), Logging {
    private var _binding: QuickChatSettingsFragmentBinding? = null

    private val binding get() = _binding!!

    private val model: UIViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = QuickChatSettingsFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // TODO
    }
}