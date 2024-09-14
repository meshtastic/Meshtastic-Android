package com.geeksville.mesh.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.Tab
import androidx.compose.material.TabRow
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.core.os.bundleOf
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.viewModels
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geeksville.mesh.R
import com.geeksville.mesh.android.Logging
import com.geeksville.mesh.model.MetricsPage
import com.geeksville.mesh.model.MetricsState
import com.geeksville.mesh.model.MetricsViewModel
import com.geeksville.mesh.ui.components.DeviceMetricsScreen
import com.geeksville.mesh.ui.components.EnvironmentMetricsScreen
import com.geeksville.mesh.ui.theme.AppTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

internal fun FragmentManager.navigateToMetrics(nodeNum: Int? = null) {
    val metricsFragment = MetricsFragment().apply {
        arguments = bundleOf("nodeNum" to nodeNum)
    }
    beginTransaction()
        .replace(R.id.mainActivityLayout, metricsFragment)
        .addToBackStack(null)
        .commit()
}

@AndroidEntryPoint
class MetricsFragment : ScreenFragment("Metrics"), Logging {

    private val model: MetricsViewModel by viewModels()

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
                    MetricsScreen(
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

@Composable
fun MetricsScreen(
    model: MetricsViewModel = hiltViewModel(),
    nodeName: String?,
    navigateBack: () -> Unit,
) {
    val state by model.state.collectAsStateWithLifecycle()
    val pagerState = rememberPagerState(pageCount = { state.pages.size })

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
                        text = "${stringResource(R.string.metrics)}: $nodeName",
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
        MetricsPagerScreen(
            state = state,
            pagerState = pagerState,
            modifier = Modifier.padding(top = innerPadding.calculateTopPadding())
        )
    }
}

@Composable
fun MetricsPagerScreen(
    state: MetricsState,
    pagerState: PagerState,
    modifier: Modifier = Modifier,
) = with(state) {
    Column(modifier) {
        val coroutineScope = rememberCoroutineScope()

        TabRow(
            selectedTabIndex = pagerState.currentPage,
        ) {
            pages.forEachIndexed { index, page ->
                val title = stringResource(id = page.titleResId)
                Tab(
                    selected = pagerState.currentPage == index,
                    onClick = { coroutineScope.launch { pagerState.animateScrollToPage(index) } },
                    text = { Text(text = title) },
                    icon = {
                        Icon(
                            painter = painterResource(id = page.drawableResId),
                            contentDescription = title
                        )
                    },
                    unselectedContentColor = MaterialTheme.colors.secondaryVariant
                )
            }
        }

        HorizontalPager(
            state = pagerState,
            verticalAlignment = Alignment.Top,
        ) { index ->
            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            } else {
                when (pages[index]) {
                    MetricsPage.DEVICE -> DeviceMetricsScreen(deviceMetrics)
                    MetricsPage.ENVIRONMENT -> EnvironmentMetricsScreen(environmentMetrics)
                }
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun MetricsPreview() {
    AppTheme {
        val state = MetricsState.Empty
        MetricsPagerScreen(
            state = state,
            pagerState = rememberPagerState(pageCount = { state.pages.size }),
        )
    }
}
