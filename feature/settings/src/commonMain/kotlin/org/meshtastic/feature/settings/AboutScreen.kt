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
package org.meshtastic.feature.settings

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.about
import org.meshtastic.core.resources.acknowledgements
import org.meshtastic.core.resources.app_version
import org.meshtastic.core.resources.apps
import org.meshtastic.core.resources.copyright_notice
import org.meshtastic.core.resources.documentation
import org.meshtastic.core.resources.github_repository
import org.meshtastic.core.resources.need_hardware
import org.meshtastic.core.resources.need_hardware_description
import org.meshtastic.core.resources.project_information
import org.meshtastic.core.resources.website
import org.meshtastic.core.resources.what_is_meshtastic
import org.meshtastic.core.resources.what_is_meshtastic_description
import org.meshtastic.core.ui.component.ListItem
import org.meshtastic.core.ui.component.MainAppBar
import org.meshtastic.core.ui.icon.ChevronRight
import org.meshtastic.core.ui.icon.Code
import org.meshtastic.core.ui.icon.HelpOutline
import org.meshtastic.core.ui.icon.Info
import org.meshtastic.core.ui.icon.Language
import org.meshtastic.core.ui.icon.Memory
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.theme.AppTheme
import org.meshtastic.feature.settings.component.ExpressiveSection

private const val CAROUSEL_INTERVAL_MS = 3000L
private const val CROSSFADE_DURATION_MS = 500
private val CAROUSEL_IMAGE_WIDTH = 110.dp
private val CAROUSEL_IMAGE_HEIGHT = 130.dp

private const val HARDWARE_URL = "https://meshtastic.org/#hardware"
private const val GITHUB_REPO_URL = "https://github.com/meshtastic/Meshtastic-Android"
private const val WEBSITE_URL = "https://meshtastic.org"
private const val DOCS_URL = "https://meshtastic.org/docs/getting-started"

private data class PopularDevice(val name: String, val svgFileName: String)

private val POPULAR_DEVICES =
    listOf(
        PopularDevice(name = "T-Deck Plus", svgFileName = "t-deck.svg"),
        PopularDevice(name = "R1 Neo", svgFileName = "muzi_r1_neo.svg"),
        PopularDevice(name = "T1000-E Tracker", svgFileName = "tracker-t1000-e.svg"),
        PopularDevice(name = "Station G2", svgFileName = "station-g2.svg"),
        PopularDevice(name = "WisMesh Tag", svgFileName = "rak_wismesh_tag.svg"),
        PopularDevice(name = "Mesh Pocket", svgFileName = "heltec_mesh_pocket.svg"),
        PopularDevice(name = "ThinkNode M1", svgFileName = "thinknode_m1.svg"),
    )

/**
 * About screen displaying general information about Meshtastic, hardware recommendations, repository and version
 * details, acknowledgements, and project links.
 */
@Composable
fun AboutScreen(
    appVersionName: String,
    onNavigateUp: () -> Unit,
    onNavigateToAcknowledgements: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uriHandler = LocalUriHandler.current

    Scaffold(
        modifier = modifier,
        topBar = {
            MainAppBar(
                title = stringResource(Res.string.about),
                canNavigateUp = true,
                onNavigateUp = onNavigateUp,
                ourNode = null,
                showNodeChip = false,
                onClickChip = {},
                actions = {},
            )
        },
    ) { innerPadding ->
        Column(
            modifier =
            Modifier.fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            WhatIsMeshtasticSection()
            AppsSection(
                appVersionName = appVersionName,
                onNavigateToAcknowledgements = onNavigateToAcknowledgements,
                onOpenHardwareLink = { uriHandler.openUri(HARDWARE_URL) },
                onOpenRepoLink = { uriHandler.openUri(GITHUB_REPO_URL) },
            )
            ProjectInformationSection(
                onOpenWebsite = { uriHandler.openUri(WEBSITE_URL) },
                onOpenDocs = { uriHandler.openUri(DOCS_URL) },
            )
            CopyrightFooter()
        }
    }
}

@Composable
private fun WhatIsMeshtasticSection(modifier: Modifier = Modifier) {
    ExpressiveSection(title = stringResource(Res.string.what_is_meshtastic), modifier = modifier) {
        Text(
            text = stringResource(Res.string.what_is_meshtastic_description),
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun AppsSection(
    appVersionName: String,
    onNavigateToAcknowledgements: () -> Unit,
    onOpenHardwareLink: () -> Unit,
    onOpenRepoLink: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ExpressiveSection(title = stringResource(Res.string.apps), modifier = modifier) {
        NeedHardwareRow(onOpenHardwareLink = onOpenHardwareLink)
        ListItem(
            text = stringResource(Res.string.github_repository),
            leadingIcon = MeshtasticIcons.Code,
            trailingIcon = MeshtasticIcons.ChevronRight,
            onClick = onOpenRepoLink,
        )
        ListItem(
            text = stringResource(Res.string.app_version),
            leadingIcon = MeshtasticIcons.Memory,
            supportingText = appVersionName,
            trailingIcon = null,
        )
        ListItem(
            text = stringResource(Res.string.acknowledgements),
            leadingIcon = MeshtasticIcons.Info,
            trailingIcon = MeshtasticIcons.ChevronRight,
            onClick = onNavigateToAcknowledgements,
        )
    }
}

@Composable
private fun ProjectInformationSection(
    onOpenWebsite: () -> Unit,
    onOpenDocs: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ExpressiveSection(title = stringResource(Res.string.project_information), modifier = modifier) {
        ListItem(
            text = stringResource(Res.string.website),
            leadingIcon = MeshtasticIcons.Language,
            trailingIcon = MeshtasticIcons.ChevronRight,
            onClick = onOpenWebsite,
        )
        ListItem(
            text = stringResource(Res.string.documentation),
            leadingIcon = MeshtasticIcons.HelpOutline,
            trailingIcon = MeshtasticIcons.ChevronRight,
            onClick = onOpenDocs,
        )
    }
}

@Composable
private fun CopyrightFooter(modifier: Modifier = Modifier) {
    Text(
        text = stringResource(Res.string.copyright_notice),
        modifier = modifier.fillMaxWidth().padding(vertical = 8.dp),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun NeedHardwareRow(onOpenHardwareLink: () -> Unit, modifier: Modifier = Modifier) {
    var currentDeviceIndex by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(CAROUSEL_INTERVAL_MS)
            currentDeviceIndex = (currentDeviceIndex + 1) % POPULAR_DEVICES.size
        }
    }

    Row(
        modifier = modifier.fillMaxWidth().clickable(onClick = onOpenHardwareLink).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            modifier = Modifier.width(CAROUSEL_IMAGE_WIDTH).height(CAROUSEL_IMAGE_HEIGHT),
            contentAlignment = Alignment.Center,
        ) {
            Crossfade(
                targetState = POPULAR_DEVICES[currentDeviceIndex],
                animationSpec = tween(durationMillis = CROSSFADE_DURATION_MS),
            ) { device ->
                val imageUri = remember(device.svgFileName) { Res.getUri("files/${device.svgFileName}") }
                AsyncImage(
                    model = ImageRequest.Builder(LocalPlatformContext.current).data(imageUri).build(),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = stringResource(Res.string.need_hardware),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = stringResource(Res.string.need_hardware_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Preview
@Composable
private fun AboutScreenPreview() {
    AppTheme { AboutScreen(appVersionName = "2.5.0", onNavigateUp = {}, onNavigateToAcknowledgements = {}) }
}
