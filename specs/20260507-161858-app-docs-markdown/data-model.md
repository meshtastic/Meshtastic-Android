# Data Model: App Documentation (Android/KMP)

## Overview

This feature introduces no Room entities and no durable database tables. Documentation content is packaged as build-time resources/assets and loaded into memory at runtime. Preferences are optional and limited to lightweight UX state (for example, remembering the last viewed section); the documentation corpus itself is never stored in Room.

The core runtime model is expressed as Kotlin `data class` and `sealed interface` types that can live in `feature/docs/src/commonMain/kotlin/...` and be shared across Android, Desktop, and iOS.

---

## Runtime Entities

### 1. `DocSection`

Represents the top-level documentation buckets shown on the website and inside the app.

```kotlin
@Serializable
sealed interface DocSection {
    @Serializable data object UserGuide : DocSection
    @Serializable data object DeveloperGuide : DocSection
}
```

| Property | Type | Notes |
|----------|------|-------|
| `id` | derived | Stable logical ID such as `user` or `developer` |
| `displayName` | derived | UI label shown in TOC/search grouping |
| `resourceDir` | derived | `docs/user/` or `docs/developer/` |

**Validation rules**
- Must map 1:1 to a top-level docs directory.
- Must be stable across releases so deep links and keyword index entries remain valid.

---

### 2. `DocPage`

Represents a single documentation page regardless of how it is rendered on a target.

```kotlin
@Serializable
data class DocPage(
    val id: String,
    val title: String,
    val section: DocSection,
    val navOrder: Int,
    val resourcePath: String,
    val keywords: List<String>,
    val aliases: List<String> = emptyList(),
    val charCount: Int,
)
```

| Field | Type | Description |
|-------|------|-------------|
| `id` | `String` | Stable slug such as `messages-and-channels` |
| `title` | `String` | Human-readable page title |
| `section` | `DocSection` | User or Developer Guide |
| `navOrder` | `Int` | Intended sort order within the section |
| `resourcePath` | `String` | Canonical packaged resource path, for example `docs/user/messages-and-channels.html` |
| `keywords` | `List<String>` | Search/retrieval vocabulary generated at build time |
| `aliases` | `List<String>` | Optional alternative search terms or renamed page slugs |
| `charCount` | `Int` | Plain-text character count used for token budgeting |

**Validation rules**
- `id` must be unique across the full corpus.
- `navOrder` must be non-negative.
- `resourcePath` must resolve in the packaged bundle for every supported target.
- `charCount` must be `> 0`.

**State transitions**
- Immutable after load.
- Replaced only when the shipped app version changes.

---

### 3. `DocPageContent`

Decouples metadata from actual content so different targets can choose HTML or markdown rendering.

```kotlin
data class DocPageContent(
    val page: DocPage,
    val html: String? = null,
    val markdown: String? = null,
    val cssPath: String? = null,
)
```

| Field | Type | Notes |
|-------|------|-------|
| `page` | `DocPage` | Metadata and lookup info |
| `html` | `String?` | Preferred on Android/WebView and for site output parity |
| `markdown` | `String?` | Optional fallback for Compose markdown rendering on Desktop/iOS |
| `cssPath` | `String?` | Shared stylesheet path for HTML surfaces |

**Rendering rules**
- Android normally prefers `html`.
- Desktop/iOS may prefer `markdown` for Compose rendering, or `html` if an embedded browser implementation is chosen.
- At least one of `html` or `markdown` must be present for each page.

---

### 4. `DocBundle`

Runtime aggregate of the full packaged documentation corpus.

```kotlin
data class DocBundle(
    val pages: List<DocPage>,
    val pageIndex: Map<String, DocPage>,
    val bundleVersion: String,
    val generatedAt: String,
    val totalBytes: Long,
)
```

| Field | Type | Description |
|-------|------|-------------|
| `pages` | `List<DocPage>` | All bundled pages |
| `pageIndex` | `Map<String, DocPage>` | O(1) lookup by page ID |
| `bundleVersion` | `String` | App/docs version identifier (`beta`, `2.8.0`, etc.) |
| `generatedAt` | `String` | ISO timestamp written by the build task |
| `totalBytes` | `Long` | Total packaged size for size-budget enforcement |

**Primary operations**

```kotlin
interface DocBundleLoader {
    suspend fun load(): DocBundle
    suspend fun readPage(pageId: String): DocPageContent?
    fun pagesBySection(section: DocSection): List<DocPage>
}
```

**Invariants**
- `pagesBySection()` sorts by `navOrder`, then title.
- `pageIndex.keys == pages.map { it.id }.toSet()`.
- `totalBytes <= 10_485_760` for release-ready bundles.

---

### 5. `KeywordIndexEntry`

Build-time artifact decoded at runtime for keyword search and AI retrieval.

```kotlin
@Serializable
data class KeywordIndexEntry(
    val id: String,
    val title: String,
    val section: String,
    val resourcePath: String,
    val navOrder: Int,
    val keywords: List<String>,
    val aliases: List<String> = emptyList(),
    val charCount: Int,
)
```

| Field | Type | Description |
|-------|------|-------------|
| `id` | `String` | Matches `DocPage.id` |
| `title` | `String` | Display title |
| `section` | `String` | `user` or `developer` |
| `resourcePath` | `String` | Packaged path to HTML/markdown asset |
| `navOrder` | `Int` | Frontmatter-derived ordering |
| `keywords` | `List<String>` | Generated retrieval terms |
| `aliases` | `List<String>` | Optional renamed terms and synonyms |
| `charCount` | `Int` | Plain-text size used for token budgeting |

