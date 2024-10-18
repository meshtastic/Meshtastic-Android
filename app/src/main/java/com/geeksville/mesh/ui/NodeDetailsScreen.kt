package com.geeksville.mesh.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChargingStation
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyOff
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Work
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import com.geeksville.mesh.R
import com.geeksville.mesh.database.entity.NodeEntity
import com.geeksville.mesh.model.MetricsState
import com.geeksville.mesh.ui.components.PreferenceCategory
import com.geeksville.mesh.ui.preview.NodeEntityPreviewParameterProvider
import com.geeksville.mesh.ui.theme.AppTheme
import com.geeksville.mesh.util.formatAgo
import java.util.concurrent.TimeUnit

@Composable
fun NodeDetailsScreen(
    node: NodeEntity?,
    metricsState: MetricsState,
    modifier: Modifier = Modifier,
    onNavigate: (String) -> Unit,
    setSelectedNode: (Int) -> Unit,
) {
    if (node != null) {
        LaunchedEffect(node.num) {
            setSelectedNode(node.num)
        }

        NodeDetailsItemList(
            node = node,
            metricsState = metricsState,
            onNavigate = onNavigate,
            modifier = modifier,
        )
    } else {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator()
        }
    }
}

@Suppress("LongMethod")
@Composable
fun NodeDetailsItemList(
    node: NodeEntity,
    metricsState: MetricsState,
    modifier: Modifier = Modifier,
    onNavigate: (String) -> Unit = {},
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp),
    ) {
        item {
            PreferenceCategory("Details") {
                if (node.mismatchKey) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.KeyOff,
                            contentDescription = stringResource(id = R.string.encryption_error),
                            tint = Color.Red,
                        )
                        Column(modifier = Modifier.padding(start = 8.dp)) {
                            Text(
                                text = stringResource(id = R.string.encryption_error),
                                style = MaterialTheme.typography.h6.copy(color = Color.Red)
                            )
                            Text(
                                text = stringResource(id = R.string.encryption_error_text),
                                style = MaterialTheme.typography.body2,
                                color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }
                }

                NodeDetailRow(
                    label = "Node Number",
                    icon = Icons.Default.Numbers,
                    value = node.num.toUInt().toString()
                )

                NodeDetailRow(
                    label = "User Id",
                    icon = Icons.Default.Person,
                    value = node.user.id
                )

                NodeDetailRow(
                    label = "Role",
                    icon = Icons.Default.Work,
                    value = node.user.role.name
                )

                if (node.deviceMetrics.uptimeSeconds > 0) {
                    NodeDetailRow(
                        label = "Uptime",
                        icon = Icons.Default.CheckCircle,
                        value = formatUptime(node.deviceMetrics.uptimeSeconds)
                    )
                }

                NodeDetailRow(
                    label = "Last heard",
                    icon = Icons.Default.History,
                    value = formatAgo(node.lastHeard)
                )
            }
        }

        item {
            NavCard(
                title = "Device Metrics Logs",
                icon = Icons.Default.ChargingStation,
                enabled = metricsState.hasDeviceMetrics()
            ) {
                onNavigate("DeviceMetrics")
            }

            NavCard(
                title = "Environment Metrics Logs",
                icon = Icons.Default.Thermostat,
                enabled = metricsState.hasEnvironmentMetrics()
            ) {
                onNavigate("EnvironmentMetrics")
            }

            NavCard(
                title = "Remote Administration",
                icon = Icons.Default.Settings,
                enabled = !node.user.isLicensed // TODO check for isManaged
            ) {
                onNavigate("RadioConfig")
            }
        }
    }
}

@Composable
fun NodeDetailRow(label: String, icon: ImageVector, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(label)
        Spacer(modifier = Modifier.weight(1f))
        Text(value)
    }
}

private fun formatUptime(seconds: Int): String = formatUptime(seconds.toLong())

private fun formatUptime(seconds: Long): String {
    val days = TimeUnit.SECONDS.toDays(seconds)
    val hours = TimeUnit.SECONDS.toHours(seconds) % TimeUnit.DAYS.toHours(1)
    val minutes = TimeUnit.SECONDS.toMinutes(seconds) % TimeUnit.HOURS.toMinutes(1)
    val secs = seconds % TimeUnit.MINUTES.toSeconds(1)

    return listOfNotNull(
        "${days}d".takeIf { days > 0 },
        "${hours}h".takeIf { hours > 0 },
        "${minutes}m".takeIf { minutes > 0 },
        "${secs}s".takeIf { secs > 0 },
    ).joinToString(" ")
}

@Preview(showBackground = true)
@Composable
private fun NodeDetailsPreview(
    @PreviewParameter(NodeEntityPreviewParameterProvider::class)
    node: NodeEntity
) {
    AppTheme {
        NodeDetailsItemList(node, MetricsState.Empty)
    }
}
