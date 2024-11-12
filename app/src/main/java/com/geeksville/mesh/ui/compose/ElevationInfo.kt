package com.geeksville.mesh.ui.compose

import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import com.geeksville.mesh.ConfigProtos.Config.DisplayConfig.DisplayUnits
import com.geeksville.mesh.util.metersIn
import com.geeksville.mesh.util.toString

@Composable
fun ElevationInfo(
    modifier: Modifier = Modifier,
    altitude: Int,
    system: DisplayUnits,
    suffix: String
) {
    val annotatedString = buildAnnotatedString {
        append(altitude.metersIn(system).toString(system))
        MaterialTheme.typography.overline.toSpanStyle().let { style ->
            withStyle(style) {
                append(" $suffix")
            }
        }
    }

    Text(
        modifier = modifier,
        fontSize = MaterialTheme.typography.button.fontSize,
        text = annotatedString,
    )
}

@Composable
@Preview
fun ElevationInfoPreview() {
    MaterialTheme {
        ElevationInfo(
            altitude = 100,
            system = DisplayUnits.METRIC,
            suffix = "ASL"
        )
    }
}