**Validation rules**
- Must match the JSON schema in `contracts/keyword-index-schema.json`.
- Every entry must correspond to exactly one bundled page.

---

### 6. `DocSearchQuery` and `DocSearchResult`

Used by the shared keyword-search fallback and by Gemini Nano retrieval pre-ranking.

```kotlin
data class DocSearchQuery(
    val rawText: String,
    val normalizedTerms: List<String>,
)

data class DocSearchResult(
    val page: DocPage,
    val score: Int,
    val matchedTerms: List<String>,
)
```

| Type | Purpose |
|------|---------|
| `DocSearchQuery` | Normalized user input after lowercasing, tokenization, alias expansion, and stop-word removal |
| `DocSearchResult` | Ranked page match used in UI search results and AI context selection |

**Ranking rules**
- Exact keyword matches score higher than alias matches.
- Title matches outrank body-keyword matches.
- `navOrder` breaks ties within a section.

---

### 7. `AIDocAssistant`

Shared abstraction over the platform-specific docs assistant.

```kotlin
interface AIDocAssistant {
    /** Answer a user question about Meshtastic using bundled documentation context. */
    suspend fun answer(question: String, currentPageId: String? = null): AIDocAssistantResult

    /** Answer a user question about Meshtastic, streaming the results as they arrive. */
    fun answerStream(
        question: String,
        currentPageId: String? = null,
    ): kotlinx.coroutines.flow.Flow<AIDocAssistantResult>
}
```

Possible runtime result model:

```kotlin
sealed interface AIDocAssistantResult {
    data class Partial(
        val answer: String,
        val sourcePages: List<DocPage>,
        val usedOnDeviceModel: Boolean,
    ) : AIDocAssistantResult

    data class Success(
        val answer: String,
        val sourcePages: List<DocPage>,
        val usedOnDeviceModel: Boolean,
    ) : AIDocAssistantResult

    data class Fallback(
        val message: String,
        val suggestedPages: List<DocPage>,
    ) : AIDocAssistantResult

    data class Error(
        val reason: DocsAiError,
        val suggestedPages: List<DocPage> = emptyList(),
    ) : AIDocAssistantResult
}
```

Associated error model:

```kotlin
sealed interface DocsAiError {
    data object UnsupportedPlatform : DocsAiError
    data object UnsupportedFlavor : DocsAiError
    data object ModelUnavailable : DocsAiError
    data object Busy : DocsAiError
    data object TokenBudgetExceeded : DocsAiError
    data object Unknown : DocsAiError
}
```

**Platform behavior**
- Android `google` flavor may return `Success` using Gemini Nano.
- `fdroid`, Desktop, and iOS normally return `Fallback` or `UnsupportedPlatform` and provide suggested pages.

---

### 8. `AIDocAssistantSessionState`

UI state for the Chirpy conversation surface.

```kotlin
data class AIDocAssistantSessionState(
    val messages: List<ChirpyMessage>,
    val isLoading: Boolean,
    val draftQuestion: String,
)

@Serializable
data class SourceRef(
    val id: String,
    val title: String,
)

@Serializable
data class ChirpyMessage(
    val id: String,
    val role: ChirpyRole,
    val text: String,
    val sources: List<SourceRef> = emptyList(),
)

@Serializable
enum class ChirpyRole { USER, ASSISTANT, SYSTEM }
```

**Lifecycle**
- Session state is ephemeral and resets when the screen/process is recreated unless explicitly made saveable.
- Messages are not persisted to Room.

---

## Build-Time Artifacts

| Artifact | Produced By | Consumed By | Notes |
|----------|-------------|-------------|-------|
| `docs/**/*.md` | Human-authored | Jekyll + Gradle docs task | Canonical source |
| Generated HTML pages | Gradle docs generation task | Android `WebView`, optional Desktop/iOS embedded browser, GitHub Pages output | Site-parity artifact |
| Optional bundled markdown mirror | Gradle docs generation task | Desktop/iOS Compose renderer | Keeps shared renderer path available |
| `index.json` | Gradle docs generation task | Search, AI retrieval, bundle loader | Must match schema contract |
| `versions.yml` | Release workflow | Jekyll version selector | Web-only manifest |
| Screenshot PNGs | Roborazzi/Paparazzi/manual capture sync | Markdown pages, packaged docs assets | Inline illustrations |
| `docs.css` | Hand-authored/shared | HTML pages | Light/dark + callouts |
| Chirpy SVG/vector | Design assets | Compose UI | Branded assistant avatar |

---

## Relationships

```text
DocBundle
 ├── pages: List<DocPage>
 ├── pageIndex: Map<String, DocPage>
 └── page content files (HTML and/or markdown)

KeywordIndexEntry --1:1--> DocPage
DocSearchQuery --ranks--> DocSearchResult --references--> DocPage
AIDocAssistant --uses--> KeywordIndexEntry + DocPageContent
AIDocAssistantSessionState --contains--> ChirpyMessage --references--> DocPage IDs
```

---

## Persistence Notes

- **No Room tables** are required for documentation content.
- **No migration story** is required for docs content because the corpus is versioned with the app binary.
- Optional UX-only settings (for example, last-opened section) may live in `core:prefs`, but they are intentionally excluded from this feature’s core data model.
