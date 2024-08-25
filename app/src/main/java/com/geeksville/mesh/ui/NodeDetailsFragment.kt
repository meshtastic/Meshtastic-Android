package com.geeksville.mesh.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
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

        val nodeName = model.getNodeName(nodeNum ?: 0)

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
    val deviceMetrics by model.deviceMetrics.collectAsStateWithLifecycle()
    val environmentMetrics by model.environmentMetrics.collectAsStateWithLifecycle()

    val pagerState = rememberPagerState(pageCount = { 2 })

    Scaffold(
        /*
         * NOTE: The bottom bar could be used to enable other actions such as clear or export data.
         */
        topBar = {
            TopAppBar(
                backgroundColor = colorResource(R.color.toolbarBackground),
                contentColor = colorResource(R.color.toolbarText),
                title = {
                    Text(
                        text = "${stringResource(R.string.node_details)}: $nodeName",
                    )
                    HorizontalTabs(pagerState)
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
        HorizontalPager(state = pagerState) { page ->
            when (page) {
                0 -> DeviceMetricsScreen(
                    innerPadding = innerPadding,
                    telemetries = deviceMetrics
                )
                1 -> EnvironmentMetricsScreen(
                    innerPadding = innerPadding,
                    telemetries = environmentMetrics
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HorizontalTabs(pagerState: PagerState) {

    Row(
        Modifier
            .wrapContentHeight()
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        repeat(pagerState.pageCount) { iteration ->
            val color = if (pagerState.currentPage == iteration)
                colorResource(R.color.toolbarText)
            else
                Color.LightGray

            val (imageVector, contentDescription) = if (iteration == 0)
                Pair(ImageVector.vectorResource(
                    R.drawable.baseline_charging_station_24),
                    stringResource(R.string.device_metrics)
                )
            else
                Pair(
                    ImageVector.vectorResource(R.drawable.baseline_thermostat_24),
                    stringResource(R.string.environment_metrics)
                )
            Icon(
                imageVector,
                contentDescription,
                tint = color
            )
        }
    }
}
