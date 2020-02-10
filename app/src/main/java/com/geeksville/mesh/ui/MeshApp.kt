package com.geeksville.mesh.ui

import androidx.annotation.DrawableRes
import androidx.compose.Composable
import androidx.compose.state
import androidx.ui.animation.Crossfade
import androidx.ui.core.Modifier
import androidx.ui.core.Text
import androidx.ui.foundation.VerticalScroller
import androidx.ui.foundation.shape.corner.RoundedCornerShape
import androidx.ui.graphics.Color
import androidx.ui.layout.*
import androidx.ui.material.*
import androidx.ui.material.surface.Surface
import androidx.ui.tooling.preview.Preview
import androidx.ui.unit.dp
import com.geeksville.mesh.R


@Composable
fun HomeContent() {
    Column {
        Text(text = "Meshtastic")


        Row {
            Container(LayoutSize(40.dp, 40.dp)) {
                VectorImage(id = if (UIState.isConnected.value) R.drawable.cloud_on else R.drawable.cloud_off)
            }

            Text(if (UIState.isConnected.value) "Connected" else "Not Connected")
        }

        UIState.nodes.value.values.forEach {
            NodeInfoCard(it)
        }

        UIState.messages.value.forEach {
            Text("Text: ${it.text}")
        }

        /*
        Button(text = "Start scan",
            onClick = {
                if (bluetoothAdapter != null) {
                    // Note: We don't want this service to die just because our activity goes away (because it is doing a software update)
                    // So we use the application context instead of the activity
                    SoftwareUpdateService.enqueueWork(
                        applicationContext,
                        SoftwareUpdateService.startUpdateIntent
                    )
                }
            })

        Button(text = "send packets",
            onClick = { sendTestPackets() }) */
    }
}

@Composable
fun HomeScreen(openDrawer: () -> Unit) {
    Column {
        TopAppBar(
            title = { Text(text = "Meshtastic") },
            navigationIcon = {
                VectorImageButton(R.drawable.ic_launcher_foreground) {
                    openDrawer()
                }
            }
        )
        VerticalScroller(modifier = LayoutFlexible(1f)) {
            HomeContent()
        }
    }
}

@Composable
fun MeshApp() {
    val (drawerState, onDrawerStateChange) = state { DrawerState.Closed }

    MaterialTheme {
        ModalDrawerLayout(
            drawerState = drawerState,
            onStateChange = onDrawerStateChange,
            gesturesEnabled = drawerState == DrawerState.Opened,
            drawerContent = {

                AppDrawer(
                    currentScreen = AppStatus.currentScreen,
                    closeDrawer = { onDrawerStateChange(DrawerState.Closed) }
                )

                /*
                // modifier = Spacing(8.dp)
                Column() {


                 */
            }, bodyContent = { AppContent { onDrawerStateChange(DrawerState.Opened) } })
    }
}

@Preview
@Composable
fun previewView() {
    // It seems modaldrawerlayout not yet supported in preview
    HomeContent()
}

@Composable
private fun AppContent(openDrawer: () -> Unit) {
    Crossfade(AppStatus.currentScreen) { screen ->
        Surface(color = (MaterialTheme.colors()).background) {
            when (screen) {
                is Screen.Home -> HomeScreen { openDrawer() }
                /* is Screen.Interests -> InterestsScreen { openDrawer() }
                is Screen.Article -> ArticleScreen(postId = screen.postId) */
            }
        }
    }
}

@Composable
private fun AppDrawer(
    currentScreen: Screen,
    closeDrawer: () -> Unit
) {
    Column(modifier = LayoutSize.Fill) {
        Spacer(LayoutHeight(24.dp))
        Row(modifier = LayoutPadding(16.dp)) {
            VectorImage(
                id = R.drawable.ic_launcher_foreground,
                tint = (MaterialTheme.colors()).primary
            )
            Spacer(LayoutWidth(8.dp))
            VectorImage(id = R.drawable.ic_launcher_foreground)
        }
        Divider(color = Color(0x14333333))
        DrawerButton(
            icon = R.drawable.ic_launcher_foreground,
            label = "Home",
            isSelected = currentScreen == Screen.Home
        ) {
            navigateTo(Screen.Home)
            closeDrawer()
        }

        /*
        DrawerButton(
            icon = R.drawable.ic_interests,
            label = "Interests",
            isSelected = currentScreen == Screen.Interests
        ) {
            navigateTo(Screen.Interests)
            closeDrawer()
        }
         */
    }
}

@Composable
private fun DrawerButton(
    modifier: Modifier = Modifier.None,
    @DrawableRes icon: Int,
    label: String,
    isSelected: Boolean,
    action: () -> Unit
) {
    val colors = MaterialTheme.colors()
    val textIconColor = if (isSelected) {
        colors.primary
    } else {
        colors.onSurface.copy(alpha = 0.6f)
    }
    val backgroundColor = if (isSelected) {
        colors.primary.copy(alpha = 0.12f)
    } else {
        colors.surface
    }

    Surface(
        modifier = modifier + LayoutPadding(
            left = 8.dp,
            top = 8.dp,
            right = 8.dp,
            bottom = 0.dp
        ),
        color = backgroundColor,
        shape = RoundedCornerShape(4.dp)
    ) {
        Button(onClick = action, style = TextButtonStyle()) {
            Row {
                VectorImage(
                    modifier = LayoutGravity.Center,
                    id = icon,
                    tint = textIconColor
                )
                Spacer(LayoutWidth(16.dp))
                Text(
                    text = label,
                    style = (MaterialTheme.typography()).body2.copy(
                        color = textIconColor
                    )
                )
            }
        }
    }
}
