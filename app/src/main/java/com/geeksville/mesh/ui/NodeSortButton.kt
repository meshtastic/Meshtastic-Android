package com.geeksville.mesh.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Divider
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.geeksville.mesh.R
import com.geeksville.mesh.model.NodeSortOption

@Suppress("LongMethod")
@Composable
internal fun NodeSortButton(
    currentSortOption: NodeSortOption,
    onSortSelected: (NodeSortOption) -> Unit,
    includeUnknown: Boolean,
    onToggleIncludeUnknown: () -> Unit,
    showDetails: Boolean,
    onToggleShowDetails: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier) {
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
                        onSortSelected(sort)
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
}
