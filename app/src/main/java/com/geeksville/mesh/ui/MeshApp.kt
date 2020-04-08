package com.geeksville.mesh.ui

import com.geeksville.android.Logging


object UILog : Logging
/*
val palette = lightColorPalette() // darkColorPalette()


@Composable
fun MeshApp() {
    val (drawerState, onDrawerStateChange) = state { DrawerState.Closed }

    MaterialTheme(colors = palette) {
        ModalDrawerLayout(
            drawerState = drawerState,
            onStateChange = onDrawerStateChange,
            gesturesEnabled = drawerState == DrawerState.Opened,
            drawerContent = {

                AppDrawer(
                    currentScreen = AppStatus.currentScreen,
                    closeDrawer = { onDrawerStateChange(DrawerState.Closed) }
                )

            }, bodyContent = { AppContent { onDrawerStateChange(DrawerState.Opened) } })
    }
}

@Preview
@Composable
fun previewView() {
    // It seems modaldrawerlayout not yet supported in preview
    MaterialTheme(colors = palette) {
        UsersContent()
    }
}


@Composable
private fun AppContent(openDrawer: () -> Unit) {
    // crossfade breaks onCommit behavior because it keeps old views around
    //Crossfade(AppStatus.currentScreen) { screen ->
    //Surface(color = (MaterialTheme.colors()).background) {

    Scaffold(topAppBar = {
        TopAppBar(
            title = { Text(text = "Meshtastic") },
            navigationIcon = {
                //Container(LayoutSize(40.dp, 40.dp)) {
                VectorImageButton(R.drawable.ic_launcher_new_foreground) {
                    openDrawer()
                }
                //}
            }
        )
    }) {
        when (AppStatus.currentScreen) {
            Screen.messages -> MessagesContent()
            Screen.settings -> SettingsContent()
            Screen.users -> UsersContent()
            Screen.channel -> ChannelContent(UIState.getChannel())
            Screen.map -> MapContent()
            else -> TODO()
        }
    }
    //}
}
*/