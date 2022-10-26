package com.geeksville.mesh.ui.view

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.geeksville.mesh.model.UIViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.viewModelFactory
import com.geeksville.mesh.ui.theme.AppTheme

@Composable
fun PreferenceView(viewModel: UIViewModel = viewModel()) {
    PreferenceItemList(viewModel = viewModel)
}

//@Preview(showBackground = true)
//@Composable
//fun PreferencePreview() {
//    AppTheme {
//        PreferenceView(viewModel = viewModel(factory = viewModelFactory { }))
//    }
//}
