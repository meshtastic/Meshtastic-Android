package com.geeksville.mesh.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.Card
import androidx.compose.material.ContentAlpha
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.twotone.KeyboardArrowRight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.viewModels
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.geeksville.mesh.R
import com.geeksville.mesh.android.Logging
import com.geeksville.mesh.model.ApplicationConfigViewModel
import com.geeksville.mesh.ui.components.PreferenceCategory
import com.geeksville.mesh.ui.components.config.application.NodeListConfigItemList
import com.google.accompanist.themeadapter.appcompat.AppCompatTheme
import dagger.hilt.android.AndroidEntryPoint

internal fun FragmentManager.navigateToApplicationConfig() {
    val applicationConfigFragment = ApplicationSettingsFragment().apply {}
    beginTransaction()
        .replace(R.id.mainActivityLayout, applicationConfigFragment)
        .addToBackStack(null)
        .commit()
}

@AndroidEntryPoint
class ApplicationSettingsFragment : ScreenFragment("Application Configuration"), Logging {

    private val model: ApplicationConfigViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setBackgroundColor(ContextCompat.getColor(context, R.color.colorAdvancedBackground))
            setContent {

                AppCompatTheme {
                    val navController: NavHostController = rememberNavController()
                    Scaffold(
                        topBar = {
                            MeshAppBar(
                                currentScreen = stringResource(R.string.application_settings),
                                canNavigateBack = true,
                                navigateUp = {
                                    if (navController.previousBackStackEntry != null) {
                                        navController.navigateUp()
                                    } else {
                                        parentFragmentManager.popBackStack()
                                    }
                                },
                            )
                        }
                    ) { innerPadding ->
                        ApplicationConfigNavHost(
                            viewModel = model,
                            navController = navController,
                            modifier = Modifier.padding(innerPadding),
                        )
                    }
                }
            }
        }
    }
}

enum class ApplicationConfigRoute(val title: String, val configType: Int = 0) {
    NODE_LIST("Node packets"),
    ;
}

@Composable
private fun MeshAppBar(
    currentScreen: String,
    canNavigateBack: Boolean,
    navigateUp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TopAppBar(
        title = { Text(currentScreen) },
        modifier = modifier,
        navigationIcon = {
            if (canNavigateBack) {
                IconButton(onClick = navigateUp) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        null,
                    )
                }
            }
        }
    )
}

@Composable
fun ApplicationConfigNavHost(
    viewModel: ApplicationConfigViewModel = hiltViewModel(),
    navController: NavHostController = rememberNavController(),
    modifier: Modifier,
) {

    val appConfigState by viewModel.applicationConfigState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    NavHost(
        navController = navController,
        startDestination = "home",
        modifier = modifier,
    ) {
        composable("home") {
            ApplicationSettingsScreen(
                enabled = true,
                onRouteClick = { route ->
                    when (route) {
                        ApplicationConfigRoute.NODE_LIST -> {
                            navController.navigate(ApplicationConfigRoute.NODE_LIST.name)
                        }
                    }
                },
            )
        }
        composable(ApplicationConfigRoute.NODE_LIST.name) {
            NodeListConfigItemList(
                nodeConfig = appConfigState.nodeListConfig,
                onSaveClicked = { nodeListConfig ->
                    viewModel.saveNodeListConfig(nodeListConfig)
                    Toast.makeText(context, "Settings saved successfully", Toast.LENGTH_SHORT).show()
                },
            )
        }
    }
}

@Composable
private fun NavCard(
    title: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val color = if (enabled) MaterialTheme.colors.onSurface
    else MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.disabled)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clickable(enabled = enabled) { onClick() },
        elevation = 4.dp
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 12.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.body1,
                color = color,
                modifier = Modifier.weight(1f)
            )
            Icon(
                Icons.AutoMirrored.TwoTone.KeyboardArrowRight, "trailingIcon",
                modifier = Modifier.wrapContentSize(),
                tint = color,
            )
        }
    }
}

@Composable
private fun ApplicationSettingsScreen(
    enabled: Boolean = true,
    onRouteClick: (Any) -> Unit = {},
) {
    LazyColumn(
        modifier = Modifier.padding(horizontal = 16.dp)
    ) {
        item { PreferenceCategory(stringResource(R.string.node_list)) }
        items(ApplicationConfigRoute.entries) { NavCard(it.title, enabled = enabled) { onRouteClick(it) } }

    }
}

@Preview(showBackground = true)
@Composable
private fun ApplicationSettingsScreenPreview() {
    ApplicationSettingsScreen()
}