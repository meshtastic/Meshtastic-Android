/*
 * Copyright (c) 2026 Chris7X
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
package org.meshtastic.feature.voiceburst.di

import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module

/**
 * Koin commonMain module for the Voice Burst feature.
 *
 * @ComponentScan scans the package and auto-registers via KSP:
 *   - VoiceBurstViewModel (@KoinViewModel with @InjectedParam destNodeId)
 *
 * Android-only dependencies (AudioRecorder, Codec2Encoder, DataStore, Repository)
 * are registered in [FeatureVoiceBurstAndroidModule] (androidMain).
 */
@Module
@ComponentScan("org.meshtastic.feature.voiceburst")
class FeatureVoiceBurstModule
