package com.geeksville.mesh.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.geeksville.mesh.R
import com.geeksville.mesh.model.UIViewModel
import kotlinx.android.synthetic.main.debug_fragment.*

class DebugFragment : Fragment() {

    val model: UIViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        return inflater.inflate(R.layout.debug_fragment, container, false)
    }
    //Button to clear All log

    //List all log

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val recyclerView = view.findViewById<RecyclerView>(R.id.packets_recyclerview)
        val adapter = PacketListAdapter(requireContext())

        recyclerView.adapter = adapter
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        clearButton.setOnClickListener {
            model.deleteAllPacket()
        }

        closeButton.setOnClickListener{
            parentFragmentManager.popBackStack();
        }
        model.allPackets.observe(viewLifecycleOwner, Observer {
            packets -> packets?.let { adapter.setPackets(it) }
        })
    }
}