package com.geeksville.mesh.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.core.os.bundleOf
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geeksville.mesh.DeviceMetrics
import com.geeksville.mesh.R
import com.geeksville.mesh.android.Logging
import com.geeksville.mesh.model.DebugViewModel
import com.geeksville.mesh.model.UIViewModel
import com.geeksville.mesh.ui.components.DataEntry
import com.geeksville.mesh.ui.components.DeviceMetricsCard
import com.geeksville.mesh.ui.components.DeviceMetricsChart
import com.geeksville.mesh.ui.theme.AppTheme
import dagger.hilt.android.AndroidEntryPoint

internal fun FragmentManager.navigateToNodeDetails(nodeNum: Int? = null) {
    val nodeDetailsFragment = NodeDetailsFragment().apply {
        arguments = bundleOf("nodeNum" to nodeNum)
    }
    beginTransaction()
        .replace(R.id.mainActivityLayout, nodeDetailsFragment)
        .addToBackStack(null)
        .commit()
}

@AndroidEntryPoint
class NodeDetailsFragment : ScreenFragment("NodeDetails"), Logging {

    private val model: UIViewModel by activityViewModels()

    private val debugModel: DebugViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val nodeNum = arguments?.getInt("nodeNum")

        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                AppTheme {
                    NodeDetailsScreen(
                        model = model,
                        debugModel = debugModel,
                        nodeNum = nodeNum,
                        navigateBack = {
                            parentFragmentManager.popBackStack()
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NodeDetailsScreen(
    model: UIViewModel = hiltViewModel(),
    debugModel: DebugViewModel, // TODO do I need something similar to hiltViewModel()?
    nodeNum: Int? = null,
    navigateBack: () -> Unit,
) {
    val ourNodeInfo by model.ourNodeInfo.collectAsStateWithLifecycle()
    val selectedNodeInfo by model.getNodeByNum(nodeNum ?: ourNodeInfo?.num ?: 0)
        .collectAsStateWithLifecycle()

    val nodeId = selectedNodeInfo?.user?.id
    val logs by debugModel.meshLog.collectAsStateWithLifecycle()
    val data = mutableListOf<DataEntry>()

    /* Retrieve only the data belonging to the selected node. */
    for (log in logs) {
        log.nodeInfo?.let { nodeInfo ->
            val currentId = nodeInfo.user.id
            if (nodeInfo.hasDeviceMetrics() && currentId == nodeId)
                data.add(DataEntry(log.received_date, DeviceMetrics(nodeInfo.deviceMetrics)))
        }
    }


    Scaffold(
        /* TODO NOTE: Suggesting that we add a bottom bar that allows the user to toggle between graphs */
        topBar = {
            TopAppBar(
                backgroundColor = colorResource(R.color.toolbarBackground),
                contentColor = colorResource(R.color.toolbarText),
                title = {
                    Text(
                        text = "Node Details: ${selectedNodeInfo?.user?.shortName}", // TODO res
                    )
                },
                navigationIcon = {
                    IconButton(onClick = navigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.navigate_back),
                        )
                    }
                }
            )
        },
    ) { innerPadding ->

        Column {
            val reversed = data.reversed()
            DeviceMetricsChart(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.33f),
                reversed.toMutableList()
            )

            /* Device Metric Cards */
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                items(data) { dataEntry -> DeviceMetricsCard(dataEntry) }
            }
        }
    }
}
