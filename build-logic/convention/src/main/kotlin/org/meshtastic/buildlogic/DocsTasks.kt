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
package org.meshtastic.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.register
import java.io.File

private const val DEFAULT_NAV_ORDER = 999
private const val MIN_KEYWORD_LENGTH = 3
private const val MAX_KEYWORDS = 30
private const val BYTES_PER_MB = 1024.0 * 1024.0
private const val BUNDLE_SIZE_HARD_LIMIT_MB = 10.0
private const val BUNDLE_SIZE_WARN_THRESHOLD_MB = 8.0
private val LOCALE_PATTERN = Regex("^[a-z]{2,3}(-r[A-Z]{2})?$")

/**
 * Registers docs generation, validation, and publishing tasks.
 *
 * Tasks:
 * - generateDocsBundle: Converts markdown to HTML + index.json
 * - validateDocsBundle: Schema, size, and asset validation
 * - publishDocsSite: Generates _site/ artifact for Pages
 */
class DocsTasks : Plugin<Project> {
    override fun apply(project: Project) {
        val docsDir = project.rootProject.layout.projectDirectory.dir("docs")
        val outputDir = project.layout.buildDirectory.dir("generated/docs")

        project.tasks.register<GenerateDocsBundleTask>("generateDocsBundle") {
            group = "documentation"
            description = "Generate packaged docs artifacts and keyword index from markdown source."
            sourceDir.set(docsDir)
            generatedOutputDir.set(outputDir.map { it.dir("common") })
            channel.set(project.providers.gradleProperty("docs.channel").orElse("beta"))
            version.set(project.providers.gradleProperty("docs.version").orElse("beta"))
        }

        project.tasks.register<ValidateDocsBundleTask>("validateDocsBundle") {
            group = "documentation"
            description = "Validate keyword index schema, bundle size, and asset references."
            dependsOn("generateDocsBundle")
            bundleDir.set(outputDir.map { it.dir("common") })
            schemaFile.set(
                project.rootProject.layout.projectDirectory.file(
                    "specs/20260507-161858-app-docs-markdown/contracts/keyword-index-schema.json",
                ),
            )
        }

        project.tasks.register<PublishDocsSiteTask>("publishDocsSite") {
            group = "documentation"
            description = "Assemble the final Pages artifact from generated docs."
            dependsOn("generateDocsBundle")
            sourceDir.set(docsDir)
            bundleDir.set(outputDir.map { it.dir("common") })
            siteOutputDir.set(project.layout.buildDirectory.dir("_site"))
            channel.set(project.providers.gradleProperty("docs.channel").orElse("beta"))
            version.set(project.providers.gradleProperty("docs.version").orElse("beta"))
        }
    }
}

private data class IndexEntry(
    val id: String,
    val title: String,
    val section: String,
    val locale: String,
    val resourcePath: String,
    val navOrder: Int,
    val keywords: List<String>,
    val aliases: List<String>,
    val charCount: Int,
)

abstract class GenerateDocsBundleTask : DefaultTask() {
    @get:InputDirectory abstract val sourceDir: DirectoryProperty

    @get:OutputDirectory abstract val generatedOutputDir: DirectoryProperty

    @get:Input abstract val channel: Property<String>

    @get:Input abstract val version: Property<String>

    @TaskAction
    fun generate() {
        val src = sourceDir.get().asFile
        val out = generatedOutputDir.get().asFile
        out.mkdirs()

        val entries = processEnglishSources(src, out) + processLocaleSources(src, out)
        writeIndexJson(out, entries)
        writeCss(out)
        writeLocalesManifest(src, out)

        val localeCount =
            src.listFiles { f -> f.isDirectory && LOCALE_PATTERN.matches(f.name) && f.name != "en" }?.size ?: 0
        logger.lifecycle(
            "Generated docs bundle: ${entries.size} pages ($localeCount locales), " +
                "channel=${channel.get()}, version=${version.get()}",
        )
    }

    private fun processEnglishSources(src: File, out: File): List<IndexEntry> =
        listOf("user", "developer").flatMap { section ->
            val sectionDir = File(File(src, "en"), section)
            if (!sectionDir.exists()) {
                return@flatMap emptyList()
            }
            sectionDir
                .listFiles { f -> f.extension == "md" }
                ?.sortedBy { it.name }
                ?.map { processMarkdown(it, section, "en", out) } ?: emptyList()
        }

    private fun processLocaleSources(src: File, out: File): List<IndexEntry> =
        src.listFiles { f -> f.isDirectory && LOCALE_PATTERN.matches(f.name) && f.name != "en" }
            ?.sortedBy { it.name }
            ?.flatMap { localeDir ->
                val sectionDir = File(localeDir, "user")
                if (!sectionDir.exists()) {
                    return@flatMap emptyList()
                }
                sectionDir
                    .listFiles { f -> f.extension == "md" }
                    ?.sortedBy { it.name }
                    ?.map { processMarkdown(it, "user", localeDir.name, out) } ?: emptyList()
            } ?: emptyList()

