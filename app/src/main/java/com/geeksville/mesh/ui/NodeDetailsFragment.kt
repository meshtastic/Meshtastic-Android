package com.geeksville.mesh.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.unit.dp
import androidx.core.os.bundleOf
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.activityViewModels
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geeksville.mesh.R
import com.geeksville.mesh.android.Logging
import com.geeksville.mesh.model.UIViewModel
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
    nodeNum: Int? = null,
    navigateBack: () -> Unit,
) {
    val ourNodeInfo by model.ourNodeInfo.collectAsStateWithLifecycle()
    val detailsNodeInfo by model.getNodeByNum(nodeNum ?: ourNodeInfo?.num ?: 0)
        .collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Node Details: ${detailsNodeInfo?.user?.shortName}")
                },
                navigationIcon = {
                    IconButton(onClick = navigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Localized description"
                        )
                    }
                }
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                modifier = Modifier.padding(8.dp),
                text = detailsNodeInfo?.user?.longName.orEmpty(),
            )
        }
    }
}
