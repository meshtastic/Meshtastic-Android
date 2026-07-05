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
package org.meshtastic.app.translation

import co.touchlab.kermit.Logger
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import org.meshtastic.feature.messaging.translation.DownloadResult
import org.meshtastic.feature.messaging.translation.MessageTranslationService
import org.meshtastic.feature.messaging.translation.TranslationResult
import kotlin.coroutines.resume

/**
 * ML Kit-powered chat message translation service for the Google flavor.
 *
 * Unlike [org.meshtastic.app.translation.MlKitDocTranslator], this does not auto-download models — it reports
 * [TranslationResult.ModelDownloadRequired] so the caller can prompt the user before pulling ~30MB over the network.
 */
class MlKitMessageTranslator : MessageTranslationService {

    private val modelManager = RemoteModelManager.getInstance()

    override suspend fun translate(text: String, targetLocale: String): TranslationResult {
        val targetLang = TranslateLanguage.fromLanguageTag(targetLocale) ?: return TranslationResult.Unavailable

        if (!isModelDownloaded(targetLang)) {
            return TranslationResult.ModelDownloadRequired(targetLocale, ESTIMATED_MODEL_SIZE_MB)
        }

        val options =
            TranslatorOptions.Builder()
                .setSourceLanguage(TranslateLanguage.ENGLISH)
                .setTargetLanguage(targetLang)
                .build()
        val translator = Translation.getClient(options)
        return try {
            val translated =
                suspendCancellableCoroutine<String> { cont ->
                    translator
                        .translate(text)
                        .addOnSuccessListener { cont.resume(it) }
                        .addOnFailureListener { e ->
                            Logger.w(tag = TAG) { "Translation failed: ${e.message}" }
                            cont.resume(text)
                        }
                }
            TranslationResult.Success(translated)
        } catch (e: Exception) {
            Logger.w(tag = TAG) { "Translation failed for locale $targetLocale: ${e.message}" }
            TranslationResult.Unavailable
        } finally {
            translator.close()
        }
    }

    override suspend fun isLanguageAvailable(targetLocale: String): Boolean =
        TranslateLanguage.fromLanguageTag(targetLocale) != null

    override suspend fun downloadLanguageModel(targetLocale: String): DownloadResult {
        val lang =
            TranslateLanguage.fromLanguageTag(targetLocale)
                ?: return DownloadResult.Failed("Unsupported language: $targetLocale")

        val model = TranslateRemoteModel.Builder(lang).build()
        val conditions = DownloadConditions.Builder().build()

        return suspendCancellableCoroutine { cont ->
            modelManager
                .download(model, conditions)
                .addOnSuccessListener { cont.resume(DownloadResult.Success) }
                .addOnFailureListener { e -> cont.resume(DownloadResult.Failed(e.message ?: "Download failed")) }
        }
    }

    private suspend fun isModelDownloaded(lang: String): Boolean = suspendCancellableCoroutine { cont ->
        val model = TranslateRemoteModel.Builder(lang).build()
        modelManager
            .isModelDownloaded(model)
            .addOnSuccessListener { cont.resume(it) }
            .addOnFailureListener { cont.resume(false) }
    }

    companion object {
        private const val ESTIMATED_MODEL_SIZE_MB = 30
        private const val TAG = "MlKitMessageTranslator"
    }
}
