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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.activityViewModels
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geeksville.mesh.DataPacket
import com.geeksville.mesh.R
import com.geeksville.mesh.android.BuildUtils.debug
import com.geeksville.mesh.android.Logging
import com.geeksville.mesh.database.entity.NodeEntity
import com.geeksville.mesh.model.UIViewModel
import com.geeksville.mesh.ui.components.MenuItemAction
import com.geeksville.mesh.ui.components.NodeFilterTextField
import com.geeksville.mesh.ui.components.SimpleAlertDialog
import com.geeksville.mesh.ui.components.rememberTimeTickWithLifecycle
import com.geeksville.mesh.ui.theme.AppTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class UsersFragment : ScreenFragment("Users"), Logging {

    private val model: UIViewModel by activityViewModels()

    private fun navigateToMessages(node: NodeEntity) = node.user.let { user ->
        val hasPKC = model.ourNodeInfo.value?.hasPKC == true && node.hasPKC // TODO use meta.hasPKC
        val channel = if (hasPKC) DataPacket.PKC_CHANNEL_INDEX else node.channel
        val contactKey = "$channel${user.id}"
        info("calling MessagesFragment filter: $contactKey")
        parentFragmentManager.navigateToMessages(contactKey, user.longName)
    }

    private fun navigateToNodeDetails(nodeNum: Int) {
        info("calling NodeDetails --> destNum: $nodeNum")
        parentFragmentManager.navigateToNavGraph(nodeNum, "NodeDetails")
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
                    NodesScreen(
                        model = model,
                        navigateToMessages = ::navigateToMessages,
                        navigateToNodeDetails = ::navigateToNodeDetails,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
@Suppress("LongMethod", "CyclomaticComplexMethod")
fun NodesScreen(
    model: UIViewModel = hiltViewModel(),
    navigateToMessages: (NodeEntity) -> Unit,
    navigateToNodeDetails: (Int) -> Unit,
) {
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

    var displayIgnoreDialogForNode: NodeEntity? by remember { mutableStateOf(null) }
    var displayRemoveDialogForNode: NodeEntity? by remember { mutableStateOf(null) }
    if (displayIgnoreDialogForNode != null) {
        val isIgnored = state.ignoreIncomingList.contains(displayIgnoreDialogForNode!!.num)
        SimpleAlertDialog(
            title = R.string.ignore,
            text = stringResource(
                id = if (isIgnored) R.string.ignore_remove else R.string.ignore_add,
                displayIgnoreDialogForNode?.user?.longName.orEmpty()
            ),
            onConfirm = {
                model.ignoreIncomingList =
                    state.ignoreIncomingList.toMutableList().apply {
                        if (isIgnored) {
                            debug("removed '${displayIgnoreDialogForNode!!.num}' from ignore list")
                            remove(displayIgnoreDialogForNode!!.num)
                        } else {
                            debug("added '${displayIgnoreDialogForNode!!.num}' to ignore list")
                            add(displayIgnoreDialogForNode!!.num)
                        }
                    }
                displayIgnoreDialogForNode = null
            },
            onDismiss = {
                displayIgnoreDialogForNode = null
            }
        )
    }
    if (displayRemoveDialogForNode != null) {
        SimpleAlertDialog(
            title = R.string.remove,
            text = R.string.remove_node_text,
            onConfirm = {
                displayIgnoreDialogForNode?.let {
                    model.removeNode(it.num)
                }
                displayRemoveDialogForNode = null
            },
            onDismiss = {
                displayRemoveDialogForNode = null
            }
        )
    }
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
                ignoreIncomingList = state.ignoreIncomingList,
                isIgnored = state.ignoreIncomingList.contains(node.num),
                menuItemActionClicked = { menuItem ->
                    when (menuItem) {
                        MenuItemAction.Remove -> {
                            displayRemoveDialogForNode = node
                        }

                        MenuItemAction.Ignore -> {
                            displayIgnoreDialogForNode = node
                        }

                        MenuItemAction.DirectMessage -> {
                            navigateToMessages(node)
                        }

                        MenuItemAction.RequestUserInfo -> {
                            model.requestUserInfo(node.num)
                        }

                        MenuItemAction.RequestPosition -> {
                            model.requestPosition(node.num)
                        }

                        MenuItemAction.TraceRoute -> {
                            model.requestTraceroute(node.num)
                        }

                        MenuItemAction.MoreDetails -> {
                            navigateToNodeDetails(node.num)
                        }
                    }
                },
                blinking = node == focusedNode,
                expanded = state.showDetails,
                currentTimeMillis = currentTimeMillis,
                isConnected = model.isConnected(),
            )
        }
    }
}
