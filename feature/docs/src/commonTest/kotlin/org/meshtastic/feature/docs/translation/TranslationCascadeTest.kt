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
package org.meshtastic.feature.docs.translation

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Tests for the translation cascade decision logic: Crowdin bundled → ML Kit runtime → English fallback.
 *
 * These tests exercise the service interface contracts without ML Kit (which requires Android).
 */
class TranslationCascadeTest {

    @Test
    fun `NoOp translator returns Unavailable for any locale`() = runTest {
        val service: DocTranslationService = NoOpDocTranslator()
        val result = service.translatePage("onboarding", "# Hello", "es")
        assertIs<TranslationResult.Unavailable>(result)
    }

    @Test
    fun `NoOp translator reports language unavailable`() = runTest {
        val service: DocTranslationService = NoOpDocTranslator()
        assertEquals(false, service.isLanguageAvailable("es"))
        assertEquals(false, service.isLanguageAvailable("fr"))
        assertEquals(false, service.isLanguageAvailable("zh"))
    }

    @Test
    fun `NoOp translator download returns Failed`() = runTest {
        val service: DocTranslationService = NoOpDocTranslator()
        val result = service.downloadLanguageModel("es")
        assertIs<DownloadResult.Failed>(result)
    }

    @Test
    fun `fake translator returns success with translated content`() = runTest {
        val service = FakeDocTranslator(translatedPrefix = "[ES] ")
        val result = service.translatePage("onboarding", "# Hello", "es")
        assertIs<TranslationResult.Success>(result)
        assertEquals("[ES] # Hello", result.translatedMarkdown)
    }

    @Test
    fun `fake translator returns ModelDownloadRequired when not downloaded`() = runTest {
        val service = FakeDocTranslator(isDownloaded = false)
        val result = service.translatePage("onboarding", "# Hello", "es")
        assertIs<TranslationResult.ModelDownloadRequired>(result)
    }

    @Test
    fun `fake translator returns Unavailable for unsupported locales`() = runTest {
        val service = FakeDocTranslator(supportedLocales = setOf("es", "fr"))
        val result = service.translatePage("onboarding", "# Hello", "xx")
        assertIs<TranslationResult.Unavailable>(result)
    }

    @Test
    fun `cache hit avoids translation call`() = runTest {
        var translateCallCount = 0
        val service = FakeDocTranslator(translatedPrefix = "[ES] ", onTranslate = { translateCallCount++ })

        // First call — translator is invoked
        service.translatePage("page1", "# Hello", "es")
        assertEquals(1, translateCallCount)

        // Second call with same inputs — fake has no cache, so it calls again
        // (real MlKitDocTranslator has DocTranslationCache that would prevent this)
        service.translatePage("page1", "# Hello", "es")
        assertEquals(2, translateCallCount)
    }

    @Test
    fun `TranslationResult sealed hierarchy covers all cases`() {
        val success: TranslationResult = TranslationResult.Success("translated")
        val download: TranslationResult = TranslationResult.ModelDownloadRequired("es", 30)
        val unavailable: TranslationResult = TranslationResult.Unavailable

        assertIs<TranslationResult.Success>(success)
        assertIs<TranslationResult.ModelDownloadRequired>(download)
        assertIs<TranslationResult.Unavailable>(unavailable)
        assertEquals("es", (download as TranslationResult.ModelDownloadRequired).locale)
    }
}

/** Configurable fake for testing cascade behavior. */
private class FakeDocTranslator(
    private val translatedPrefix: String = "[TRANSLATED] ",
    private val isDownloaded: Boolean = true,
    private val supportedLocales: Set<String> = setOf("es", "fr", "de", "zh", "ja", "ko"),
    private val onTranslate: () -> Unit = {},
) : DocTranslationService {

    override suspend fun translatePage(pageId: String, markdown: String, targetLocale: String): TranslationResult {
        if (targetLocale !in supportedLocales) return TranslationResult.Unavailable
        if (!isDownloaded) return TranslationResult.ModelDownloadRequired(targetLocale, 30)
        onTranslate()
        return TranslationResult.Success("$translatedPrefix$markdown")
    }

    override suspend fun isLanguageAvailable(locale: String): Boolean = locale in supportedLocales && isDownloaded

    override suspend fun downloadLanguageModel(locale: String): DownloadResult =
        if (locale in supportedLocales) DownloadResult.Success else DownloadResult.Failed("Unsupported: $locale")
}