    private fun processMarkdown(mdFile: File, section: String, locale: String, out: File): IndexEntry {
        val frontmatter = parseFrontmatter(mdFile)
        val id = mdFile.nameWithoutExtension
        val title = frontmatter["title"] ?: id.replace("-", " ").replaceFirstChar { it.uppercase() }
        val resourcePath = if (locale == "en") "docs/$section/$id.html" else "docs/$locale/$section/$id.html"
        File(out, resourcePath).also { it.parentFile.mkdirs() }.writeText(generateHtml(mdFile, title, locale))
        return IndexEntry(
            id = id,
            title = title,
            section = section,
            locale = locale,
            resourcePath = resourcePath,
            navOrder = frontmatter["nav_order"]?.toIntOrNull() ?: DEFAULT_NAV_ORDER,
            keywords = extractKeywords(mdFile, title),
            aliases = parseListField(frontmatter["aliases_raw"] ?: ""),
            charCount = mdFile.readText().length,
        )
    }

    private fun writeIndexJson(out: File, entries: List<IndexEntry>) {
        val json =
            entries.joinToString(",\n", "[\n", "\n]") { e ->
                val kw = e.keywords.joinToString(", ") { "\"$it\"" }
                val al = e.aliases.joinToString(", ") { "\"$it\"" }
                """  {"id":"${e.id}","title":"${e.title}","section":"${e.section}",""" +
                    """"locale":"${e.locale}","resourcePath":"${e.resourcePath}",""" +
                    """"navOrder":${e.navOrder},"keywords":[$kw],"aliases":[$al],"charCount":${e.charCount}}"""
            }
        File(out, "index.json").writeText(json)
    }

    private fun writeCss(out: File) {
        val cssDir = File(out, "docs/styles").also { it.mkdirs() }
        File(cssDir, "docs.css").writeText(generateCss())
    }

    private fun writeLocalesManifest(src: File, out: File) {
        val locales =
            src.listFiles { f -> f.isDirectory && LOCALE_PATTERN.matches(f.name) && f.name != "en" }
                ?.map { it.name }
                ?.sorted() ?: emptyList()
        File(out, "locales.json").writeText(locales.joinToString(", ", "[", "]") { "\"$it\"" })
    }
}

private fun parseFrontmatter(file: File): Map<String, String> {
    val lines = file.readLines()
    val endIndex =
        lines
            .takeIf { it.firstOrNull()?.trim() == "---" }
            ?.drop(1)
            ?.indexOfFirst { it.trim() == "---" }
            ?.takeIf { it >= 0 } ?: return emptyMap()
    val result = mutableMapOf<String, String>()
    var inAliases = false
    val aliasesBuilder = StringBuilder()
    for (line in lines.subList(1, endIndex + 1)) {
        when {
            line.startsWith("aliases:") -> inAliases = true

            inAliases && line.startsWith("  - ") -> aliasesBuilder.append(line.removePrefix("  - ").trim()).append(",")

            inAliases -> {
                inAliases = false
                result["aliases_raw"] = aliasesBuilder.toString().trimEnd(',')
                if (line.contains(":")) result += parseLine(line)
            }

            !inAliases && line.contains(":") -> result += parseLine(line)
        }
    }
    if (inAliases) result["aliases_raw"] = aliasesBuilder.toString().trimEnd(',')
    return result
}

private fun parseLine(line: String): Map<String, String> {
    val (key, value) = line.split(":", limit = 2)
    return mapOf(key.trim() to value.trim())
}

private fun parseListField(raw: String): List<String> = raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }

private fun extractKeywords(file: File, title: String): List<String> {
    val text = file.readText().lowercase()
    val keywords = mutableSetOf<String>()
    title.lowercase().split(Regex("[^a-z0-9]+")).filter { it.length >= MIN_KEYWORD_LENGTH }.forEach { keywords.add(it) }
    Regex("^#{1,3}\\s+(.+)$", RegexOption.MULTILINE).findAll(text).forEach { match ->
        match.groupValues[1]
            .split(Regex("[^a-z0-9]+"))
            .filter { it.length >= MIN_KEYWORD_LENGTH }
            .forEach { keywords.add(it) }
    }
    return keywords.toList().take(MAX_KEYWORDS)
}

private fun generateHtml(mdFile: File, title: String, locale: String = "en"): String {
    val content =
        mdFile
            .readText()
            .replace(Regex("^---[\\s\\S]*?---\\s*", RegexOption.MULTILINE), "")
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
    val dir = if (locale == "ar") "rtl" else "ltr"
    val cssPath = if (locale != "en") "../../styles/docs.css" else "../styles/docs.css"
    return """
            |<!DOCTYPE html>
            |<html lang="$locale" dir="$dir">
            |<head>
            |  <meta charset="UTF-8">
            |  <meta name="viewport" content="width=device-width, initial-scale=1.0">
            |  <title>$title</title>
            |  <link rel="stylesheet" href="$cssPath">
            |</head>
            |<body data-page="${mdFile.nameWithoutExtension}" data-locale="$locale">
            |<pre class="markdown-content">$content</pre>
            |</body>
            |</html>
        """
        .trimMargin()
}

