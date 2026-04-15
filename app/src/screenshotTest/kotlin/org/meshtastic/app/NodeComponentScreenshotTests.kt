/*
 * Copyright (c) 2025-2026 Meshtastic LLC
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
package org.meshtastic.app

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import org.meshtastic.app.preview.CooldownButtonsPreview
import org.meshtastic.app.preview.InfoCardPreview
import org.meshtastic.app.preview.LegendIndicatorsPreview
import org.meshtastic.app.preview.MetricLogComponentsPreview
import org.meshtastic.app.preview.MetricValueRowPreview
import org.meshtastic.app.preview.MultiPreview
import org.meshtastic.app.preview.NodeStatusIconsPreview
import org.meshtastic.app.preview.SelectableMetricCardPreview
import org.meshtastic.app.preview.TimeFrameSelectorPreview

/** Screenshot tests for node feature components (InfoCard, NodeStatusIcons, metrics, etc.). */
class NodeComponentScreenshotTests {
    @PreviewTest
    @MultiPreview
    @Composable
    fun infoCardScreenshot() {
        InfoCardPreview()
    }

    @PreviewTest
    @MultiPreview
    @Composable
    fun nodeStatusIconsScreenshot() {
        NodeStatusIconsPreview()
    }

    @PreviewTest
    @MultiPreview
    @Composable
    fun timeFrameSelectorScreenshot() {
        TimeFrameSelectorPreview()
    }

    @PreviewTest
    @MultiPreview
    @Composable
    fun metricLogComponentsScreenshot() {
        MetricLogComponentsPreview()
    }

    @PreviewTest
    @MultiPreview
    @Composable
    fun selectableMetricCardScreenshot() {
        SelectableMetricCardPreview()
    }

    @PreviewTest
    @MultiPreview
    @Composable
    fun metricValueRowScreenshot() {
        MetricValueRowPreview()
    }

    @PreviewTest
    @MultiPreview
    @Composable
    fun legendIndicatorsScreenshot() {
        LegendIndicatorsPreview()
    }

    @PreviewTest
    @MultiPreview
    @Composable
    fun cooldownButtonsScreenshot() {
        CooldownButtonsPreview()
    }
}
