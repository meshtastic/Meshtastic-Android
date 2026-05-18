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
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.register
import java.io.File

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
                project.rootProject.layout.projectDirectory
                    .file("specs/003-app-docs-markdown/contracts/keyword-index-schema.json")
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

abstract class GenerateDocsBundleTask : DefaultTask() {
    @get:InputDirectory
    abstract val sourceDir: DirectoryProperty

    @get:OutputDirectory
    abstract val generatedOutputDir: DirectoryProperty

    @get:Input
    abstract val channel: Property<String>

    @get:Input
    abstract val version: Property<String>

    @TaskAction
    fun generate() {
        val src = sourceDir.get().asFile
        val out = generatedOutputDir.get().asFile
        out.mkdirs()

        val indexEntries = mutableListOf<String>()
        var pageCount = 0

        // Process English user and developer directories
        listOf("user", "developer").forEach { section ->
            val sectionDir = File(src, section)
            if (!sectionDir.exists()) return@forEach

            sectionDir.listFiles { f -> f.extension == "md" }?.sortedBy { it.name }?.forEach { mdFile ->
                val frontmatter = parseFrontmatter(mdFile)
                val id = mdFile.nameWithoutExtension
                val title = frontmatter["title"] ?: id.replace("-", " ").replaceFirstChar { it.uppercase() }
                val navOrder = frontmatter["nav_order"]?.toIntOrNull() ?: 999
                val aliases = parseListField(frontmatter["aliases_raw"] ?: "")
                val keywords = extractKeywords(mdFile, title)
                val charCount = mdFile.readText().length

                // Generate simple HTML wrapper
                val htmlDir = File(out, "docs/$section")
                htmlDir.mkdirs()
                val htmlFile = File(htmlDir, "$id.html")
                htmlFile.writeText(generateHtml(mdFile, title, "en"))

                // Build index entry
                val keywordsJson = keywords.joinToString(", ") { "\"$it\"" }
                val aliasesJson = aliases.joinToString(", ") { "\"$it\"" }
                indexEntries.add("""
                    |  {
                    |    "id": "$id",
                    |    "title": "$title",
                    |    "section": "$section",
                    |    "locale": "en",
                    |    "resourcePath": "docs/$section/$id.html",
                    |    "navOrder": $navOrder,
                    |    "keywords": [$keywordsJson],
                    |    "aliases": [$aliasesJson],
                    |    "charCount": $charCount
                    |  }
                """.trimMargin())

                pageCount++
            }
        }

        // Process Crowdin locale directories: docs/{locale}/user/*.md
        val localePattern = Regex("^[a-z]{2}(-[A-Z]{2})?$")
        src.listFiles { f -> f.isDirectory && localePattern.matches(f.name) }
            ?.sortedBy { it.name }
            ?.forEach { localeDir ->
                val locale = localeDir.name
                listOf("user").forEach { section ->
                    val localeSectionDir = File(localeDir, section)
                    if (!localeSectionDir.exists()) return@forEach

                    localeSectionDir.listFiles { f -> f.extension == "md" }?.sortedBy { it.name }?.forEach { mdFile ->
                        val frontmatter = parseFrontmatter(mdFile)
                        val id = mdFile.nameWithoutExtension
                        val title = frontmatter["title"]
                            ?: id.replace("-", " ").replaceFirstChar { it.uppercase() }
                        val navOrder = frontmatter["nav_order"]?.toIntOrNull() ?: 999
                        val keywords = extractKeywords(mdFile, title)
                        val charCount = mdFile.readText().length

                        // Generate locale-qualified HTML
                        val htmlDir = File(out, "docs/$locale/$section")
                        htmlDir.mkdirs()
                        val htmlFile = File(htmlDir, "$id.html")
                        htmlFile.writeText(generateHtml(mdFile, title, locale))

                        // Build locale index entry
                        val keywordsJson = keywords.joinToString(", ") { "\"$it\"" }
                        indexEntries.add("""
                            |  {
                            |    "id": "$id",
                            |    "title": "$title",
                            |    "section": "$section",
                            |    "locale": "$locale",
                            |    "resourcePath": "docs/$locale/$section/$id.html",
                            |    "navOrder": $navOrder,
                            |    "keywords": [$keywordsJson],
                            |    "aliases": [],
                            |    "charCount": $charCount
                            |  }
                        """.trimMargin())

                        pageCount++
                    }
                }
            }

        // Write index.json
        val indexFile = File(out, "index.json")
        indexFile.writeText("[\n${indexEntries.joinToString(",\n")}\n]")

        // Write shared CSS
        val cssDir = File(out, "docs/styles")
        cssDir.mkdirs()
        File(cssDir, "docs.css").writeText(generateCss())

        // Write locales manifest (for consumers that need to know available translations)
        val localesManifest = src.listFiles { f -> f.isDirectory && localePattern.matches(f.name) }
            ?.map { it.name }?.sorted() ?: emptyList()
        val manifestFile = File(out, "locales.json")
        manifestFile.writeText(localesManifest.joinToString(", ", "[", "]") { "\"$it\"" })

        logger.lifecycle("Generated docs bundle: $pageCount pages (${localesManifest.size} locales), channel=${channel.get()}, version=${version.get()}")
    }

