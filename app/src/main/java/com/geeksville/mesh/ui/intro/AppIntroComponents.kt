/*
 * Copyright (c) 2025 Meshtastic LLC
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

package com.geeksville.mesh.ui.intro

import androidx.annotation.DrawableRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.geeksville.mesh.R
import com.geeksville.mesh.ui.common.components.AutoLinkText
import kotlinx.coroutines.launch

// Data class for a slide
private data class IntroSlide(
    val title: String,
    val description: String,
    @DrawableRes val imageRes: Int,
)

@Suppress("LongMethod")
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppIntroductionScreen(onDone: () -> Unit) {
    val slides = slides()
    val pagerState = rememberPagerState(pageCount = { slides.size })
    val scope = rememberCoroutineScope()

    Scaffold(
        bottomBar = {
            Surface(shadowElevation = 8.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    if (pagerState.currentPage < slides.size - 1) {
                        TextButton(onClick = onDone) {
                            Text(stringResource(id = R.string.app_intro_skip_button))
                        }
                    } else {
                        TextButton(onClick = {
                            scope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage - 1)
                            }
                        }) {
                            Text(stringResource(id = R.string.app_intro_back_button))
                        }
                    }

                    PagerIndicator(
                        slideCount = slides.size,
                        currentPage = pagerState.currentPage,
                        modifier = Modifier.weight(1f)
                    )

                    if (pagerState.currentPage < slides.size - 1) {
                        TextButton(
                            onClick = {
                                scope.launch {
                                    pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                }
                            }
                        ) {
                            Text(stringResource(id = R.string.app_intro_next_button))
                        }
                    } else {
                        Button(onClick = onDone) {
                            Text(stringResource(id = R.string.app_intro_done_button))
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) { page ->
            IntroScreenContent(slides[page])
        }
    }
}

@Composable
private fun slides(): List<IntroSlide> {
    val slides = listOf(
        IntroSlide(
            title = stringResource(R.string.intro_welcome),
            description = stringResource(R.string.intro_welcome_text),
            imageRes = R.drawable.app_icon,
        ),
        IntroSlide(
            title = stringResource(R.string.intro_started),
            description = stringResource(R.string.intro_started_text),
            imageRes = R.drawable.icon_meanings,
        ),
        IntroSlide(
            title = stringResource(R.string.intro_encryption),
            description = stringResource(R.string.intro_encryption_text),
            imageRes = R.drawable.channel_name_image,
        )
    )
    return slides
}

@Composable
private fun IntroScreenContent(slide: IntroSlide) {
    Surface(
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Image(
                painter = painterResource(id = slide.imageRes),
                contentDescription = slide.title,
                modifier = Modifier
                    .size(200.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop,
            )
            Spacer(modifier = Modifier.height(32.dp))
            Text(
                text = slide.title,
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            AutoLinkText(
                text = slide.description,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

@Composable
fun PagerIndicator(slideCount: Int, currentPage: Int, modifier: Modifier = Modifier) {
    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
    ) {
        repeat(slideCount) { iteration ->
            val color =
                if (currentPage == iteration) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(
                        alpha = 0.2f
                    )
                }
            Box(
                modifier = Modifier
                    .padding(4.dp)
                    .clip(CircleShape)
                    .background(color)
                    .size(12.dp)
            )
        }
    }
}
