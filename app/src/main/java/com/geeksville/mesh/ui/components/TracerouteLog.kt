package com.geeksville.mesh.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.Card
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.PersonOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geeksville.mesh.MeshProtos
import com.geeksville.mesh.R
import com.geeksville.mesh.model.MetricsViewModel
import com.geeksville.mesh.model.getTracerouteResponse
import com.geeksville.mesh.ui.theme.AppTheme
import java.text.DateFormat

@Composable
fun TracerouteLogScreen(
    viewModel: MetricsViewModel = hiltViewModel(),
    modifier: Modifier = Modifier,
) {
    val state by viewModel.tracerouteState.collectAsStateWithLifecycle()
    val dateFormat = remember {
        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM)
    }

    fun getUsername(nodeNum: Int): String =
        with(viewModel.getUser(nodeNum)) { "$longName ($shortName)" }

    var showDialog by remember { mutableStateOf<String?>(null) }

    if (showDialog != null) {
        val message = showDialog ?: return
        SimpleAlertDialog(
            title = R.string.traceroute,
            text = {
                SelectionContainer {
                    Text(text = message)
                }
            },
            onDismiss = { showDialog = null }
        )
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp),
    ) {
        items(state.requests, key = { it.uuid }) { log ->
            val result = remember(state.requests) {
                state.results.find { it.decoded.requestId == log.fromRadio.packet.id }
            }
            val route = remember(result) {
                result?.let { MeshProtos.RouteDiscovery.parseFrom(it.decoded.payload) }
            }

            val time = dateFormat.format(log.received_date)
            val (text, icon) = route.getTextAndIcon()

            TracerouteItem(
                icon = icon,
                text = "$time - $text",
                modifier = Modifier.clickable(enabled = result != null) {
                    if (result != null) {
                        showDialog = result.getTracerouteResponse(::getUsername)
                    }
                }
            )
        }
    }
}

@Composable
private fun TracerouteItem(
    icon: ImageVector,
    text: String,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(vertical = 2.dp),
        elevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = stringResource(id = R.string.traceroute)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.body1,
            )
        }
    }
}

@Composable
private fun MeshProtos.RouteDiscovery?.getTextAndIcon(): Pair<String, ImageVector> = when {
    this == null -> {
        stringResource(R.string.routing_error_no_response) to Icons.Default.PersonOff
    }

    routeList.isEmpty() -> {
        stringResource(R.string.traceroute_direct) to Icons.Default.Group
    }

    routeList.size == routeBackList.size -> {
        val hops = routeList.size
        pluralStringResource(R.plurals.traceroute_hops, hops, hops) to Icons.Default.Groups
    }

    else -> {
        val (towards, back) = routeList.size to routeBackList.size
        stringResource(R.string.traceroute_diff, towards, back) to Icons.Default.Groups
    }
}

@PreviewLightDark
@Composable
private fun TracerouteItemPreview() {
    val dateFormat = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM)
    AppTheme {
        TracerouteItem(
            icon = Icons.Default.Group,
            text = "${dateFormat.format(System.currentTimeMillis())} - Direct"
        )
    }
}
