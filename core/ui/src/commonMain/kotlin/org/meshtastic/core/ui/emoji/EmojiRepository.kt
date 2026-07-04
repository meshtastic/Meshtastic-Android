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
package org.meshtastic.core.ui.emoji

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import meshtasticandroid.core.ui.generated.resources.Res
import org.koin.core.annotation.Single

/** A single emoji entry with optional skin-tone support and search keywords. */
internal data class Emoji(
    val base: String,
    val keywords: List<String> = emptyList(),
    val supportsSkinTone: Boolean = false,
)

/** A named category of emojis with an icon emoji for the tab. */
internal data class EmojiCategory(val name: String, val icon: String, val emojis: List<Emoji>)

/** Unicode skin tone modifiers (Fitzpatrick scale). */
internal enum class SkinTone(val modifier: String, val label: String, val preview: String) {
    DEFAULT("", "Default", "👋"),
    LIGHT("\uD83C\uDFFB", "Light", "👋🏻"),
    MEDIUM_LIGHT("\uD83C\uDFFC", "Medium-Light", "👋🏼"),
    MEDIUM("\uD83C\uDFFD", "Medium", "👋🏽"),
    MEDIUM_DARK("\uD83C\uDFFE", "Medium-Dark", "👋🏾"),
    DARK("\uD83C\uDFFF", "Dark", "👋🏿"),
}

/**
 * Applies a skin tone modifier to a base emoji string. Only works correctly for single-codepoint emojis that support
 * skin tones.
 */
internal fun Emoji.withSkinTone(tone: SkinTone): String {
    if (!supportsSkinTone || tone == SkinTone.DEFAULT) return base
    val firstChar = base[0]
    val charCount = if (firstChar.isHighSurrogate() && base.length > 1) 2 else 1
    val baseChar = base.substring(0, charCount)
    val after = base.substring(charCount)
    return baseChar + tone.modifier + after
}

// ── JSON models (compact wire format) ──────────────────────────────────────────

@Serializable
private data class EmojiJson(
    @SerialName("b") val base: String,
    @SerialName("k") val keywords: List<String> = emptyList(),
    @SerialName("s") val supportsSkinTone: Boolean = false,
)

@Serializable private data class CategoryJson(val name: String, val icon: String, val emojis: List<EmojiJson>)

@Serializable private data class EmojiDataJson(val version: String, val categories: List<CategoryJson>)

// ── Repository ─────────────────────────────────────────────────────────────────

/**
 * Provides access to the emoji catalog loaded from a bundled JSON resource.
 *
 * Data is loaded lazily on first access and cached for the lifetime of the app. The JSON resource contains Unicode 16.0
 * emoji data with CLDR-based keywords.
 */
@Single
internal class EmojiRepository {

    private var cached: Pair<List<EmojiCategory>, List<Emoji>>? = null

    val categories: List<EmojiCategory>
        get() = ensureLoaded().first

    val all: List<Emoji>
        get() = ensureLoaded().second

    suspend fun preload() {
        if (cached != null) return
        val bytes = Res.readBytes("files/emoji-data.json")
        val json = Json { ignoreUnknownKeys = true }
        val data = json.decodeFromString<EmojiDataJson>(bytes.decodeToString())
        val cats =
            data.categories.map { cat ->
                EmojiCategory(
                    name = cat.name,
                    icon = cat.icon,
                    emojis =
                    cat.emojis.map { e ->
                        Emoji(base = e.base, keywords = e.keywords, supportsSkinTone = e.supportsSkinTone)
                    },
                )
            }
        cached = cats to cats.flatMap { it.emojis }
    }

    private fun ensureLoaded(): Pair<List<EmojiCategory>, List<Emoji>> =
        cached ?: error("EmojiRepository not loaded. Call preload() before accessing data.")
}
