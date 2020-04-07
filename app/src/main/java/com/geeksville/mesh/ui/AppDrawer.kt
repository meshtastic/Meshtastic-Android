package com.geeksville.mesh.ui

/*
@Composable
fun AppDrawer(
    currentScreen: ScreenInfo,
    closeDrawer: () -> Unit
) {
    Column(modifier = LayoutSize.Fill) {
        Spacer(LayoutHeight(24.dp))
        Row(modifier = LayoutPadding(16.dp)) {
            VectorImage(
                id = R.drawable.ic_launcher_new_foreground,
                tint = MaterialTheme.colors.primary
            )
            Spacer(LayoutWidth(8.dp))
            // VectorImage(id = R.drawable.ic_launcher_new_foreground)
        }
        Divider(color = Color(0x14333333))

        @Composable
        fun ScreenButton(screen: ScreenInfo) {
            DrawerButton(
                icon = screen.icon,
                label = screen.label,
                isSelected = currentScreen == screen
            ) {
                navigateTo(screen)
                closeDrawer()
            }
        }

        ScreenButton(Screen.messages)
        ScreenButton(Screen.users)
        ScreenButton(Screen.map) // turn off for now
        ScreenButton(Screen.channel)
        ScreenButton(Screen.settings)
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
    val colors = MaterialTheme.colors
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
            start = 8.dp,
            top = 8.dp,
            end = 8.dp,
            bottom = 0.dp
        ),
        color = backgroundColor,
        shape = RoundedCornerShape(4.dp)
    ) {
        TextButton(onClick = action) {
            Row {
                VectorImage(
                    modifier = LayoutGravity.Center,
                    id = icon,
                    tint = textIconColor
                )
                Spacer(LayoutWidth(16.dp))
                Text(
                    text = label,
                    style = (MaterialTheme.typography).body2.copy(
                        color = textIconColor
                    ),
                    modifier = LayoutWidth.Fill
                )
            }
        }
    }
}


@Preview
@Composable
fun previewDrawer() {
    AppDrawer(
        currentScreen = AppStatus.currentScreen,
        closeDrawer = { }
    )
} */