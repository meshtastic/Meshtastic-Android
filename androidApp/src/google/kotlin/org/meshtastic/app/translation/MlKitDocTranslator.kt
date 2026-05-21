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
import org.meshtastic.feature.docs.translation.DocTranslationCache
import org.meshtastic.feature.docs.translation.DocTranslationService
import org.meshtastic.feature.docs.translation.DownloadResult
import org.meshtastic.feature.docs.translation.MarkdownTranslationSegmenter
import org.meshtastic.feature.docs.translation.TranslationResult
import org.meshtastic.feature.docs.translation.md5Hash
import kotlin.coroutines.resume

/**
 * ML Kit-powered document translation service for the Google flavor.
 *
 * Downloads language models on-demand (~30MB each) and translates markdown content while preserving structure via
 * [MarkdownTranslationSegmenter].
 */
class MlKitDocTranslator(private val cache: DocTranslationCache) : DocTranslationService {

    private val modelManager = RemoteModelManager.getInstance()

    override suspend fun translatePage(pageId: String, markdown: String, targetLocale: String): TranslationResult {
        val sourceHash = md5Hash(markdown)

        // Check cache first
        cache.get(pageId, targetLocale, sourceHash)?.let { cached ->
            return TranslationResult.Success(cached)
        }

        // Check if language is supported by ML Kit
        val targetLang = TranslateLanguage.fromLanguageTag(targetLocale) ?: return TranslationResult.Unavailable

        // Auto-download model if not present
        if (!isModelDownloaded(targetLang)) {
            Logger.i(tag = "MlKitDocTranslator") {
                "Downloading model for $targetLocale (~${ESTIMATED_MODEL_SIZE_MB}MB)"
            }
            val downloadResult = downloadLanguageModel(targetLocale)
            if (downloadResult is DownloadResult.Failed) {
                Logger.w(tag = "MlKitDocTranslator") { "Model download failed: ${downloadResult.reason}" }
                return TranslationResult.ModelDownloadRequired(targetLocale, ESTIMATED_MODEL_SIZE_MB)
            }
        }

        // Perform translation
        return try {
            val options =
                TranslatorOptions.Builder()
                    .setSourceLanguage(TranslateLanguage.ENGLISH)
                    .setTargetLanguage(targetLang)
                    .build()

            val translator = Translation.getClient(options)
            try {
                val translated =
                    MarkdownTranslationSegmenter.translateMarkdown(markdown) { text ->
                        suspendCancellableCoroutine { cont ->
                            translator
                                .translate(text)
                                .addOnSuccessListener { cont.resume(it) }
                                .addOnFailureListener { e ->
                                    Logger.w(tag = "MlKitDocTranslator") {
                                        "Segment translation failed, using source: ${e.message}"
                                    }
                                    cont.resume(text)
                                }
                        }
                    }

                // Cache the result
                cache.put(pageId, targetLocale, sourceHash, translated)
                TranslationResult.Success(translated)
            } finally {
                translator.close()
            }
        } catch (e: Exception) {
            Logger.w(tag = "MlKitDocTranslator") { "Translation failed for $pageId to $targetLocale: ${e.message}" }
            TranslationResult.Unavailable
        }
    }

    override suspend fun isLanguageAvailable(locale: String): Boolean {
        val lang = TranslateLanguage.fromLanguageTag(locale) ?: return false
        return isModelDownloaded(lang)
    }

    override suspend fun downloadLanguageModel(locale: String): DownloadResult {
        val lang =
            TranslateLanguage.fromLanguageTag(locale) ?: return DownloadResult.Failed("Unsupported language: $locale")

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
    }
}
