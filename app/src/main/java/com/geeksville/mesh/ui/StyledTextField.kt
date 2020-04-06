package com.geeksville.mesh.ui

import androidx.compose.Composable
import androidx.compose.state
import androidx.ui.core.Modifier
import androidx.ui.foundation.TextField
import androidx.ui.foundation.shape.corner.RoundedCornerShape
import androidx.ui.graphics.Color
import androidx.ui.input.ImeAction
import androidx.ui.input.KeyboardType
import androidx.ui.input.VisualTransformation
import androidx.ui.layout.LayoutPadding
import androidx.ui.material.Emphasis
import androidx.ui.material.MaterialTheme
import androidx.ui.material.ProvideEmphasis
import androidx.ui.material.Surface
import androidx.ui.text.TextStyle
import androidx.ui.unit.dp


val HintEmphasis = object : Emphasis {
    override fun emphasize(color: Color) = color.copy(alpha = 0.05f)
}


/// A text field that visually conveys that it is editable - FIXME, once Compose has material
/// design text fields use that instead.
@Composable
fun StyledTextField(
    value: String,
    modifier: Modifier = Modifier.None,
    onValueChange: (String) -> Unit = {},
    textStyle: TextStyle = TextStyle.Default,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Unspecified,
    onFocus: () -> Unit = {},
    onBlur: () -> Unit = {},
    focusIdentifier: String? = null,
    onImeActionPerformed: (ImeAction) -> Unit = {},
    visualTransformation: VisualTransformation? = null,
    hintText: String = ""
) {
    val backgroundColor = palette.secondary.copy(alpha = 0.12f)
    Surface(
        modifier = LayoutPadding(8.dp),
        color = backgroundColor,
        shape = RoundedCornerShape(4.dp)
    ) {
        val showingHint = state { value.isEmpty() }
        val level = if (showingHint.value) HintEmphasis else MaterialTheme.emphasisLevels.medium

        ProvideEmphasis(level) {
            TextField(
                value.ifEmpty { if (showingHint.value) hintText else "" },
                modifier + LayoutPadding(4.dp),
                onValueChange,
                textStyle,
                keyboardType,
                imeAction,
                {
                    showingHint.value = false // Stop showing the hint now
                    onFocus()
                },
                {
                    // if the string is empty again, return to the hint text
                    showingHint.value = value.isEmpty()
                    onBlur()
                },
                focusIdentifier,
                onImeActionPerformed,
                visualTransformation
            )
        }
    }
}
