package com.geeksville.mesh.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.IconButton
import androidx.compose.material.Scaffold
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geeksville.mesh.R
import com.geeksville.mesh.database.entity.MeshLog
import com.geeksville.mesh.model.DebugViewModel
import com.geeksville.mesh.ui.theme.AppTheme
import dagger.hilt.android.AndroidEntryPoint
import java.text.DateFormat
import java.util.Locale

@AndroidEntryPoint
class DebugFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setBackgroundColor(ContextCompat.getColor(context, R.color.colorAdvancedBackground))
            setContent {
                val viewModel: DebugViewModel = hiltViewModel()

                AppTheme {
                    Scaffold(
                        topBar = {
                            TopAppBar(
                                title = { Text(stringResource(id = R.string.debug_panel)) },
                                navigationIcon = {
                                    IconButton(onClick = { parentFragmentManager.popBackStack() }) {
                                        Icon(
                                            Icons.AutoMirrored.Filled.ArrowBack,
                                            stringResource(id = R.string.navigate_back),
                                        )
                                    }
                                },
                                actions = {
                                    Button(onClick = viewModel::deleteAllLogs) {
                                        Text(text = stringResource(R.string.clear))
                                    }
                                }
                            )
                        },
                    ) { innerPadding ->
                        DebugScreen(
                            viewModel = viewModel,
                            contentPadding = innerPadding,
                        )
                    }
                }
            }
        }
    }
}

private val REGEX_ANNOTATED_NODE_ID = Regex("\\(![0-9a-fA-F]{8}\\)$", RegexOption.MULTILINE)

/**
 * Transform the input [MeshLog] by enhancing the raw message with annotations.
 */
private fun annotateMeshLog(meshLog: MeshLog): MeshLog {
    val annotated = when (meshLog.message_type) {
        "Packet" -> meshLog.meshPacket?.let { packet ->
            annotateRawMessage(meshLog.raw_message, packet.from, packet.to)
        }

        "NodeInfo" -> meshLog.nodeInfo?.let { nodeInfo ->
            annotateRawMessage(meshLog.raw_message, nodeInfo.num)
        }

        "MyNodeInfo" -> meshLog.myNodeInfo?.let { nodeInfo ->
            annotateRawMessage(meshLog.raw_message, nodeInfo.myNodeNum)
        }

        else -> null
    }
    return if (annotated == null) {
        meshLog
    } else {
        meshLog.copy(raw_message = annotated)
    }
}

/**
 * Annotate the raw message string with the node IDs provided, in hex, if they are present.
 */
private fun annotateRawMessage(rawMessage: String, vararg nodeIds: Int): String {
    val msg = StringBuilder(rawMessage)
    var mutated = false
    nodeIds.forEach { nodeId ->
        mutated = mutated or msg.annotateNodeId(nodeId)
    }
    return if (mutated) {
        return msg.toString()
    } else {
        rawMessage
    }
}

/**
 * Look for a single node ID integer in the string and annotate it with the hex equivalent
 * if found.
 */
private fun StringBuilder.annotateNodeId(nodeId: Int): Boolean {
    val nodeIdStr = nodeId.toUInt().toString()
    indexOf(nodeIdStr).takeIf { it >= 0 }?.let { idx ->
        insert(idx + nodeIdStr.length, " (${nodeId.asNodeId()})")
        return true
    }
    return false
}

private fun Int.asNodeId(): String {
    return "!%08x".format(Locale.getDefault(), this)
}

@Composable
internal fun DebugScreen(
    viewModel: DebugViewModel = hiltViewModel(),
    contentPadding: PaddingValues,
) {
    val listState = rememberLazyListState()
    val logs by viewModel.meshLog.collectAsStateWithLifecycle()

    val shouldAutoScroll by remember { derivedStateOf { listState.firstVisibleItemIndex < 3 } }
    if (shouldAutoScroll) {
        LaunchedEffect(logs) {
            if (!listState.isScrollInProgress) {
                listState.scrollToItem(0)
            }
        }
    }

    SelectionContainer {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = contentPadding,
        ) {
            items(logs, key = { it.uuid }) { log -> DebugItem(annotateMeshLog(log)) }
        }
    }
}

@Composable
internal fun DebugItem(log: MeshLog) {
    val timeFormat = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM)

    Card(
        modifier = Modifier
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
                ) {
                    Text(
                        text = log.message_type,
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
                        text = timeFormat.format(log.received_date),
                        style = TextStyle(fontWeight = FontWeight.Bold),
                    )
                }

                val style = SpanStyle(
                    color = colorResource(id = R.color.colorAnnotation),
                    fontStyle = FontStyle.Italic,
                )
                val annotatedString = buildAnnotatedString {
                    append(log.raw_message)
                    REGEX_ANNOTATED_NODE_ID.findAll(log.raw_message).toList().reversed().forEach {
                        addStyle(style = style, start = it.range.first, end = it.range.last + 1)
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
            MeshLog(
                uuid = "",
                message_type = "NodeInfo",
                received_date = 1601251258000L,
                raw_message = "from: 2885173132\n" +
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
