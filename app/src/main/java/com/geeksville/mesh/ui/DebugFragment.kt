package com.geeksville.mesh.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.asLiveData
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.geeksville.mesh.R
import com.geeksville.mesh.databinding.FragmentDebugBinding
import com.geeksville.mesh.model.UIViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class DebugFragment : Fragment() {

    private var _binding: FragmentDebugBinding? = null

    // This property is only valid between onCreateView and onDestroyView.
    private val binding get() = _binding!!

    private val model: UIViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDebugBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val recyclerView = view.findViewById<RecyclerView>(R.id.debug_recyclerview)
        val adapter = DebugAdapter(requireContext())

        recyclerView.adapter = adapter
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        binding.clearButton.setOnClickListener {
            model.deleteAllLogs()
        }

        binding.closeButton.setOnClickListener {
            parentFragmentManager.popBackStack()
        }
        model.meshLog.asLiveData().observe(viewLifecycleOwner) { logs ->
            logs?.let { adapter.setLogs(it) }
        }
    }
}