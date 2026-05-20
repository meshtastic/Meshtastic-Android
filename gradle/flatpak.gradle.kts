import groovy.json.JsonOutput
import java.io.File
import java.security.MessageDigest
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

// Abstract Task Definition for Configuration Cache compatibility and clean code separation
abstract class GenerateFlatpakSourcesTask : DefaultTask() {

    @get:Internal
    abstract val cacheDir: DirectoryProperty

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    init {
        group = "flatpak"
        description = "Generates a complete flatpak-sources.json manifest from the local Gradle cache directory."
        // Ensure the task always runs when executed
        outputs.upToDateWhen { false }
    }

    @TaskAction
    fun generate() {
        val cacheFolder = cacheDir.orNull?.asFile
        if (cacheFolder == null || !cacheFolder.exists()) {
            throw GradleException(
                "Gradle cache directory does not exist or is not configured correctly: ${cacheFolder?.absolutePath}. " +
                "Please run a build first to populate the cache."
            )
        }

        val outputSourcesFile = outputFile.get().asFile
        logger.lifecycle("Scanning Gradle cache directory: ${cacheFolder.absolutePath}")

        val allowedExtensions = setOf("jar", "aar", "pom", "module")
        val entries = mutableListOf<Map<String, Any>>()

        cacheFolder.walkTopDown().forEach { file ->
            if (file.isFile) {
                val ext = file.extension.lowercase()
                if (ext in allowedExtensions) {
                    val filename = file.name
                    if (!filename.endsWith("-sources.jar") && !filename.endsWith("-javadoc.jar")) {
                        val relativePath = file.relativeTo(cacheFolder).path.replace('\\', '/')
                        val parts = relativePath.split('/')
                        if (parts.size == 5) {
                            val group = parts[0]
                            val name = parts[1]
                            val version = parts[2]

                            val groupPath = group.replace('.', '/')

                            // Reconstruct correct Maven filename if Gradle cache renamed it locally (e.g. animation.aar -> animation-android-1.10.0.aar)
                            val standardPrefix = "$name-$version"
                            val isSnapshot = version.endsWith("-SNAPSHOT") || version.contains("-SNAPSHOT")
                            val classifier = if (isSnapshot) {
                                val prefix = "$name-$version-"
                                if (filename.startsWith(prefix)) {
                                    filename.substring(prefix.length, filename.length - ext.length - 1)
                                } else {
                                    null
                                }
                            } else {
                                null
                            }

                            val resolvedVersion = if (isSnapshot) {
                                val resourcesFolder = File(cacheFolder.parentFile, "resources-2.1")
                                findSnapshotValue(resourcesFolder, group, name, ext, classifier) ?: version
                            } else {
                                version
                            }

                            val serverFilename = if (isSnapshot) {
                                if (classifier != null) {
                                    "$name-$resolvedVersion-$classifier.$ext"
                                } else {
                                    "$name-$resolvedVersion.$ext"
                                }
                            } else if (filename.startsWith(standardPrefix)) {
                                filename
                            } else {
                                "$name-$version.$ext"
                            }

                            val mavenPath = "$groupPath/$name/$version/$serverFilename"
                            val dest = "offline-repository/$groupPath/$name/$version"

                            val sha256 = calculateSha256(file)

                            val isJitpack = group.startsWith("com.github.")
                            val primaryUrl = if (isSnapshot) {
                                "https://central.sonatype.com/repository/maven-snapshots/$mavenPath"
                            } else if (isJitpack) {
                                "https://jitpack.io/$mavenPath"
                            } else {
                                "https://repo.maven.apache.org/maven2/$mavenPath"
                            }

                            val mirrorUrls = if (isSnapshot) {
                                listOf(
                                    "https://oss.sonatype.org/content/repositories/snapshots/$mavenPath"
                                )
                            } else if (isJitpack) {
                                listOf(
                                    "https://repo.maven.apache.org/maven2/$mavenPath",
                                    "https://maven-central.storage-download.googleapis.com/maven2/$mavenPath",
                                    "https://maven.aliyun.com/repository/public/$mavenPath"
                                )
                            } else {
                                listOf(
                                    "https://dl.google.com/dl/android/maven2/$mavenPath",
                                    "https://plugins.gradle.org/m2/$mavenPath",
                                    "https://maven-central.storage-download.googleapis.com/maven2/$mavenPath",
                                    "https://maven.aliyun.com/repository/public/$mavenPath"
                                )
                            }

                            entries.add(
                                mapOf(
                                    "type" to "file",
                                    "url" to primaryUrl,
                                    "sha256" to sha256,
                                    "dest" to dest,
                                    "dest-filename" to filename, // Save using the cached name expected by offline Gradle
                                    "mirror-urls" to mirrorUrls
                                )
                            )
                        }
                    }
                }
            }
        }

        // Deduplicate and sort
        val deduplicated = entries.groupBy { "${it["dest"]}/${it["dest-filename"]}" }
            .map { (_, group) -> group.first() }
            .sortedBy { "${it["dest"]}/${it["dest-filename"]}" }

        outputSourcesFile.writeText(JsonOutput.prettyPrint(JsonOutput.toJson(deduplicated)))
        logger.lifecycle("Successfully scanned cache and generated ${outputSourcesFile.name} containing ${deduplicated.size} entries.")
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

    private fun findSnapshotValue(resourcesFolder: File, group: String, name: String, extension: String, classifier: String?): String? {
        if (!resourcesFolder.exists()) return null
        resourcesFolder.walkTopDown().forEach { file ->
            if (file.isFile && file.name == "maven-metadata.xml") {
                try {
                    val dbFactory = DocumentBuilderFactory.newInstance()
                    val dBuilder = dbFactory.newDocumentBuilder()
                    val doc = dBuilder.parse(file)
                    doc.documentElement.normalize()

                    val root = doc.documentElement
                    val fileGroup = root.getElementsByTagName("groupId").item(0)?.textContent
                    val fileArtifact = root.getElementsByTagName("artifactId").item(0)?.textContent

                    if (fileGroup == group && fileArtifact == name) {
                        val snapshotVersions = root.getElementsByTagName("snapshotVersion")
                        for (i in 0 until snapshotVersions.length) {
                            val element = snapshotVersions.item(i) as Element
                            val ext = element.getElementsByTagName("extension").item(0)?.textContent
                            val value = element.getElementsByTagName("value").item(0)?.textContent
                            val classif = element.getElementsByTagName("classifier").item(0)?.textContent

                            if (ext == extension) {
                                if (classifier == null && classif == null) {
                                    return value
                                }
                                if (classifier != null && classifier == classif) {
                                    return value
                                }
                            }
                        }

                        val snapshotNode = root.getElementsByTagName("snapshot").item(0) as? Element
                        if (snapshotNode != null) {
                            val timestamp = snapshotNode.getElementsByTagName("timestamp").item(0)?.textContent
                            val buildNumber = snapshotNode.getElementsByTagName("buildNumber").item(0)?.textContent
                            val version = root.getElementsByTagName("version").item(0)?.textContent
                            if (timestamp != null && buildNumber != null && version != null) {
                                val baseVersion = version.substringBefore("-SNAPSHOT")
                                return "$baseVersion-$timestamp-$buildNumber"
                            }
                        }
                    }
                } catch (e: Exception) {
                    // Ignore parsing errors for individual files
                }
            }
        }
        return null
    }
}

tasks.register<GenerateFlatpakSourcesTask>("generateFlatpakSourcesFromCache") {
    val customCachePath = providers.gradleProperty("flatpak.cache.dir").orNull
    if (customCachePath != null) {
        cacheDir.set(layout.projectDirectory.dir(customCachePath))
    } else {
        cacheDir.set(layout.dir(providers.provider { File(gradle.gradleUserHomeDir, "caches/modules-2/files-2.1") }))
    }
    outputFile.set(layout.projectDirectory.file("flatpak-sources.json"))
}
