package com.geeksville.mesh.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Divider
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.geeksville.mesh.R
import com.geeksville.mesh.model.NodeSortOption
import com.geeksville.mesh.ui.theme.AppTheme

@Composable
fun NodeFilterTextField(
    modifier: Modifier = Modifier,
    filterText: String,
    onTextChange: (String) -> Unit,
    currentSortOption: NodeSortOption,
    onSortSelect: (NodeSortOption) -> Unit,
    includeUnknown: Boolean,
    onToggleIncludeUnknown: () -> Unit,
    showDetails: Boolean,
    onToggleShowDetails: () -> Unit,
) {
    Row(
        modifier = modifier.background(MaterialTheme.colors.background),
    ) {
        NodeFilterTextField(
            filterText = filterText,
            onTextChange = onTextChange,
            modifier = Modifier.weight(1f)
        )

        NodeSortButton(
            currentSortOption = currentSortOption,
            onSortSelect = onSortSelect,
            includeUnknown = includeUnknown,
            onToggleIncludeUnknown = onToggleIncludeUnknown,
            showDetails = showDetails,
            onToggleShowDetails = onToggleShowDetails,
        )
    }
}

@Composable
private fun NodeFilterTextField(
    filterText: String,
    onTextChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    var isFocused by remember { mutableStateOf(false) }

    OutlinedTextField(
        modifier = modifier
            .heightIn(max = 48.dp)
            .onFocusEvent { isFocused = it.isFocused },
        value = filterText,
        placeholder = {
            Text(
                text = stringResource(id = R.string.node_filter_placeholder),
                style = MaterialTheme.typography.body1,
                color = MaterialTheme.colors.onBackground.copy(alpha = 0.35F)
            )
        },
        leadingIcon = {
            Icon(
                Icons.Default.Search,
                contentDescription = stringResource(id = R.string.node_filter_placeholder),
            )
        },
        onValueChange = onTextChange,
        trailingIcon = {
            if (filterText.isNotEmpty() || isFocused) {
                Icon(
                    Icons.Default.Clear,
                    contentDescription = stringResource(id = R.string.desc_node_filter_clear),
                    modifier = Modifier.clickable {
                        onTextChange("")
                        focusManager.clearFocus()
                    }
                )
            }
        },
        textStyle = TextStyle(
            color = MaterialTheme.colors.onBackground
        ),
        maxLines = 1,
        keyboardOptions = KeyboardOptions(
            imeAction = ImeAction.Done
        ),
        keyboardActions = KeyboardActions(
            onDone = { focusManager.clearFocus() }
        )
    )
}

@Suppress("LongMethod")
@Composable
private fun NodeSortButton(
    currentSortOption: NodeSortOption,
    onSortSelect: (NodeSortOption) -> Unit,
    includeUnknown: Boolean,
    onToggleIncludeUnknown: () -> Unit,
    showDetails: Boolean,
    onToggleShowDetails: () -> Unit,
    modifier: Modifier = Modifier,
) = Box(modifier) {
    var expanded by remember { mutableStateOf(false) }

    IconButton(onClick = { expanded = true }) {
        Icon(
            imageVector = ImageVector.vectorResource(id = R.drawable.ic_twotone_sort_24),
            contentDescription = null,
            modifier = Modifier.heightIn(max = 48.dp),
            tint = MaterialTheme.colors.onSurface
        )
    }

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = { expanded = false },
        modifier = Modifier.background(MaterialTheme.colors.background.copy(alpha = 1f))
    ) {
        NodeSortOption.entries.forEach { sort ->
            DropdownMenuItem(
                onClick = {
                    onSortSelect(sort)
                    expanded = false
                },
            ) {
                Text(
                    text = stringResource(id = sort.stringRes),
                    fontWeight = if (sort == currentSortOption) FontWeight.Bold else null,
                )
            }
        }
        Divider()
        DropdownMenuItem(
            onClick = {
                onToggleIncludeUnknown()
                expanded = false
            },
        ) {
            Text(
                text = stringResource(id = R.string.node_filter_include_unknown),
            )
            AnimatedVisibility(visible = includeUnknown) {
                Icon(
                    imageVector = Icons.Default.Done,
                    contentDescription = null,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
        }
        Divider()
        DropdownMenuItem(
            onClick = {
                onToggleShowDetails()
                expanded = false
            },
        ) {
            Text(
                text = stringResource(id = R.string.node_filter_show_details),
            )
            AnimatedVisibility(visible = showDetails) {
                Icon(
                    imageVector = Icons.Default.Done,
                    contentDescription = null,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun NodeFilterTextFieldPreview() {
    AppTheme {
        NodeFilterTextField(
            filterText = "Filter text",
            onTextChange = {},
            currentSortOption = NodeSortOption.LAST_HEARD,
            onSortSelect = {},
            includeUnknown = false,
            onToggleIncludeUnknown = {},
            showDetails = false,
            onToggleShowDetails = {},
        )
    }
}
