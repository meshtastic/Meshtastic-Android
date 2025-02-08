/*
 * Copyright (c) 2025 Meshtastic LLC
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.geeksville.mesh.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.Button
import androidx.compose.material.Card
import androidx.compose.material.Icon
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.Fragment
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geeksville.mesh.R
import com.geeksville.mesh.model.DebugViewModel
import com.geeksville.mesh.model.DebugViewModel.UiMeshLog
import com.geeksville.mesh.ui.components.BaseScaffold
import com.geeksville.mesh.ui.theme.AppTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class DebugFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                AppTheme {
                    DebugScreen { parentFragmentManager.popBackStack() }
                }
            }
        }
    }
}

private val REGEX_ANNOTATED_NODE_ID = Regex("\\(![0-9a-fA-F]{8}\\)$", RegexOption.MULTILINE)

@Composable
internal fun DebugScreen(
    viewModel: DebugViewModel = hiltViewModel(),
    navigateUp: () -> Unit
) {
    val listState = rememberLazyListState()
    val logs by viewModel.meshLog.collectAsStateWithLifecycle()

    val shouldAutoScroll by remember { derivedStateOf { listState.firstVisibleItemIndex < 3 } }
    if (shouldAutoScroll) {
        LaunchedEffect(logs) {
            if (!listState.isScrollInProgress) {
                listState.animateScrollToItem(0)
            }
        }
    }

    BaseScaffold(
        title = stringResource(id = R.string.debug_panel),
        navigateUp = navigateUp,
        actions = {
            Button(onClick = viewModel::deleteAllLogs) {
                Text(text = stringResource(R.string.clear))
            }
        }
    ) {
        SelectionContainer {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState,
            ) {
                items(logs, key = { it.uuid }) { log ->
                    DebugItem(
                        modifier = Modifier.animateItem(),
                        log = log,
                    )
                }
            }
        }
    }
}

@Composable
internal fun DebugItem(
    log: UiMeshLog,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(4.dp),
        elevation = 4.dp,
        shape = RoundedCornerShape(12.dp),
    ) {
        Surface {
            Column(
                modifier = Modifier.padding(8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = log.messageType,
                        modifier = Modifier.weight(1f),
                        style = TextStyle(fontWeight = FontWeight.Bold),
                    )
                    Icon(
                        imageVector = Icons.Outlined.CloudDownload,
                        contentDescription = stringResource(id = R.string.logs),
                        tint = Color.Gray.copy(alpha = 0.6f),
                        modifier = Modifier.padding(end = 8.dp),
                    )
                    Text(
                        text = log.formattedReceivedDate,
                        style = TextStyle(fontWeight = FontWeight.Bold),
                    )
                }

                val style = SpanStyle(
                    color = colorResource(id = R.color.colorAnnotation),
                    fontStyle = FontStyle.Italic,
                )
                val annotatedString = remember(log.uuid) {
                    buildAnnotatedString {
                        append(log.logMessage)
                        REGEX_ANNOTATED_NODE_ID.findAll(log.logMessage).toList().reversed()
                            .forEach {
                                addStyle(
                                    style = style,
                                    start = it.range.first,
                                    end = it.range.last + 1
                                )
                            }
                    }
                }

                Text(
                    text = annotatedString,
                    softWrap = false,
                    style = TextStyle(
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                )
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun DebugScreenPreview() {
    AppTheme {
        DebugItem(
            UiMeshLog(
                uuid = "",
                messageType = "NodeInfo",
                formattedReceivedDate = "9/27/20, 8:00:58 PM",
                logMessage = "from: 2885173132\n" +
                        "decoded {\n" +
                        "   position {\n" +
                        "       altitude: 60\n" +
                        "       battery_level: 81\n" +
                        "       latitude_i: 411111136\n" +
                        "       longitude_i: -711111805\n" +
                        "       time: 1600390966\n" +
                        "   }\n" +
                        "}\n" +
                        "hop_limit: 3\n" +
                        "id: 1737414295\n" +
                        "rx_snr: 9.5\n" +
                        "rx_time: 316400569\n" +
                        "to: -1409790708",
            )
        )
    }
}
