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
package org.meshtastic.flatpak

import groovy.json.JsonOutput
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.w3c.dom.Element
import org.w3c.dom.NodeList
import java.io.File
import java.security.MessageDigest
import javax.xml.parsers.DocumentBuilderFactory

/** Generates a complete flatpak-sources.json manifest from the local Gradle cache directory. */
abstract class GenerateFlatpakSourcesTask : DefaultTask() {

    @get:Internal abstract val cacheDir: DirectoryProperty

    @get:OutputFile abstract val outputFile: RegularFileProperty

    init {
        group = "flatpak"
        description = "Generates a complete flatpak-sources.json manifest from the local Gradle cache directory."
        // Ensure the task always runs when executed
        outputs.upToDateWhen { false }
    }

    private data class SnapshotVersion(val extension: String, val classifier: String?, val value: String)

    private data class SnapshotMetadata(val snapshotVersions: List<SnapshotVersion>, val fallbackValue: String?)

    private data class FlatpakSourceCandidate(
        val file: File,
        val group: String,
        val name: String,
        val version: String,
        val ext: String,
        val dest: String,
        val destFilename: String,
        val primaryUrl: String,
        val mirrorUrls: List<String>,
    )

    private var metadataCache: Map<String, SnapshotMetadata>? = null

    @TaskAction
    fun generate() {
        val cacheFolder =
            cacheDir.orNull?.asFile
                ?: throw GradleException(
                    "Gradle cache directory does not exist or is not configured correctly. Please run a build first to populate the cache.",
                )

        val outputSourcesFile = outputFile.get().asFile
        logger.lifecycle("Scanning Gradle cache directory: ${cacheFolder.absolutePath}")

        val allowedExtensions = setOf("jar", "aar", "pom", "module")

        // Scan the cache using a clean functional sequence pipeline
        val candidates =
            cacheFolder
                .walkTopDown()
                .filter { it.isFile }
                .filter { it.extension.lowercase() in allowedExtensions }
                .filterNot { it.name.endsWith("-sources.jar") || it.name.endsWith("-javadoc.jar") }
                .mapNotNull { file ->
                    val ext = file.extension.lowercase()
                    val filename = file.name
                    val relativePath = file.relativeTo(cacheFolder).path.replace('\\', '/')
                    val parts = relativePath.split('/')

                    if (parts.size != 5) return@mapNotNull null

                    val (group, name, version) = parts
                    val groupPath = group.replace('.', '/')
                    val standardPrefix = "$name-$version"
                    val isSnapshot = version.endsWith("-SNAPSHOT") || version.contains("-SNAPSHOT")

                    val classifier =
                        if (isSnapshot) {
                            val prefix = "$name-$version-"
                            filename.takeIf { it.startsWith(prefix) }?.removePrefix(prefix)?.removeSuffix(".$ext")
                        } else {
                            null
                        }

                    val resolvedVersion =
                        if (isSnapshot) {
                            val resourcesFolder = File(cacheFolder.parentFile, "resources-2.1")
                            findSnapshotValue(resourcesFolder, group, name, ext, classifier) ?: version
                        } else {
                            version
                        }

                    val serverFilename =
                        when {
                            isSnapshot -> {
                                val suffix = classifier?.let { "-$it" } ?: ""
                                "$name-$resolvedVersion$suffix.$ext"
                            }

                            filename.startsWith(standardPrefix) -> filename

                            else -> "$name-$version.$ext"
                        }

                    val mavenPath = "$groupPath/$name/$version/$serverFilename"
                    val dest = "offline-repository/$groupPath/$name/$version"

                    val isJitpack = group.startsWith("com.github.")
                    val primaryUrl =
                        if (isSnapshot) {
                            "https://central.sonatype.com/repository/maven-snapshots/$mavenPath"
                        } else if (isJitpack) {
                            "https://jitpack.io/$mavenPath"
                        } else {
                            "https://repo.maven.apache.org/maven2/$mavenPath"
                        }

                    val mirrorUrls =
                        when {
                            isSnapshot -> listOf("https://oss.sonatype.org/content/repositories/snapshots/$mavenPath")

                            isJitpack ->
                                listOf(
                                    "https://repo.maven.apache.org/maven2/$mavenPath",
                                    "https://maven-central.storage-download.googleapis.com/maven2/$mavenPath",
                                    "https://maven.aliyun.com/repository/public/$mavenPath",
                                )

                            else ->
                                listOf(
                                    "https://dl.google.com/dl/android/maven2/$mavenPath",
                                    "https://plugins.gradle.org/m2/$mavenPath",
                                    "https://maven-central.storage-download.googleapis.com/maven2/$mavenPath",
                                    "https://maven.aliyun.com/repository/public/$mavenPath",
                                )
                        }

                    FlatpakSourceCandidate(
                        file = file,
                        group = group,
                        name = name,
                        version = version,
                        ext = ext,
                        dest = dest,
                        destFilename = filename,
                        primaryUrl = primaryUrl,
                        mirrorUrls = mirrorUrls,
                    )
                }
                .toList()

        // Deduplicate and sort by unique destination path + file
        val deduplicated =
            candidates
                .groupBy { "${it.dest}/${it.destFilename}" }
                .map { (_, groupCandidates) -> groupCandidates.first() }
                .sortedBy { "${it.dest}/${it.destFilename}" }

        logger.lifecycle("Calculating checksums for ${deduplicated.size} unique sources...")

        val finalEntries =
            deduplicated.map { candidate ->
                mapOf(
                    "type" to "file",
                    "url" to candidate.primaryUrl,
                    "sha256" to calculateSha256(candidate.file),
                    "dest" to candidate.dest,
                    "dest-filename" to candidate.destFilename,
                    "mirror-urls" to candidate.mirrorUrls,
                )
            }

        outputSourcesFile.writeText(JsonOutput.prettyPrint(JsonOutput.toJson(finalEntries)))
        logger.lifecycle(
            "Successfully scanned cache and generated ${outputSourcesFile.name} containing ${finalEntries.size} entries.",
        )
    }

