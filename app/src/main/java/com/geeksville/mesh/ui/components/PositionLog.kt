package com.geeksville.mesh.ui.components

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.ContentAlpha
import androidx.compose.material.Icon
import androidx.compose.material.LocalContentAlpha
import androidx.compose.material.LocalTextStyle
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Save
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geeksville.mesh.ConfigProtos.Config.DisplayConfig.DisplayUnits
import com.geeksville.mesh.MeshProtos
import com.geeksville.mesh.R
import com.geeksville.mesh.model.MetricsViewModel
import com.geeksville.mesh.ui.theme.AppTheme
import com.geeksville.mesh.util.metersIn
import com.geeksville.mesh.util.toString
import java.text.DateFormat
import kotlin.time.Duration.Companion.days

@Composable
private fun RowScope.PositionText(text: String, weight: Float) {
    Text(
        text = text,
        modifier = Modifier.weight(weight),
        textAlign = TextAlign.Center,
        overflow = TextOverflow.Ellipsis,
        maxLines = 1,
    )
}

private const val Weight10 = .10f
private const val Weight15 = .15f
private const val Weight20 = .20f
private const val Weight40 = .40f

@Composable
private fun HeaderItem(compactWidth: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        PositionText("Latitude", Weight20)
        PositionText("Longitude", Weight20)
        PositionText("Sats", Weight10)
        PositionText("Alt", Weight15)
        if (!compactWidth) {
            PositionText("Speed", Weight15)
            PositionText("Heading", Weight15)
        }
        PositionText("Timestamp", Weight40)
    }
}

private const val DegD = 1e-7
private const val HeadingDeg = 1e-5
private const val SecondsToMillis = 1000L

@Composable
private fun PositionItem(
    compactWidth: Boolean,
    position: MeshProtos.Position,
    dateFormat: DateFormat,
    system: DisplayUnits,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        PositionText("%.5f".format(position.latitudeI * DegD), Weight20)
        PositionText("%.5f".format(position.longitudeI * DegD), Weight20)
        PositionText(position.satsInView.toString(), Weight10)
        PositionText(position.altitude.metersIn(system).toString(system), Weight15)
        if (!compactWidth) {
            PositionText("${position.groundSpeed} Km/h", Weight15)
            PositionText("%.0f°".format(position.groundTrack * HeadingDeg), Weight15)
        }
        PositionText(formatPositionTime(position, dateFormat), Weight40)
    }
}

@Composable
private fun formatPositionTime(
    position: MeshProtos.Position,
    dateFormat: DateFormat
): String {
    val currentTime = System.currentTimeMillis()
    val sixMonthsAgo = currentTime - 180.days.inWholeMilliseconds
    val isOlderThanSixMonths = position.time * SecondsToMillis > sixMonthsAgo
    val timeText = if (isOlderThanSixMonths) {
        stringResource(id = R.string.unknown)
    } else {
        dateFormat.format(position.time * SecondsToMillis)
    }
    return timeText
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActionButtons(
    clearButtonEnabled: Boolean,
    onClear: () -> Unit,
    saveButtonEnabled: Boolean,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedButton(
            modifier = Modifier.weight(1f),
            onClick = onClear,
            enabled = clearButtonEnabled,
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colors.error,
            )
        ) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = stringResource(id = R.string.clear),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(id = R.string.clear),
            )
        }

        OutlinedButton(
            modifier = Modifier.weight(1f),
            onClick = onSave,
            enabled = saveButtonEnabled,
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.medium),
            )
        ) {
            Icon(
                imageVector = Icons.Default.Save,
                contentDescription = stringResource(id = R.string.save),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(id = R.string.save),
            )
        }
    }
}

@Composable
fun PositionLogScreen(
    viewModel: MetricsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    val exportPositionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (it.resultCode == Activity.RESULT_OK) {
            it.data?.data?.let { uri -> viewModel.savePositionCSV(uri) }
        }
    }

    var clearButtonEnabled by rememberSaveable(state.positionLogs) {
        mutableStateOf(state.positionLogs.isNotEmpty())
    }

    BoxWithConstraints {
        val compactWidth = maxWidth < 600.dp
        Column {
            val textStyle = if (compactWidth) {
                MaterialTheme.typography.caption
            } else {
                LocalTextStyle.current
            }
            CompositionLocalProvider(LocalTextStyle provides textStyle) {
                HeaderItem(compactWidth)
                PositionList(compactWidth, state.positionLogs, state.displayUnits)
            }

            ActionButtons(
                clearButtonEnabled = clearButtonEnabled,
                onClear = {
                    clearButtonEnabled = false
                    viewModel.clearPosition()
                },
                saveButtonEnabled = state.hasPositionLogs(),
                onSave = {
                    val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "application/*"
                        putExtra(Intent.EXTRA_TITLE, "position.csv")
                    }
                    exportPositionLauncher.launch(intent)
                },
            )
        }
    }
}

@Composable
private fun ColumnScope.PositionList(
    compactWidth: Boolean,
    positions: List<MeshProtos.Position>,
    displayUnits: DisplayUnits,
) {
    val dateFormat = remember {
        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM)
    }

    CompositionLocalProvider(LocalContentAlpha provides ContentAlpha.medium) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            items(positions) { position ->
                PositionItem(compactWidth, position, dateFormat, displayUnits)
            }
        }
    }
}

@Suppress("MagicNumber")
private val testPosition = MeshProtos.Position.newBuilder().apply {
    latitudeI = 297604270
    longitudeI = -953698040
    altitude = 1230
    satsInView = 7
    time = (System.currentTimeMillis() / 1000).toInt()
}.build()

@Preview(showBackground = true)
@Composable
private fun PositionItemPreview() {
    AppTheme {
        PositionItem(
            compactWidth = false,
            position = testPosition,
            dateFormat = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM),
            system = DisplayUnits.METRIC,
        )
    }
}

@PreviewScreenSizes
@Composable
private fun ActionButtonsPreview() {
    AppTheme {
        Column(Modifier.fillMaxSize(), Arrangement.Bottom) {
            ActionButtons(
                clearButtonEnabled = true,
                onClear = {},
                saveButtonEnabled = true,
                onSave = {},
            )
        }
    }
}