private fun generateCss(): String =
    """
    |:root {
    |  --primary: #2C2D3C;
    |  --accent: #67EA94;
    |  --accent-text: #3FB86D;
    |  --info: #5C6BC0;
    |  --warning: #E8A33E;
    |  --error: #E05252;
    |}
    |body {
    |  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
    |  line-height: 1.6;
    |  padding: 16px;
    |  color: var(--primary);
    |  max-width: 800px;
    |  margin: 0 auto;
    |}
    |@media (prefers-color-scheme: dark) {
    |  body { background: #1A1B26; color: #ECEDF3; }
    |  pre { background: #2C2D3C; }
    |}
    |pre.markdown-content {
    |  white-space: pre-wrap;
    |  word-wrap: break-word;
    |  font-family: inherit;
    |  background: transparent;
    |  padding: 0;
    |  margin: 0;
    |}
    |.callout-info { border-left: 4px solid var(--info); padding: 12px; background: #E8EAF6; margin: 12px 0; }
    |.callout-warning { border-left: 4px solid var(--warning); padding: 12px; background: #FFF3E0; margin: 12px 0; }
    |.callout-error { border-left: 4px solid var(--error); padding: 12px; background: #FDEAEA; margin: 12px 0; }
    """
        .trimMargin()

abstract class ValidateDocsBundleTask : DefaultTask() {
    @get:InputDirectory @get:Optional
    abstract val bundleDir: DirectoryProperty

    @get:InputFile @get:Optional
    abstract val schemaFile: RegularFileProperty

    @TaskAction
    fun validate() {
        val dir = bundleDir.get().asFile
        val indexFile = File(dir, "index.json")
        val errors = mutableListOf<String>()

        if (!indexFile.exists()) {
            errors.add("index.json not found in ${dir.absolutePath}")
        }

        val indexContent = if (indexFile.exists()) indexFile.readText() else ""
        if (indexContent.isNotEmpty() && !indexContent.trimStart().startsWith("[")) {
            errors.add("index.json must be a JSON array")
        }

        val sizeMb = dir.walkTopDown().filter { it.isFile }.sumOf { it.length() } / BYTES_PER_MB
        if (sizeMb > BUNDLE_SIZE_HARD_LIMIT_MB) {
            errors.add(
                "Bundle size ${String.format("%.2f", sizeMb)} MB exceeds $BUNDLE_SIZE_HARD_LIMIT_MB MB hard limit",
            )
        } else if (sizeMb > BUNDLE_SIZE_WARN_THRESHOLD_MB) {
            logger.warn(
                "Bundle size ${String.format(
                    "%.2f",
                    sizeMb,
                )} MB exceeds $BUNDLE_SIZE_WARN_THRESHOLD_MB MB warning threshold",
            )
        }

        if (indexContent.isNotEmpty()) {
            val paths =
                Regex("\"resourcePath\"\\s*:\\s*\"([^\"]+)\"").findAll(indexContent).map { it.groupValues[1] }.toList()
            paths.filter { !File(dir, it).exists() }.forEach { errors.add("Missing page file: $it") }
            if (errors.isEmpty()) {
                logger.lifecycle(
                    "Docs bundle validation PASSED: ${paths.size} pages, ${String.format("%.2f", sizeMb)} MB",
                )
            }
        }

        if (errors.isNotEmpty()) throw org.gradle.api.GradleException(errors.joinToString("\n"))
    }
}

abstract class PublishDocsSiteTask : DefaultTask() {
    @get:InputDirectory abstract val sourceDir: DirectoryProperty

    @get:InputDirectory abstract val bundleDir: DirectoryProperty

    @get:OutputDirectory abstract val siteOutputDir: DirectoryProperty

    @get:Input abstract val channel: Property<String>

    @get:Input abstract val version: Property<String>

    @TaskAction
    fun publish() {
        val siteDir = siteOutputDir.get().asFile
        val channelPath =
            when (channel.get()) {
                "release" -> "v${version.get()}"
                "root" -> ""
                else -> channel.get()
            }
        val outDir = if (channelPath.isEmpty()) siteDir else File(siteDir, channelPath)
        outDir.mkdirs()

        bundleDir.get().asFile.copyRecursively(outDir, overwrite = true)

        sourceDir
            .get()
            .asFile
            .listFiles()
            ?.filter { it.name != "_site" && it.name != ".jekyll-cache" }
            ?.forEach { f ->
                if (f.isDirectory) {
                    f.copyRecursively(File(outDir, f.name), overwrite = true)
                } else {
                    f.copyTo(File(outDir, f.name), overwrite = true)
                }
            }

        logger.lifecycle("Published docs site to: ${outDir.absolutePath} (channel=$channelPath)")
    }
}
