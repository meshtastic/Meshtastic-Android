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

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import org.meshtastic.proto.HostMetrics
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class HostMetricsCardBytesTest {

    @Test
    fun `free memory and disk rows read in decimal units`() = runComposeUiTest {
        val hostMetrics =
            HostMetrics.Builder()
                .also { wb ->
                    wb.freemem_bytes = 17_179_869_184L
                    wb.diskfree1_bytes = 1_500_000_000L
                }
                .build()
        setContent { MaterialTheme { HostMetricsCardContent(time = "", hostMetrics = hostMetrics) } }

        onNodeWithText("17.18 GB").assertExists()
        onNodeWithText("1.50 GB").assertExists()
        onNodeWithText("16 GB").assertDoesNotExist()
    }
}
