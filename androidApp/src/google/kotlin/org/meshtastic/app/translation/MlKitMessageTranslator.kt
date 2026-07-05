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
import com.google.mlkit.nl.languageid.LanguageIdentification
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
 * ML Kit-powered chat message translation for the Google flavor.
 *
 * Detects the source language on-device, then translates to the target locale. Unlike [MlKitDocTranslator], missing
 * language models (~30MB each) are never downloaded implicitly — [translate] reports them via
 * [TranslationResult.ModelDownloadRequired] so the UI can ask the user first, then calls [downloadLanguageModels].
 */
class MlKitMessageTranslator : MessageTranslationService {

    private val modelManager = RemoteModelManager.getInstance()

    override suspend fun translate(text: String, targetLocale: String): TranslationResult {
        if (text.isBlank()) return TranslationResult.Unavailable
        val targetLang =
            TranslateLanguage.fromLanguageTag(normalizeLanguageTag(targetLocale))
                ?: return TranslationResult.Unavailable

        val detectedTag = identifySourceLanguage(text)
        if (detectedTag == UNDETERMINED_LANGUAGE_TAG) return TranslationResult.Unavailable
        val sourceLang =
            TranslateLanguage.fromLanguageTag(normalizeLanguageTag(detectedTag)) ?: return TranslationResult.Unavailable
        if (sourceLang == targetLang) return TranslationResult.NotRequired

        val missing = listOf(sourceLang, targetLang).filterNot { isModelDownloaded(it) }
        if (missing.isNotEmpty()) {
            return TranslationResult.ModelDownloadRequired(missing, ESTIMATED_MODEL_SIZE_MB * missing.size)
        }

        return try {
            val options =
                TranslatorOptions.Builder().setSourceLanguage(sourceLang).setTargetLanguage(targetLang).build()
            val translator = Translation.getClient(options)
            val translated =
                try {
                    suspendCancellableCoroutine { cont ->
                        translator
                            .translate(text)
                            .addOnSuccessListener { cont.resume(it) }
                            .addOnFailureListener { e ->
                                Logger.w(tag = TAG) { "Translation failed: ${e.message}" }
                                cont.resume(null)
                            }
                    }
                } finally {
                    translator.close()
                }
            translated?.let { TranslationResult.Success(it) } ?: TranslationResult.Unavailable
        } catch (e: Exception) {
            Logger.w(tag = TAG) { "Translation to $targetLocale failed: ${e.message}" }
            TranslationResult.Unavailable
        }
    }

    /** Available means ML Kit can translate into [locale], even if the model still needs downloading. */
    override suspend fun isLanguageAvailable(locale: String): Boolean =
        TranslateLanguage.fromLanguageTag(normalizeLanguageTag(locale)) != null

    override suspend fun downloadLanguageModels(languageTags: List<String>): DownloadResult {
        languageTags.forEach { tag ->
            val lang =
                TranslateLanguage.fromLanguageTag(normalizeLanguageTag(tag))
                    ?: return DownloadResult.Failed("Unsupported language: $tag")
            val result = downloadModel(lang)
            if (result is DownloadResult.Failed) {
                Logger.w(tag = TAG) { "Model download for $tag failed: ${result.reason}" }
                return result
            }
        }
        return DownloadResult.Success
    }

    private suspend fun identifySourceLanguage(text: String): String {
        val client = LanguageIdentification.getClient()
        return try {
            suspendCancellableCoroutine { cont ->
                client
                    .identifyLanguage(text)
                    .addOnSuccessListener { cont.resume(it) }
                    .addOnFailureListener { e ->
                        Logger.w(tag = TAG) { "Language identification failed: ${e.message}" }
                        cont.resume(UNDETERMINED_LANGUAGE_TAG)
                    }
            }
        } finally {
            client.close()
        }
    }

    private suspend fun downloadModel(lang: String): DownloadResult {
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

    /** ML Kit expects modern ISO 639-1 codes; Android's [java.util.Locale] can emit legacy ones. */
    private fun normalizeLanguageTag(tag: String): String = when (tag) {
        "iw" -> "he"
        "in" -> "id"
        "ji" -> "yi"
        else -> tag
    }

    companion object {
        private const val TAG = "MlKitMessageTranslator"
        private const val ESTIMATED_MODEL_SIZE_MB = 30
        private const val UNDETERMINED_LANGUAGE_TAG = "und"
    }
}
