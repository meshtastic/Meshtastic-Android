package com.geeksville.mesh.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import androidx.fragment.app.activityViewModels
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geeksville.mesh.DataPacket
import com.geeksville.mesh.R
import com.geeksville.mesh.android.Logging
import com.geeksville.mesh.database.entity.NodeEntity
import com.geeksville.mesh.model.UIViewModel
import com.geeksville.mesh.ui.components.NodeFilterTextField
import com.geeksville.mesh.ui.components.rememberTimeTickWithLifecycle
import com.geeksville.mesh.ui.theme.AppTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class UsersFragment : ScreenFragment("Users"), Logging {

    private val model: UIViewModel by activityViewModels()

    private fun popup(node: NodeEntity) {
        if (!model.isConnected()) return
        val isOurNode = node.num == model.myNodeNum
        val ignoreIncomingList = model.ignoreIncomingList

        requireView().nodeMenu(
            node = node,
            ignoreIncomingList = ignoreIncomingList,
            isOurNode = isOurNode,
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

                R.id.more_details -> {
                    navigateToRadioConfig(node.num)
                }

                R.id.request_userinfo -> {
                    model.requestUserInfo(node.num)
                }
            }
        }
    }

    private fun navigateToMessages(node: NodeEntity) = node.user.let { user ->
        val hasPKC = model.ourNodeInfo.value?.hasPKC == true && node.hasPKC // TODO use meta.hasPKC
        val channel = if (hasPKC) DataPacket.PKC_CHANNEL_INDEX else node.channel
        val contactKey = "$channel${user.id}"
        info("calling MessagesFragment filter: $contactKey")
        parentFragmentManager.navigateToMessages(contactKey, user.longName)
    }

    private fun navigateToRadioConfig(nodeNum: Int) {
        info("calling NodeDetails --> destNum: $nodeNum")
        parentFragmentManager.navigateToRadioConfig(nodeNum, "NodeDetails")
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
    chipClicked: (NodeEntity) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val state by model.nodesUiState.collectAsStateWithLifecycle()

    val nodes by model.nodeList.collectAsStateWithLifecycle()
    val ourNode by model.ourNodeInfo.collectAsStateWithLifecycle()

    val listState = rememberLazyListState()
    val focusedNode by model.focusedNode.collectAsStateWithLifecycle()
    LaunchedEffect(focusedNode) {
        focusedNode?.let { node ->
            val index = nodes.indexOfFirst { it.num == node.num }
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
            NodeFilterTextField(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                filterText = state.filter,
                onTextChange = model::setNodeFilterText,
                currentSortOption = state.sort,
                onSortSelect = model::setSortOption,
                includeUnknown = state.includeUnknown,
                onToggleIncludeUnknown = model::toggleIncludeUnknown,
                showDetails = state.showDetails,
                onToggleShowDetails = model::toggleShowDetails,
            )
        }

        items(nodes, key = { it.num }) { node ->
            NodeItem(
                thisNode = ourNode,
                thatNode = node,
                gpsFormat = state.gpsFormat,
                distanceUnits = state.distanceUnits,
                tempInFahrenheit = state.tempInFahrenheit,
                isIgnored = state.ignoreIncomingList.contains(node.num),
                chipClicked = {
                    focusManager.clearFocus()
                    chipClicked(node)
                },
                blinking = node == focusedNode,
                expanded = state.showDetails,
                currentTimeMillis = currentTimeMillis,
            )
        }
    }
}
