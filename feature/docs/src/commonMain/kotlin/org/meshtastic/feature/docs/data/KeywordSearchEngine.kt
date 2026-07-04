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
package org.meshtastic.feature.docs.data

import org.koin.core.annotation.Single
import org.meshtastic.feature.docs.model.DocPage
import org.meshtastic.feature.docs.model.DocSearchQuery
import org.meshtastic.feature.docs.model.DocSearchResult

/**
 * Keyword-based search engine for the docs corpus. Provides search functionality without AI, working on all platforms.
 */
@Single
class KeywordSearchEngine(private val bundleLoader: DocBundleLoader) {
    private val stopWords =
        setOf(
            "the",
            "a",
            "an",
            "is",
            "are",
            "was",
            "were",
            "be",
            "been",
            "to",
            "of",
            "in",
            "for",
            "on",
            "with",
            "at",
            "by",
            "from",
            "it",
            "this",
            "that",
            "how",
            "what",
            "where",
            "when",
            "do",
            "does",
            "can",
            "will",
            "my",
            "i",
            "me",
            "and",
            "or",
        )

    /** Search the docs corpus with the given query text. */
    @Suppress("ReturnCount")
    suspend fun search(queryText: String): List<DocSearchResult> {
        if (queryText.isBlank()) return emptyList()

        val query = normalize(queryText)
        if (query.normalizedTerms.isEmpty()) return emptyList()

        val bundle = bundleLoader.load()
        return bundle.pages
            .map { page -> score(page, query) }
            .filter { it.score > 0 }
            .sortedWith(compareByDescending<DocSearchResult> { it.score }.thenBy { it.page.navOrder })
    }

    /** Select top pages within a token budget for AI retrieval context. */
    suspend fun selectForTokenBudget(queryText: String, maxChars: Int): List<DocPage> {
        val results = search(queryText)
        val selected = mutableListOf<DocPage>()
        var totalChars = 0

        for (result in results) {
            if (totalChars + result.page.charCount > maxChars) break
            selected.add(result.page)
            totalChars += result.page.charCount
        }

        return selected
    }

    fun normalize(queryText: String): DocSearchQuery {
        val terms =
            queryText
                .lowercase()
                .replace(Regex("[^\\p{L}\\p{N}\\s-]"), " ")
                .split(Regex("\\s+"))
                .filter { it.length >= 2 && it !in stopWords }
                .distinct()

        return DocSearchQuery(rawText = queryText, normalizedTerms = terms)
    }

    @Suppress("LoopWithTooManyJumpStatements")
    private fun score(page: DocPage, query: DocSearchQuery): DocSearchResult {
        var totalScore = 0
        val matchedTerms = mutableListOf<String>()

        for (term in query.normalizedTerms) {
            // Title match (highest priority)
            if (page.title.lowercase().contains(term)) {
                totalScore += TITLE_MATCH_SCORE
                matchedTerms.add(term)
                continue
            }

            // Exact keyword match
            if (page.keywords.any { it.lowercase() == term }) {
                totalScore += KEYWORD_EXACT_SCORE
                matchedTerms.add(term)
                continue
            }

            // Partial keyword match
            if (page.keywords.any { it.lowercase().contains(term) }) {
                totalScore += KEYWORD_PARTIAL_SCORE
                matchedTerms.add(term)
                continue
            }

            // Alias match
            if (page.aliases.any { it.lowercase() == term || it.lowercase().contains(term) }) {
                totalScore += ALIAS_MATCH_SCORE
                matchedTerms.add(term)
                continue
            }

            // ID/slug match
            if (page.id.contains(term)) {
                totalScore += ID_MATCH_SCORE
                matchedTerms.add(term)
            }
        }

        return DocSearchResult(page = page, score = totalScore, matchedTerms = matchedTerms)
    }

    companion object {
        const val TITLE_MATCH_SCORE = 10
        const val KEYWORD_EXACT_SCORE = 7
        const val KEYWORD_PARTIAL_SCORE = 4
        const val ALIAS_MATCH_SCORE = 5
        const val ID_MATCH_SCORE = 3
    }
}
