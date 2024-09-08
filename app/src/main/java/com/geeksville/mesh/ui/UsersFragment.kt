package com.geeksville.mesh.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import androidx.fragment.app.activityViewModels
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geeksville.mesh.NodeInfo
import com.geeksville.mesh.R
import com.geeksville.mesh.android.Logging
import com.geeksville.mesh.model.UIViewModel
import com.geeksville.mesh.ui.components.NodeFilterTextField
import com.geeksville.mesh.ui.components.rememberTimeTickWithLifecycle
import com.geeksville.mesh.ui.theme.AppTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class UsersFragment : ScreenFragment("Users"), Logging {

    private val model: UIViewModel by activityViewModels()

    private fun popup(node: NodeInfo) {
        if (!model.isConnected()) return
        val isOurNode = node.num == model.myNodeNum
        val ignoreIncomingList = model.ignoreIncomingList

        requireView().nodeMenu(
            node = node,
            ignoreIncomingList = ignoreIncomingList,
            isOurNode = isOurNode,
            showAdmin = isOurNode || model.hasAdminChannel,
            isManaged = model.isManaged,
        ) {
            when (itemId) {
                R.id.direct_message -> {
                    navigateToMessages(node)
                }

                R.id.request_position -> {
                    model.requestPosition(node.num)
                }

                R.id.traceroute -> {
                    model.requestTraceroute(node.num)
                }

                R.id.remove -> {
                    model.removeNode(node.num)
                }

                R.id.ignore -> {
                    model.ignoreIncomingList = ignoreIncomingList.toMutableList().apply {
                        if (contains(node.num)) {
                            debug("removed '${node.num}' from ignore list")
                            remove(node.num)
                        } else {
                            debug("added '${node.num}' to ignore list")
                            add(node.num)
                        }
                    }
                }

                R.id.remote_admin -> {
                    navigateToRadioConfig(node)
                }

                R.id.metrics -> {
                    navigateToMetrics(node)
                }
            }
        }
    }

    private fun navigateToMessages(node: NodeInfo) = node.user?.let { user ->
        val contactKey = "${node.channel}${user.id}"
        info("calling MessagesFragment filter: $contactKey")
        parentFragmentManager.navigateToMessages(contactKey, user.longName)
    }

    private fun navigateToRadioConfig(node: NodeInfo) {
        info("calling RadioConfig --> destNum: ${node.num}")
        parentFragmentManager.navigateToRadioConfig(node.num)
    }

    private fun navigateToMetrics(node: NodeInfo) {
        info("calling Metrics --> destNum: ${node.num}")
        parentFragmentManager.navigateToMetrics(node.num)
    }


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                AppTheme {
                    NodesScreen(model = model, chipClicked = ::popup)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NodesScreen(
    model: UIViewModel = hiltViewModel(),
    chipClicked: (NodeInfo) -> Unit,
) {
    val state by model.nodesUiState.collectAsStateWithLifecycle()

    val nodes by model.nodeList.collectAsStateWithLifecycle()
    val ourNodeInfo by model.ourNodeInfo.collectAsStateWithLifecycle()

    val listState = rememberLazyListState()
    val focusedNode by model.focusedNode.collectAsStateWithLifecycle()
    LaunchedEffect(focusedNode) {
        focusedNode?.let { node ->
            val index = nodes.indexOfFirst { it == node }
            if (index != -1) {
                listState.animateScrollToItem(index)
            }
            model.focusUserNode(null)
        }
    }

    val currentTimeMillis = rememberTimeTickWithLifecycle()

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
    ) {
        stickyHeader {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colors.background)
                    .padding(8.dp),
            ) {
                NodeFilterTextField(
                    filterText = state.filter,
                    onTextChanged = model::setNodeFilterText,
                    modifier = Modifier.weight(1f)
                )
                NodeSortButton(
                    currentSortOption = state.sort,
                    onSortSelected = model::setSortOption,
                    includeUnknown = state.includeUnknown,
                    onToggleIncludeUnknown = model::toggleIncludeUnknown,
                    showDetails = state.showDetails,
                    onToggleShowDetails = model::toggleShowDetails,
                )
            }
        }

        items(nodes, key = { it.num }) { node ->
            NodeInfo(
                thisNodeInfo = ourNodeInfo,
                thatNodeInfo = node,
                gpsFormat = state.gpsFormat,
                distanceUnits = state.distanceUnits,
                tempInFahrenheit = state.tempInFahrenheit,
                isIgnored = state.ignoreIncomingList.contains(node.num),
                chipClicked = { chipClicked(node) },
                blinking = node == focusedNode,
                expanded = state.showDetails,
                currentTimeMillis = currentTimeMillis
            )
        }
    }
}
