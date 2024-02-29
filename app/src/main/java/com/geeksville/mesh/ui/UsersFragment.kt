package com.geeksville.mesh.ui

import android.animation.ValueAnimator
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import androidx.appcompat.widget.PopupMenu
import androidx.compose.ui.platform.ComposeView
import androidx.core.animation.doOnEnd
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.asLiveData
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSmoothScroller
import androidx.recyclerview.widget.RecyclerView
import com.geeksville.mesh.NodeInfo
import com.geeksville.mesh.R
import com.geeksville.mesh.android.Logging
import com.geeksville.mesh.databinding.NodelistFragmentBinding
import com.geeksville.mesh.model.UIViewModel
import com.geeksville.mesh.ui.theme.AppTheme
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class UsersFragment : ScreenFragment("Users"), Logging {

    private var _binding: NodelistFragmentBinding? = null

    // This property is only valid between onCreateView and onDestroyView.
    private val binding get() = _binding!!

    private val model: UIViewModel by activityViewModels()

    private val ignoreIncomingList: MutableList<Int> = mutableListOf()
    private var gpsFormat = 0
    private var displayUnits = 0
    private var displayFahrenheit = false

    class ViewHolder(val composeView: ComposeView) : RecyclerView.ViewHolder(composeView) {

        // TODO not working with compose changes
        fun blink() {
            val bg = composeView.backgroundTintList
            ValueAnimator.ofArgb(
                Color.parseColor("#00FFFFFF"),
                Color.parseColor("#33FFFFFF")
            ).apply {
                interpolator = LinearInterpolator()
                startDelay = 500
                duration = 250
                repeatCount = 3
                repeatMode = ValueAnimator.REVERSE
                addUpdateListener {
                    composeView.backgroundTintList = ColorStateList.valueOf(it.animatedValue as Int)
                }
                start()
                doOnEnd {
                    composeView.backgroundTintList = bg
                }
            }
        }

        fun bind(
            thisNodeInfo: NodeInfo,
            thatNodeInfo: NodeInfo,
            gpsFormat: Int,
            distanceUnits: Int,
            tempInFahrenheit: Boolean,
            onChipClicked: () -> Unit
        ) {
            composeView.setContent {
                AppTheme {
                    NodeInfo(
                        thisNodeInfo = thisNodeInfo,
                        thatNodeInfo = thatNodeInfo,
                        gpsFormat = gpsFormat,
                        distanceUnits = distanceUnits,
                        tempInFahrenheit = tempInFahrenheit,
                        onClicked = onChipClicked
                    )
                }
            }
        }
    }

    private val nodesAdapter = object : RecyclerView.Adapter<ViewHolder>() {

        var nodes = arrayOf<NodeInfo>()
            private set

        private fun popup(view: View, position: Int) {
            if (!model.isConnected()) return
            val node = nodes[position]
            val user = node.user ?: return
            val showAdmin = position == 0 || model.adminChannelIndex > 0
            val isIgnored = ignoreIncomingList.contains(node.num)
            val popup = PopupMenu(requireContext(), view)
            popup.inflate(R.menu.menu_nodes)
            popup.menu.setGroupVisible(R.id.group_remote, position > 0)
            popup.menu.setGroupVisible(R.id.group_admin, showAdmin)
            popup.menu.setGroupEnabled(R.id.group_admin, !model.isManaged)
            popup.menu.findItem(R.id.ignore).apply {
                isEnabled = isIgnored || ignoreIncomingList.size < 3
                isChecked = isIgnored
            }
            popup.setOnMenuItemClickListener { item: MenuItem ->
                when (item.itemId) {
                    R.id.direct_message -> {
                        debug("calling MessagesFragment filter: ${node.channel}${user.id}")
                        model.setContactKey("${node.channel}${user.id}")
                        parentFragmentManager.beginTransaction()
                            .replace(R.id.mainActivityLayout, MessagesFragment())
                            .addToBackStack(null)
                            .commit()
                    }
                    R.id.request_position -> {
                        debug("requesting position for '${user.longName}'")
                        model.requestPosition(node.num)
                    }
                    R.id.traceroute -> {
                        debug("requesting traceroute for '${user.longName}'")
                        model.requestTraceroute(node.num)
                    }
                    R.id.ignore -> {
                        val message = if (isIgnored) R.string.ignore_remove else R.string.ignore_add
                        MaterialAlertDialogBuilder(requireContext())
                            .setTitle(R.string.ignore)
                            .setMessage(getString(message, user.longName))
                            .setNeutralButton(R.string.cancel) { _, _ -> }
                            .setPositiveButton(R.string.send) { _, _ ->
                                model.ignoreIncomingList = ignoreIncomingList.apply {
                                    if (isIgnored) {
                                        debug("removed '${user.longName}' from ignore list")
                                        remove(node.num)
                                    } else {
                                        debug("added '${user.longName}' to ignore list")
                                        add(node.num)
                                    }
                                }
                                item.isChecked = !item.isChecked
                                notifyItemChanged(position)
                            }
                            .show()
                    }
                    R.id.remote_admin -> {
                        debug("calling remote admin --> destNum: ${node.num.toUInt()}")
                        model.setDestNode(node)
                        parentFragmentManager.beginTransaction()
                            .replace(R.id.mainActivityLayout, DeviceSettingsFragment())
                            .addToBackStack(null)
                            .commit()
                    }
                }
                true
            }
            popup.show()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            return ViewHolder(ComposeView(parent.context))
        }

        override fun getItemCount(): Int = nodes.size

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val thisNode = nodes[0]
            val thatNode = nodes[position]

            holder.bind(
                thisNodeInfo = thisNode,
                thatNodeInfo = thatNode,
                gpsFormat = gpsFormat,
                distanceUnits = displayUnits,
                tempInFahrenheit = displayFahrenheit
            ) {
                popup(holder.composeView, position)
            }
        }

        override fun onViewRecycled(holder: ViewHolder) {
            holder.composeView.disposeComposition()
        }

        // Called when our node DB changes
        fun onNodesChanged(nodesIn: Array<NodeInfo>) {
            if (nodesIn.size > 1) {
                nodesIn.sortWith(compareByDescending { it.lastHeard }, 1)
            }

            val previousNodes = nodes
            val indexChanged = nodesIn.mapIndexed { index, nodeInfo ->
                previousNodes.getOrNull(index) != nodeInfo
            }
            if (indexChanged.isEmpty()) return

            nodes = nodesIn
            for (i in indexChanged.indices) {
                if (indexChanged[i]) {
                    notifyItemChanged(i)
                }
            }
        }

    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = NodelistFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.nodeListView.adapter = nodesAdapter
        binding.nodeListView.layoutManager = LinearLayoutManager(requireContext())

        model.nodeDB.nodeDBbyNum.asLiveData().observe(viewLifecycleOwner) {
            nodesAdapter.onNodesChanged(it.values.toTypedArray())
        }

        model.localConfig.asLiveData().observe(viewLifecycleOwner) { config ->
            ignoreIncomingList.apply {
                clear()
                addAll(config.lora.ignoreIncomingList)
            }
            gpsFormat = config.display.gpsFormat.number
            displayUnits = config.display.units.number
        }

        model.moduleConfig.asLiveData().observe(viewLifecycleOwner) { module ->
            displayFahrenheit = module.telemetry.environmentDisplayFahrenheit
        }

        model.tracerouteResponse.observe(viewLifecycleOwner) { response ->
            MaterialAlertDialogBuilder(requireContext())
                .setCancelable(false)
                .setTitle(R.string.traceroute)
                .setMessage(response ?: return@observe)
                .setPositiveButton(R.string.okay) { _, _ -> }
                .show()

            model.clearTracerouteResponse()
        }

        model.focusedNode.asLiveData().observe(viewLifecycleOwner) { node ->
            val idx = nodesAdapter.nodes.indexOfFirst {
                it.user?.id == node?.user?.id
            }

            if (idx < 1) return@observe

            lifecycleScope.launch {
                binding.nodeListView.layoutManager?.smoothScrollToTop(idx)
                val vh = binding.nodeListView.findViewHolderForLayoutPosition(idx)
                (vh as? ViewHolder)?.blink()
                model.focusUserNode(null)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    /**
     * Scrolls the recycler view until the item at [position] is at the top of the view, then waits
     * until the scrolling is finished.
     */
    private suspend fun RecyclerView.LayoutManager.smoothScrollToTop(position: Int) {
        this.startSmoothScroll(
            object : LinearSmoothScroller(requireContext()) {
                override fun getVerticalSnapPreference(): Int {
                    return SNAP_TO_START
                }
            }.apply {
                targetPosition = position
            }
        )
        withContext(Dispatchers.Default) {
            while (this@smoothScrollToTop.isSmoothScrolling) {
                // noop
            }
        }
    }
}
