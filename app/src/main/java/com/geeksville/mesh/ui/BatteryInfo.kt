package com.geeksville.mesh.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import com.geeksville.mesh.R
import com.geeksville.mesh.ui.theme.AppTheme

@Composable
fun BatteryInfo(batteryLevel: Int?, voltage: Float?) {
    val infoString = "%d%% %.2fV".format(batteryLevel, voltage)
    val (image, level) = when (batteryLevel) {
        in 0 .. 4 -> R.drawable.ic_battery_alert to " $infoString"
        in 5 .. 14 -> R.drawable.ic_battery_outline to infoString
        in 15..34 -> R.drawable.ic_battery_low to infoString
        in 35..79 -> R.drawable.ic_battery_medium to infoString
        in 80..100 -> R.drawable.ic_battery_high to infoString
        101 -> R.drawable.ic_power_plug_24 to "%.1fV".format(voltage)
        else -> R.drawable.ic_battery_unknown to (voltage?.let { "%.2fV".format(it) } ?: "")
    }

    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            modifier = Modifier.height(18.dp),
            imageVector = ImageVector.vectorResource(id = image),
            contentDescription = null,
            tint = MaterialTheme.colors.onSurface,
        )
        Text(
            text = level,
            color = MaterialTheme.colors.onSurface,
            fontSize = MaterialTheme.typography.button.fontSize
        )
    }
}

@Composable
@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
fun BatteryInfoPreview(
    @PreviewParameter(BatteryInfoPreviewParameterProvider::class)
    batteryInfo: Pair<Int?, Float?>
) {
    AppTheme {
        BatteryInfo(batteryInfo.first, batteryInfo.second)
    }
}

@Composable
@Preview
fun BatteryInfoPreviewSimple() {
    AppTheme {
        BatteryInfo(85, 3.7F)
    }
}

class BatteryInfoPreviewParameterProvider : PreviewParameterProvider<Pair<Int?, Float?>> {
    override val values: Sequence<Pair<Int?, Float?>>
        get() = sequenceOf(
            85 to 3.7F,
            2 to 3.7F,
            12 to 3.7F,
            28 to 3.7F,
            50 to 3.7F,
            101 to 4.9F,
            null to 4.5F,
            null to null
        )
}