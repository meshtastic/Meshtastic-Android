import java.io.FileInputStream
import java.util.Properties

project.pluginManager.apply("maven-publish")

val configProperties = Properties()
val configFile = rootProject.file("config.properties")
if (configFile.exists()) {
    FileInputStream(configFile).use { configProperties.load(it) }
}

val versionBase = configProperties.getProperty("VERSION_NAME_BASE") ?: "0.0.0-SNAPSHOT"
val appVersion = System.getenv("VERSION_NAME") ?: versionBase

project.version = appVersion
project.group = "org.meshtastic"

val GITHUB_ACTOR = System.getenv("GITHUB_ACTOR")
val GITHUB_TOKEN = System.getenv("GITHUB_TOKEN")

if (!GITHUB_ACTOR.isNullOrEmpty() && !GITHUB_TOKEN.isNullOrEmpty()) {
    configure<PublishingExtension> {
        repositories {
            maven {
                name = "GitHubPackages"
                url = uri("https://maven.pkg.github.com/meshtastic/Meshtastic-Android")
                credentials {
                    username = GITHUB_ACTOR
                    password = GITHUB_TOKEN
                }
            }
        }
    }
} else {
    println("Skipping GitHub Packages repository configuration: GITHUB_ACTOR or GITHUB_TOKEN not set.")
}