    private fun calculateSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { inputStream ->
            val buffer = ByteArray(8192)
            var bytesRead = inputStream.read(buffer)
            while (bytesRead != -1) {
                digest.update(buffer, 0, bytesRead)
                bytesRead = inputStream.read(buffer)
            }
        }
        val bytes = digest.digest()
        val hexDigits = "0123456789abcdef"
        val hexChars = CharArray(bytes.size * 2)
        for (i in bytes.indices) {
            val v = bytes[i].toInt() and 0xFF
            hexChars[i * 2] = hexDigits[v ushr 4]
            hexChars[i * 2 + 1] = hexDigits[v and 0x0F]
        }
        return String(hexChars)
    }

    // Modern helper extension to query DOM child element text safely
    private fun Element.getChildText(tagName: String): String? = getElementsByTagName(tagName).item(0)?.textContent

    // Modern helper extension to iterate DOM elements cleanly
    private fun NodeList.forEachElement(action: (Element) -> Unit) {
        for (i in 0 until length) {
            val node = item(i)
            if (node is Element) {
                action(node)
            }
        }
    }

    private fun populateMetadataCache(resourcesFolder: File) {
        if (metadataCache != null || !resourcesFolder.exists()) return
        val cache = mutableMapOf<String, SnapshotMetadata>()

        resourcesFolder.walkTopDown().forEach { file ->
            if (file.isFile && file.name == "maven-metadata.xml") {
                try {
                    val dbFactory = DocumentBuilderFactory.newInstance()
                    val dBuilder = dbFactory.newDocumentBuilder()
                    val doc = dBuilder.parse(file)
                    doc.documentElement.normalize()

                    val root = doc.documentElement
                    val group = root.getChildText("groupId") ?: return@forEach
                    val name = root.getChildText("artifactId") ?: return@forEach

                    val key = "$group:$name"
                    val snapshotVersions = mutableListOf<SnapshotVersion>()
                    root.getElementsByTagName("snapshotVersion").forEachElement { element ->
                        val ext = element.getChildText("extension") ?: return@forEachElement
                        val value = element.getChildText("value") ?: return@forEachElement
                        val classif = element.getChildText("classifier")

                        snapshotVersions.add(SnapshotVersion(ext, classif, value))
                    }

                    var fallbackValue: String? = null
                    val snapshotNode = root.getElementsByTagName("snapshot").item(0) as? Element
                    if (snapshotNode != null) {
                        val timestamp = snapshotNode.getChildText("timestamp")
                        val buildNumber = snapshotNode.getChildText("buildNumber")
                        val version = root.getChildText("version")
                        if (timestamp != null && buildNumber != null && version != null) {
                            val baseVersion = version.substringBefore("-SNAPSHOT")
                            fallbackValue = "$baseVersion-$timestamp-$buildNumber"
                        }
                    }

                    cache[key] = SnapshotMetadata(snapshotVersions, fallbackValue)
                } catch (e: Exception) {
                    // Ignore parsing errors for individual files
                }
            }
        }
        metadataCache = cache
    }

    private fun findSnapshotValue(
        resourcesFolder: File,
        group: String,
        name: String,
        extension: String,
        classifier: String?,
    ): String? {
        populateMetadataCache(resourcesFolder)
        val metadata = metadataCache?.get("$group:$name") ?: return null

        for (version in metadata.snapshotVersions) {
            if (version.extension == extension) {
                if (classifier == null && version.classifier == null) {
                    return version.value
                }
                if (classifier != null && classifier == version.classifier) {
                    return version.value
                }
            }
        }

        return metadata.fallbackValue
    }
}
