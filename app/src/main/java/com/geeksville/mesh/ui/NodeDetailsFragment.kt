package com.geeksville.mesh.ui

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
import androidx.fragment.app.viewModels
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.geeksville.mesh.R
import com.geeksville.mesh.android.Logging
import com.geeksville.mesh.model.NodeDetailsViewModel
import com.geeksville.mesh.ui.components.DeviceMetricsCard
import com.geeksville.mesh.ui.components.DeviceMetricsChart
import com.geeksville.mesh.ui.theme.AppTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

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

    private val model: NodeDetailsViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val nodeNum = arguments?.getInt("nodeNum")
        if (nodeNum != null)
            model.setSelectedNode(nodeNum)

        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                AppTheme {
                    NodeDetailsScreen(
                        model = model,
                        coroutineScope = lifecycleScope,
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

@SuppressLint("CoroutineCreationDuringComposition")
@Composable
fun NodeDetailsScreen(
    model: NodeDetailsViewModel = hiltViewModel(),
    coroutineScope: CoroutineScope,
    nodeNum: Int? = null,
    navigateBack: () -> Unit,
) {
    // TODO Need to let user know when we don't have data to display
    val data by model.dataEntries.collectAsStateWithLifecycle()
    /* We only need to get the nodes name once. */
    var nodeName: String? = ""
    coroutineScope.launch { nodeName = model.getNodeName(nodeNum ?: 0) }

    Scaffold(
        /*
         * NOTE: Perhaps we can use a Pager to navigate from graph to graph.
         *       The bottom bar could be used to enable other actions such as clear data.
         **/
        topBar = {
            TopAppBar(
                backgroundColor = colorResource(R.color.toolbarBackground),
                contentColor = colorResource(R.color.toolbarText),
                title = {
                    Text(
                        text = "${stringResource(R.string.node_details)}: $nodeName",
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
            DeviceMetricsChart(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(fraction = 0.33f),
                data.toList()
            )

            /* Device Metric Cards */
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                items(data.reversed()) { dataEntry -> DeviceMetricsCard(dataEntry) }
            }
        }
    }
}
