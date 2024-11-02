package com.geeksville.mesh.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
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
import com.geeksville.mesh.model.fullRouteDiscovery
import com.geeksville.mesh.model.getTracerouteResponse
import com.geeksville.mesh.ui.theme.AppTheme
import java.text.DateFormat

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TracerouteLogScreen(
    viewModel: MetricsViewModel = hiltViewModel(),
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
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
        items(state.tracerouteRequests, key = { it.uuid }) { log ->
            val result = remember(state.tracerouteRequests) {
                state.tracerouteResults.find { it.decoded.requestId == log.fromRadio.packet.id }
            }
            val route = remember(result) { result?.fullRouteDiscovery }

            val time = dateFormat.format(log.received_date)
            val (text, icon) = route.getTextAndIcon()
            var expanded by remember { mutableStateOf(false) }

            Box {
                TracerouteItem(
                    icon = icon,
                    text = "$time - $text",
                    modifier = Modifier.combinedClickable(
                        onLongClick = { expanded = true },
                    ) {
                        if (result != null) {
                            showDialog = result.getTracerouteResponse(::getUsername)
                        }
                    }
                )
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                ) {
                    DeleteItem {
                        viewModel.deleteLog(log.uuid)
                        expanded = false
                    }
                }
            }
        }
    }
}

@Composable
private fun DeleteItem(onClick: () -> Unit) {
    DropdownMenuItem(onClick = onClick) {
        Icon(
            imageVector = Icons.Default.Delete,
            contentDescription = stringResource(id = R.string.delete),
            tint = MaterialTheme.colors.error,
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = stringResource(id = R.string.delete),
            color = MaterialTheme.colors.error,
        )
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

    routeCount <= 2 -> {
        stringResource(R.string.traceroute_direct) to Icons.Default.Group
    }

    routeCount == routeBackCount -> {
        val hops = routeCount - 2
        pluralStringResource(R.plurals.traceroute_hops, hops, hops) to Icons.Default.Groups
    }

    else -> {
        val (towards, back) = maxOf(0, routeCount - 2) to maxOf(0, routeBackCount - 2)
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
