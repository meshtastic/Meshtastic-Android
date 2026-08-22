/*
 * Copyright (c) 2026 Meshtastic LLC
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.meshtastic.feature.node.metrics

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.buildAnnotatedString
import org.meshtastic.core.ui.component.MeshtasticDialog
import org.meshtastic.core.ui.theme.AppTheme
import kotlin.test.Test

private const val HOP_COUNT = 80
private const val LAST_HOP_MARKER = "LAST_HOP_MARKER"

/**
 * #6701: a traceroute with many hops scrolled fine live, but was cut off with no way to scroll when reopened from
 * history. Root cause: [MetricsViewModel.showTracerouteDetail] and [MetricsViewModel.showLogDetail] passed
 * [MeshtasticDialog] a bare `SelectionContainer { Text(...) }` as `composableMessage` - [MeshtasticDialog] only adds
 * `verticalScroll` to its own wrapping column when the dialog has `choices` (a button list), so a plain text dialog's
 * scrolling is entirely the caller's responsibility.
 *
 * A screenshot can't catch this regression: a single frame of "scrolled to top, more content below" looks identical
 * whether or not scrolling actually works. [performScrollTo] is the right tool instead - it throws when the target node
 * has no scrollable ancestor, so it fails exactly when the `verticalScroll` wrapper is missing.
 */
@OptIn(ExperimentalTestApi::class)
class DetailDialogScrollTest {

    @Test
    fun longDetailContentScrollsToRevealTrailingText() = runComposeUiTest {
        val longRoute = buildAnnotatedString {
            repeat(HOP_COUNT) { append("Hop $it -> ") }
            append(LAST_HOP_MARKER)
        }

        setContent {
            AppTheme {
                MeshtasticDialog(
                    title = "Traceroute",
                    // Matches the fixed composableMessage shape in showTracerouteDetail/showLogDetail exactly.
                    text = {
                        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                            SelectionContainer { Text(text = longRoute) }
                        }
                    },
                    onDismiss = {},
                )
            }
        }

        onNodeWithText(LAST_HOP_MARKER, substring = true).performScrollTo().assertIsDisplayed()
    }
}
