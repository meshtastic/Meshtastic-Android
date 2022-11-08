package com.geeksville.mesh.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.geeksville.mesh.model.UIViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.viewModelFactory
import com.geeksville.mesh.ui.theme.AppTheme

@Composable
fun PreferenceScreen(viewModel: UIViewModel = viewModel()) {
    PreferenceItemList(viewModel)
}

//@Preview(showBackground = true)
//@Composable
//fun PreferencePreview() {
//    AppTheme {
//        PreferenceScreen(viewModel(factory = viewModelFactory { }))
//    }
//}
