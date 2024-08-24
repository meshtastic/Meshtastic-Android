package com.geeksville.mesh.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.geeksville.mesh.ui.components.DeviceMetricsScreen
import com.geeksville.mesh.ui.components.EnvironmentMetricsScreen
import com.geeksville.mesh.ui.theme.AppTheme
import dagger.hilt.android.AndroidEntryPoint
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
        /* We only need to get the nodes name once. */
        var nodeName: String? = ""
        lifecycleScope.launch { nodeName = model.getNodeName(nodeNum ?: 0) }

        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                AppTheme {
                    NodeDetailsScreen(
                        model = model,
                        nodeName = nodeName,
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
    model: NodeDetailsViewModel = hiltViewModel(),
    nodeName: String?,
    navigateBack: () -> Unit,
) {
    // TODO Need to let user know when we don't have data to display

    val deviceMetrics by model.deviceMetrics.collectAsStateWithLifecycle()
    val environmentMetrics by model.environmentMetrics.collectAsStateWithLifecycle()

    val pagerState = rememberPagerState(pageCount = { 2 })

    Scaffold(
        /*
         * NOTE: The bottom bar could be used to enable other actions such as clear or export data.
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

        // TODO need tabs that help the user know what tab they are located in
        // TODO it would be cool to animate swipe
        HorizontalPager(
            state = pagerState,
        ) { page ->
            // TODO Maybe the no data thing can be handled here also
            when (page) {
                0 -> DeviceMetricsScreen(innerPadding = innerPadding, telemetries = deviceMetrics)
                1 -> EnvironmentMetricsScreen(innerPadding = innerPadding, telemetries = environmentMetrics)
            }

        }
    }
}
