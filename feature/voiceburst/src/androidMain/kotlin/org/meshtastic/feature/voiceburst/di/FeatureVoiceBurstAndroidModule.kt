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

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.annotation.Module
import org.koin.core.annotation.Named
import org.koin.core.annotation.Single
import org.meshtastic.core.model.RadioController
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.core.repository.PacketRepository
import org.meshtastic.core.repository.ServiceRepository
import org.meshtastic.feature.voiceburst.audio.AndroidAudioPlayer
import org.meshtastic.feature.voiceburst.audio.AudioPlayer
import org.meshtastic.feature.voiceburst.audio.AndroidAudioRecorder
import org.meshtastic.feature.voiceburst.audio.AudioRecorder
import org.meshtastic.feature.voiceburst.codec.AndroidCodec2Encoder
import org.meshtastic.feature.voiceburst.codec.Codec2Encoder
import org.meshtastic.feature.voiceburst.repository.AndroidVoiceBurstRepository
import org.meshtastic.feature.voiceburst.repository.VoiceBurstRepository

/**
 * Koin module for the Voice Burst feature module.
 *
 * Follows the standard Android feature-module pattern:
 *   - Context and Android-only APIs remain in androidMain
 *   - commonMain has no direct Android dependencies
 */
@Module
class FeatureVoiceBurstAndroidModule {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @Single
    @Named("VoiceBurstDataStore")
    fun provideVoiceBurstDataStore(context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { context.preferencesDataStoreFile("voice_burst") },
        )

    @Single(createdAtStart = true)
    fun provideVoiceBurstRepository(
        radioController: RadioController,
        @Named("VoiceBurstDataStore") dataStore: DataStore<Preferences>,
        packetRepository: PacketRepository,
        nodeRepository: NodeRepository,
        serviceRepository: ServiceRepository,
        context: Context,
    ): VoiceBurstRepository = AndroidVoiceBurstRepository(
        radioController = radioController,
        dataStore = dataStore,
        packetRepository = packetRepository,
        nodeRepository = nodeRepository,
        serviceRepository = serviceRepository,
        context = context,
        scope = scope,
    )

    @Single
    fun provideCodec2Encoder(): Codec2Encoder = AndroidCodec2Encoder()

    @Single
    fun provideAudioRecorder(): AudioRecorder = AndroidAudioRecorder(scope = scope)

    @Single
    fun provideAudioPlayer(): AudioPlayer = AndroidAudioPlayer(scope = scope)
}