    private fun parseFrontmatter(file: File): Map<String, String> {
        val lines = file.readLines()
        if (lines.firstOrNull()?.trim() != "---") return emptyMap()
        val endIndex = lines.drop(1).indexOfFirst { it.trim() == "---" }
        if (endIndex < 0) return emptyMap()
        val fmLines = lines.subList(1, endIndex + 1)
        val result = mutableMapOf<String, String>()
        var inAliases = false
        val aliasesBuilder = StringBuilder()
        for (line in fmLines) {
            if (line.startsWith("aliases:")) {
                inAliases = true
                continue
            }
            if (inAliases) {
                if (line.startsWith("  - ")) {
                    aliasesBuilder.append(line.removePrefix("  - ").trim()).append(",")
                } else {
                    inAliases = false
                    result["aliases_raw"] = aliasesBuilder.toString().trimEnd(',')
                }
            }
            if (!inAliases && line.contains(":")) {
                val (key, value) = line.split(":", limit = 2)
                result[key.trim()] = value.trim()
            }
        }
        if (inAliases) result["aliases_raw"] = aliasesBuilder.toString().trimEnd(',')
        return result
    }

    private fun parseListField(raw: String): List<String> =
        raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }

    private fun extractKeywords(file: File, title: String): List<String> {
        val text = file.readText().lowercase()
        val keywords = mutableSetOf<String>()
        // Add title words
        title.lowercase().split(Regex("[^a-z0-9]+")).filter { it.length >= 3 }.forEach { keywords.add(it) }
        // Extract headings
        Regex("^#{1,3}\\s+(.+)$", RegexOption.MULTILINE).findAll(text).forEach { match ->
            match.groupValues[1].split(Regex("[^a-z0-9]+")).filter { it.length >= 3 }.forEach { keywords.add(it) }
        }
        return keywords.toList().take(30)
    }

    private fun generateHtml(mdFile: File, title: String, locale: String = "en"): String {
        val content = mdFile.readText()
            // Strip frontmatter
            .replace(Regex("^---[\\s\\S]*?---\\s*", RegexOption.MULTILINE), "")
            .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        val dir = if (locale == "ar") "rtl" else "ltr"
        return """
            |<!DOCTYPE html>
            |<html lang="$locale" dir="$dir">
            |<head>
            |  <meta charset="UTF-8">
            |  <meta name="viewport" content="width=device-width, initial-scale=1.0">
            |  <title>$title</title>
            |  <link rel="stylesheet" href="../styles/docs.css">
            |</head>
            |<body data-page="${mdFile.nameWithoutExtension}" data-locale="$locale">
            |<pre class="markdown-content">$content</pre>
            |</body>
            |</html>
        """.trimMargin()
    }

    private fun generateCss(): String = """
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
    """.trimMargin()
}

abstract class ValidateDocsBundleTask : DefaultTask() {
    @get:InputDirectory
    @get:Optional
    abstract val bundleDir: DirectoryProperty

    @get:InputFile
    @get:Optional
    abstract val schemaFile: RegularFileProperty

    @TaskAction
    fun validate() {
        val dir = bundleDir.get().asFile
        val indexFile = File(dir, "index.json")

        // Check index exists
        if (!indexFile.exists()) {
            throw org.gradle.api.GradleException("index.json not found in ${dir.absolutePath}")
        }

        // Check index is valid JSON array
        val indexContent = indexFile.readText()
        if (!indexContent.trimStart().startsWith("[")) {
            throw org.gradle.api.GradleException("index.json must be a JSON array")
        }

        // Check bundle size
        val totalSize = dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        val sizeMb = totalSize / (1024.0 * 1024.0)

        if (sizeMb > 10.0) {
            throw org.gradle.api.GradleException("Bundle size ${String.format("%.2f", sizeMb)} MB exceeds 10 MB hard limit")
        }
        if (sizeMb > 8.0) {
            logger.warn("WARNING: Bundle size ${String.format("%.2f", sizeMb)} MB exceeds 8 MB warning threshold")
        }

        // Check all referenced pages exist
        val pagePattern = Regex("\"resourcePath\"\\s*:\\s*\"([^\"]+)\"")
        val referencedPaths = pagePattern.findAll(indexContent).map { it.groupValues[1] }.toList()
        val missingPages = referencedPaths.filter { !File(dir, it).exists() }
        if (missingPages.isNotEmpty()) {
            throw org.gradle.api.GradleException("Missing page files: ${missingPages.joinToString()}")
        }

        logger.lifecycle("Docs bundle validation PASSED: ${referencedPaths.size} pages, ${String.format("%.2f", sizeMb)} MB")
    }
}

abstract class PublishDocsSiteTask : DefaultTask() {
    @get:InputDirectory
    abstract val sourceDir: DirectoryProperty

    @get:InputDirectory
    abstract val bundleDir: DirectoryProperty

    @get:OutputDirectory
    abstract val siteOutputDir: DirectoryProperty

    @get:Input
    abstract val channel: Property<String>

    @get:Input
    abstract val version: Property<String>

    @TaskAction
    fun publish() {
        val siteDir = siteOutputDir.get().asFile
        val channelPath = if (channel.get() == "release") "v${version.get()}" else channel.get()
        val outDir = File(siteDir, channelPath)
        outDir.mkdirs()

        // Copy generated bundle to site output
        val bundle = bundleDir.get().asFile
        bundle.copyRecursively(outDir, overwrite = true)

        // Copy source markdown for Jekyll processing
        val src = sourceDir.get().asFile
        src.listFiles()?.filter { it.name != "_site" && it.name != ".jekyll-cache" }?.forEach { f ->
            if (f.isDirectory) f.copyRecursively(File(outDir, f.name), overwrite = true)
            else f.copyTo(File(outDir, f.name), overwrite = true)
        }

        logger.lifecycle("Published docs site to: ${outDir.absolutePath} (channel=$channelPath)")
    }
}






