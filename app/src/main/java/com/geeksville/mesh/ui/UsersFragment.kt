package com.geeksville.mesh.ui

import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.PopupMenu
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
import androidx.compose.runtime.rememberCoroutineScope
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
import com.geeksville.mesh.ui.theme.AppTheme
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class UsersFragment : ScreenFragment("Users"), Logging {

    private val model: UIViewModel by activityViewModels()

    private fun popup(view: View, node: NodeInfo) {
        if (!model.isConnected()) return
        val user = node.user ?: return
        val isOurNode = node.num == model.myNodeNum
        val showAdmin = isOurNode || model.hasAdminChannel
        val ignoreIncomingList = model.ignoreIncomingList
        val isIgnored = ignoreIncomingList.contains(node.num)
        val popup =
            PopupMenu(view.context, view, Gravity.NO_GRAVITY, R.attr.actionOverflowMenuStyle, 0)
        popup.inflate(R.menu.menu_nodes)
        popup.menu.setGroupVisible(R.id.group_remote, !isOurNode)
        popup.menu.setGroupVisible(R.id.group_admin, showAdmin)
        popup.menu.setGroupEnabled(R.id.group_admin, !model.isManaged)
        popup.menu.findItem(R.id.ignore).apply {
            isEnabled = isIgnored || ignoreIncomingList.size < 3
            isChecked = isIgnored
        }
        popup.setOnMenuItemClickListener { item: MenuItem ->
            when (item.itemId) {
                R.id.direct_message -> {
                    val contactKey = "${node.channel}${user.id}"
                    debug("calling MessagesFragment filter: $contactKey")
                    parentFragmentManager.navigateToMessages(contactKey, user.longName)
                }

                R.id.request_position -> {
                    debug("requesting position for '${user.longName}'")
                    model.requestPosition(node.num)
                }

                R.id.traceroute -> {
                    debug("requesting traceroute for '${user.longName}'")
                    model.requestTraceroute(node.num)
                }

                R.id.remove -> {
                    MaterialAlertDialogBuilder(view.context)
                        .setTitle(R.string.remove)
                        .setMessage(getString(R.string.remove_node_text))
                        .setNeutralButton(R.string.cancel) { _, _ -> }
                        .setPositiveButton(R.string.send) { _, _ ->
                            debug("removing node '${user.longName}'")
                            model.removeNode(node.num)
                        }
                        .show()
                }

                R.id.ignore -> {
                    val message = if (isIgnored) R.string.ignore_remove else R.string.ignore_add
                    MaterialAlertDialogBuilder(view.context)
                        .setTitle(R.string.ignore)
                        .setMessage(getString(message, user.longName))
                        .setNeutralButton(R.string.cancel) { _, _ -> }
                        .setPositiveButton(R.string.send) { _, _ ->
                            model.ignoreIncomingList = ignoreIncomingList.toMutableList().apply {
                                if (isIgnored) {
                                    debug("removed '${user.longName}' from ignore list")
                                    remove(node.num)
                                } else {
                                    debug("added '${user.longName}' to ignore list")
                                    add(node.num)
                                }
                            }
                            item.isChecked = !item.isChecked
                        }
                        .show()
                }

                R.id.remote_admin -> {
                    debug("calling remote admin --> destNum: ${node.num.toUInt()}")
                    parentFragmentManager.navigateToRadioConfig(node.num)
                }
            }
            true
        }
        popup.show()
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
                    NodesScreen(model = model, onClick = { popup(requireView(), it) })
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NodesScreen(
    model: UIViewModel = hiltViewModel(),
    onClick: (NodeInfo) -> Unit,
) {
    val state by model.nodesUiState.collectAsStateWithLifecycle()

    val nodes by model.nodeList.collectAsStateWithLifecycle()
    val ourNodeInfo by model.ourNodeInfo.collectAsStateWithLifecycle()

    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    val focusedNode by model.focusedNode.collectAsStateWithLifecycle()
    LaunchedEffect(focusedNode) {
        focusedNode?.let { node ->
            val index = nodes.indexOfFirst { it == node }
            if (index != -1) {
                coroutineScope.launch {
                    listState.animateScrollToItem(index)
                    model.focusUserNode(null)
                }
            }
        }
    }

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
                onClicked = { onClick(node) },
                blinking = node == focusedNode,
            )
        }
    }
}
