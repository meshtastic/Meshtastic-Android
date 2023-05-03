package com.geeksville.mesh.ui.map.components

import android.view.View
import androidx.compose.foundation.layout.*
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.widget.ConstraintLayout
import com.geeksville.mesh.R

@Composable
fun CacheLayout() {

}
//    ConstraintLayout(
//        modifier = Modifier
//            .fillMaxWidth()
//            .wrapContentHeight()
//            .visibility(visibility = if (visible) View.VISIBLE else View.GONE)
//    ) {
//        val (title, toggleButton, cacheEstimate, executeJob, cancelDownload) = createRefs()
//        Text(
//            text = stringResource(id = R.string.map_select_download_region),
//            fontSize = TextUnit.Sp(18),
//            fontWeight = FontWeight.Bold,
//            textAlign = TextAlign.Center,
//            color = Color.Gray,
//            modifier = Modifier
//                .fillMaxWidth()
//                .constrainAs(title) {
//                    top.linkTo(parent.top)
//                    start.linkTo(parent.start)
//                    end.linkTo(parent.end)
//                    bottom.linkTo(toggleButton.top)
//                }
//        )
//
//        val toggleButtonGroup = remember { mutableStateOf(1) }
//
//        ToggleButton(
//            groupValue = toggleButtonGroup.value,
//            onValueChange = { selectedValue -> toggleButtonGroup.value = selectedValue },
//            modifier = Modifier
//                .fillMaxWidth()
//                .constrainAs(toggleButton) {
//                    top.linkTo(title.bottom)
//                    start.linkTo(parent.start)
//                    end.linkTo(parent.end)
//                    bottom.linkTo(cacheEstimate.top)
//                }
//        ) {
//            ToggleButtonOption(
//                id = 1,
//                text = stringResource(id = R.string.map_5_miles)
//            )
//            ToggleButtonOption(
//                id = 2,
//                text = stringResource(id = R.string.map_10_miles)
//            )
//            ToggleButtonOption(
//                id = 3,
//                text = stringResource(id = R.string.map_15_miles)
//            )
//        }
//
//        Text(
//            text = stringResource(id = R.string.map_tile_download_estimate),
//            fontSize = TextUnit.Sp(14),
//            color = Color.Gray,
//            modifier = Modifier
//                .constrainAs(cacheEstimate) {
//                    top.linkTo(toggleButton.bottom)
//                    start.linkTo(parent.start)
//                    end.linkTo(parent.end)
//                    bottom.linkTo(executeJob.top)
//                }
//        )
//
//        Button(
//            onClick = { /* Perform action */ },
//            modifier = Modifier
//                .fillMaxWidth()
//                .height(72.dp)
//                .constrainAs(executeJob) {
//                    top.linkTo(cacheEstimate.bottom)
//                    start.linkTo(parent.start)
//                    bottom.linkTo(parent.bottom)
//                }
//        ) {
//            Text(
//                text = stringResource(id = R.string.map_start_download),
//                fontWeight = FontWeight.Bold
//            )
//        }
//
//        Button(
//            onClick = { /* Perform action */ },
//            modifier = Modifier
//                .fillMaxWidth()
//                .height(72.dp)
//                .constrainAs(cancelDownload) {
//                    top.linkTo(cacheEstimate.bottom)
//                    end.linkTo(parent.end)
//                    bottom.linkTo(parent.bottom)
//                }
//        ) {
//            Text(text = stringResource(id = R.string.cancel))
//        }
//    }
//}}
//
//@Composable
//fun ToggleButton(
//    groupValue: Int,
//    onValueChange: (Int) -> Unit,
//    modifier: Modifier = Modifier,
//    content: @Composable () -> Unit
//) {
//    androidx.compose.material.ButtonGroup(
//        selectedButton = groupValue - 1,
//        modifier = modifier,
//        options = {
//            content()
//        },
//        onSelectedChange = { index -> onValueChange(index + 1) }
//    )
//}
//
//@Composable
//fun ToggleButtonOption(id: Int, text: String) {
//    androidx.compose.material.Button(
//        onClick = {},
//        colors = ButtonDefaults.buttonColors(
//            backgroundColor = if (id == 1) Color.Gray else Color.White,
//            contentColor = if