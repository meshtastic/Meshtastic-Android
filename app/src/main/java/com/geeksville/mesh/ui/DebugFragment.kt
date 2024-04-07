package com.geeksville.mesh.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.asLiveData
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.geeksville.mesh.CoroutineDispatchers
import com.geeksville.mesh.R
import com.geeksville.mesh.database.entity.MeshLog
import com.geeksville.mesh.databinding.FragmentDebugBinding
import com.geeksville.mesh.model.DebugViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class DebugFragment : Fragment() {

    private var _binding: FragmentDebugBinding? = null

    // This property is only valid between onCreateView and onDestroyView.
    private val binding get() = _binding!!

    private val model: DebugViewModel by viewModels()

    @Inject
    lateinit var dispatchers: CoroutineDispatchers

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
        model.meshLog
            .map(this::annotateMeshLogs)
            .flowOn(dispatchers.default)
            .asLiveData()
            .observe(viewLifecycleOwner) { logs ->
            logs?.let { adapter.setLogs(it) }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    /**
     * Transform the input list by enhancing the raw message with annotations.
     */
    private fun annotateMeshLogs(logs: List<MeshLog>): List<MeshLog> {
        return logs.map { meshLog ->
            val annotated = when (meshLog.message_type) {
                "Packet" -> {
                    meshLog.meshPacket?.let { packet ->
                        annotateRawMessage(meshLog.raw_message, packet.from, packet.to)
                    }
                }
                "NodeInfo" -> {
                    meshLog.nodeInfo?.let { nodeInfo ->
                        annotateRawMessage(meshLog.raw_message, nodeInfo.num)
                    }
                }
                "MyNodeInfo" -> {
                    meshLog.myNodeInfo?.let { nodeInfo ->
                        annotateRawMessage(meshLog.raw_message, nodeInfo.myNodeNum)
                    }
                }
                else -> null
            }
            if (annotated == null) {
                meshLog
            } else {
                meshLog.copy(raw_message = annotated)
            }
        }
    }

    /**
     * Annotate the raw message string with the node IDs provided, in hex, if they are present.
     */
    private fun annotateRawMessage(rawMessage: String, vararg nodeIds: Int): String {
        val msg = StringBuilder(rawMessage)
        var mutated = false
        nodeIds.forEach { nodeId ->
            mutated = mutated or msg.annotateNodeId(nodeId)
        }
        return if (mutated) {
            return msg.toString()
        } else {
            rawMessage
        }
    }

    /**
     * Look for a single node ID integer in the string and annotate it with the hex equivalent
     * if found.
     */
    private fun StringBuilder.annotateNodeId(nodeId: Int): Boolean {
        val nodeIdStr = nodeId.toUInt().toString()
        indexOf(nodeIdStr).takeIf { it >= 0 }?.let { idx ->
            insert(idx + nodeIdStr.length, " (${nodeId.asNodeId()})")
            return true
        }
        return false
    }

    private fun Int.asNodeId(): String {
        return "!%08x".format(Locale.getDefault(), this)
    }
}